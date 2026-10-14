package ym.dreamSkyblock.config

import org.bukkit.configuration.file.YamlConfiguration
import ym.dreamSkyblock.world.WorldArchiveIO
import java.nio.file.Files
import java.nio.file.Path

/** 旧配置只追加版本头，保留未知字段与原始中文注释；解析失败不写回。 */
object VersionedYaml {
    fun load(path: Path): YamlConfiguration {
        val text = Files.readString(path)
        val yaml = YamlConfiguration().also { configuration ->
            try { configuration.loadFromString(text) } catch (error: Throwable) {
                throw IllegalStateException("配置解析失败 file=$path, path=<root>, version=unknown: ${error.message}", error)
            }
        }
        val version = yaml.get("schema-version")
        require(version == null || version == 1) { "不支持的配置版本 file=$path, path=schema-version, value=$version" }
        if (version == null) {
            try { WorldArchiveIO().writeDurable(path, "schema-version: 1\n$text") } catch (error: Throwable) {
                throw IllegalStateException("配置迁移失败 file=$path, path=schema-version, version=0->1: ${error.message}", error)
            }
            yaml.set("schema-version", 1)
        }
        return yaml
    }
}
