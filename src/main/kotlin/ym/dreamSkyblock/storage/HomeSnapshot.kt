package ym.dreamSkyblock.storage

import org.bukkit.configuration.file.YamlConfiguration
import ym.dreamSkyblock.model.Home
import ym.dreamSkyblock.model.HomeFlag
import ym.dreamSkyblock.model.HomeTemplate
import java.util.Collections
import java.util.Locale
import java.util.UUID

/** Immutable metadata captured before asynchronous persistence or world backup. */
class HomeSnapshot(
    val id: UUID,
    val ownerName: String,
    val templateId: String,
    val center: HomeTemplate.Point,
    val borderSize: Double,
    val level: Int,
    val experience: Long,
    val borderLevel: Int,
    val memberLimit: Int,
    owners: Set<UUID>,
    ownerNames: Map<UUID, String>,
    flags: Map<HomeFlag, Boolean>,
    val spawn: HomeTemplate.Point? = null,
    val status: String = "ACTIVE"
) {
    val owners: Set<UUID> = Collections.unmodifiableSet(LinkedHashSet(owners))
    val ownerNames: Map<UUID, String> = Collections.unmodifiableMap(LinkedHashMap(ownerNames))
    val flags: Map<HomeFlag, Boolean> = Collections.unmodifiableMap(LinkedHashMap(flags))
    val worldKey: String get() = "dreamskyblock:dreamskyblock_${id.toString().replace("-", "")}"

    init {
        require(ownerName.isNotBlank()) { "home $id: owner-name 不能为空" }
        require(templateId.isNotBlank()) { "home $id: template 不能为空" }
        require(owners.isNotEmpty()) { "home $id: owners 不能为空；拒绝创建无归属空岛" }
        require(borderSize.isFinite() && borderSize > 0.0) { "home $id: border-size 无效: $borderSize" }
        require(level > 0 && borderLevel > 0 && experience >= 0 && memberLimit > 0) {
            "home $id: level/experience/border-level/member-limit 无效"
        }
        require(status in STATUSES) { "home $id: status 无效: $status" }
        require(ownerNames.values.none { it.isBlank() }) { "home $id: owner-names 包含空名称" }
        validatePoint(center, "center", id)
        spawn?.let { validatePoint(it, "spawn", id) }
    }

    fun toHome(): Home = Home(id, ownerName, templateId, center.copy(), borderSize, level,
        experience, borderLevel, memberLimit, spawn?.copy()).also { home ->
        home.owners.addAll(owners)
        home.ownerNames.putAll(ownerNames)
        home.flags.putAll(flags)
        home.status = status
    }

    fun withStatus(newStatus: String): HomeSnapshot = HomeSnapshot(id, ownerName, templateId, center.copy(),
        borderSize, level, experience, borderLevel, memberLimit, owners, ownerNames, flags, spawn?.copy(), newStatus)

    fun toYaml(): String = YamlConfiguration().apply {
        set("schema-version", 1)
        set("id", id.toString())
        set("world-key", worldKey)
        set("owner-name", ownerName)
        set("template", templateId)
        setPoint(this, "center", center)
        spawn?.let { setPoint(this, "spawn", it) }
        set("border-size", borderSize)
        set("level", level)
        set("experience", experience)
        set("border-level", borderLevel)
        set("member-limit", memberLimit)
        set("status", status)
        set("owners", owners.sortedBy(UUID::toString).map(UUID::toString))
        ownerNames.toSortedMap(compareBy(UUID::toString)).forEach { (ownerId, name) -> set("owner-names.$ownerId", name) }
        flags.toSortedMap(compareBy(HomeFlag::name)).forEach { (flag, value) -> set("flags.${flag.name.lowercase(Locale.ROOT)}", value) }
    }.saveToString()

    companion object {
        val STATUSES: Set<String> = setOf("CREATING", "ACTIVE", "DELETED", "RECOVERY")

        fun fromHome(home: Home): HomeSnapshot = synchronized(home) {
            HomeSnapshot(home.id, home.ownerName, home.templateId, home.center.copy(), home.borderSize,
                home.level, home.experience, home.borderLevel, home.memberLimit, home.owners.toSet(),
                home.ownerNames.toMap(), home.flags.toMap(), home.spawn?.copy(), home.status)
        }

        /** Legacy home.yml is accepted; malformed fields never silently disappear. */
        fun fromYaml(text: String): HomeSnapshot {
            val yaml = YamlConfiguration().also { it.loadFromString(text) }
            if (yaml.contains("schema-version")) require(integer(yaml, "schema-version", null) == 1) {
                "home.yml: 不支持 schema-version=${yaml.get("schema-version")}"
            }
            val id = uuid(requiredString(yaml, "id"), "id")
            val rawOwners = yaml.get("owners") as? List<*> ?: error("home.yml: owners 必须是 UUID 列表")
            val owners = rawOwners.mapIndexed { index, owner ->
                uuid(owner as? String ?: error("home.yml: owners.$index 必须是 UUID 字符串"), "owners.$index")
            }
            require(owners.size == owners.toSet().size) { "home.yml: owners 包含重复 UUID" }
            val names = yaml.getConfigurationSection("owner-names")?.getKeys(false)?.associate { key ->
                uuid(key, "owner-names.$key") to requiredString(yaml, "owner-names.$key")
            } ?: emptyMap()
            if (yaml.contains("owner-names")) require(yaml.isConfigurationSection("owner-names")) {
                "home.yml: owner-names 必须是映射"
            }
            val flags = yaml.getConfigurationSection("flags")?.getKeys(false)?.associate { key ->
                val flag = runCatching { HomeFlag.valueOf(key.uppercase(Locale.ROOT)) }.getOrElse {
                    throw IllegalArgumentException("home.yml: flags.$key 未知权限", it)
                }
                val value = yaml.get("flags.$key") as? Boolean ?: error("home.yml: flags.$key 必须是布尔值")
                flag to value
            } ?: emptyMap()
            if (yaml.contains("flags")) require(yaml.isConfigurationSection("flags")) { "home.yml: flags 必须是映射" }
            val snapshot = HomeSnapshot(id, requiredString(yaml, "owner-name"), requiredString(yaml, "template"),
                readPoint(yaml, "center"), number(yaml, "border-size", null), integer(yaml, "level", 1),
                longNumber(yaml, "experience", 0), integer(yaml, "border-level", 1), integer(yaml, "member-limit", 4),
                owners.toSet(), names, flags, if (yaml.contains("spawn")) readPoint(yaml, "spawn") else null,
                if (yaml.contains("status")) requiredString(yaml, "status") else "ACTIVE")
            if (yaml.contains("world-key")) require(requiredString(yaml, "world-key") == snapshot.worldKey) {
                "home $id: world-key 与 home_id 不匹配"
            }
            return snapshot
        }

        private fun requiredString(yaml: YamlConfiguration, path: String): String =
            (yaml.get(path) as? String)?.takeIf(String::isNotBlank) ?: error("home.yml: $path 缺失或不是非空字符串")

        private fun uuid(value: String, path: String): UUID {
            val parsed = runCatching { UUID.fromString(value) }.getOrElse {
                throw IllegalArgumentException("home.yml: $path 的 UUID 无效: $value", it)
            }
            require(parsed.toString().equals(value, true)) { "home.yml: $path 的 UUID 格式不规范: $value" }
            return parsed
        }

        private fun number(yaml: YamlConfiguration, path: String, default: Double?): Double {
            if (!yaml.contains(path) && default != null) return default
            return (yaml.get(path) as? Number)?.toDouble()?.takeIf(Double::isFinite)
                ?: error("home.yml: $path 缺失或不是有限数值")
        }

        private fun integer(yaml: YamlConfiguration, path: String, default: Int?): Int {
            if (!yaml.contains(path) && default != null) return default
            val value = number(yaml, path, null)
            require(value % 1.0 == 0.0 && value in Int.MIN_VALUE.toDouble()..Int.MAX_VALUE.toDouble()) {
                "home.yml: $path 必须是有效整数"
            }
            return value.toInt()
        }

        private fun longNumber(yaml: YamlConfiguration, path: String, default: Long): Long {
            if (!yaml.contains(path)) return default
            return when (val value = yaml.get(path)) {
                is Byte -> value.toLong()
                is Short -> value.toLong()
                is Int -> value.toLong()
                is Long -> value
                else -> error("home.yml: $path 必须是有效长整数")
            }
        }

        private fun readPoint(yaml: YamlConfiguration, path: String): HomeTemplate.Point = HomeTemplate.Point(
            number(yaml, "$path.x", null), number(yaml, "$path.y", null), number(yaml, "$path.z", null),
            number(yaml, "$path.yaw", 0.0).toFloat(), number(yaml, "$path.pitch", 0.0).toFloat())

        private fun setPoint(yaml: YamlConfiguration, path: String, point: HomeTemplate.Point) {
            yaml.set("$path.x", point.x)
            yaml.set("$path.y", point.y)
            yaml.set("$path.z", point.z)
            yaml.set("$path.yaw", point.yaw.toDouble())
            yaml.set("$path.pitch", point.pitch.toDouble())
        }

        private fun validatePoint(point: HomeTemplate.Point, field: String, id: UUID) {
            require(point.x.isFinite() && point.y.isFinite() && point.z.isFinite() && point.yaw.isFinite() && point.pitch.isFinite()) {
                "home $id: $field 包含非有限坐标"
            }
        }
    }
}
