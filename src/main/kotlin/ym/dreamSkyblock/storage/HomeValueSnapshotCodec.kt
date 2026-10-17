package ym.dreamSkyblock.storage

import org.bukkit.configuration.file.YamlConfiguration
import ym.dreamSkyblock.api.HomeValueKey
import ym.dreamSkyblock.api.HomeValueRecord

internal object HomeValueSnapshotCodec {
    fun write(yaml: YamlConfiguration, records: Map<HomeValueKey, HomeValueRecord>) {
        if (records.isEmpty()) return
        yaml.set("value-records", records.entries.sortedWith(compareBy({ it.key.source }, { it.key.id })).map { (key, record) ->
            linkedMapOf("source" to key.source, "id" to key.id, "value" to record.value, "revision" to record.revision)
        })
    }

    fun read(yaml: YamlConfiguration): Map<HomeValueKey, HomeValueRecord>? {
        if (!yaml.contains("value-records")) return if (yaml.getInt("schema-version", 1) >= 2) emptyMap() else null
        val list = yaml.get("value-records") as? List<*> ?: error("home.yml: value-records 必须是列表")
        return buildMap {
            list.forEachIndexed { index, entry ->
                val row = entry as? Map<*, *> ?: error("home.yml: value-records.$index 必须是映射")
                val key = HomeValueKey(row["source"] as? String ?: error("value-records.$index.source 缺失"),
                    row["id"] as? String ?: error("value-records.$index.id 缺失"))
                fun integer(field: String): Long {
                    val value = row[field]
                    require(value is Int || value is Long) { "home.yml: value-records.$index.$field 必须是整数" }
                    return (value as Number).toLong()
                }
                require(put(key, HomeValueRecord(integer("value"), integer("revision"))) == null) {
                    "home.yml: value-records.$index 设施标识重复"
                }
            }
        }
    }
}
