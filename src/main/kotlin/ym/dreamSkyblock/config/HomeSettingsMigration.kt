package ym.dreamSkyblock.config

import org.bukkit.configuration.file.YamlConfiguration
import ym.dreamSkyblock.gui.HomeMenu
import ym.dreamSkyblock.model.HomeFlag
import ym.dreamSkyblock.world.WorldArchiveIO
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

/** Configuration IO thread. Retain the complete original before installing the split screens. */
internal object HomeSettingsMigration {
    fun migrate(folder: Path, defaults: (HomeMenu) -> YamlConfiguration) {
        val path = folder.resolve("gui/settings.yml")
        if (!Files.exists(path)) return
        val text = Files.readString(path)
        val legacy = VersionedYaml.load(path)
        val components = legacy.getConfigurationSection("components") ?: return
        if (components.getKeys(false).none { legacy.getString("components.$it.action", "")!!.startsWith("toggle:") }) return
        val hub = project(legacy, defaults(HomeMenu.SETTINGS), null)
        val pending = linkedMapOf<Path, YamlConfiguration>()
        listOf(HomeMenu.WORLD_RULES to HomeFlag.Category.WORLD, HomeMenu.VISITOR_PERMISSIONS to HomeFlag.Category.STRANGER).forEach { (menu, category) ->
            val target = folder.resolve("gui/${menu.file}.yml")
            if (!Files.exists(target)) pending[target] = project(legacy, defaults(menu), category)
        }
        pending[path] = hub // Install the hub last, so interrupted migration can be retried.
        pending.forEach { (file, yaml) -> HomeGuiConfig.validate(file.toString(), yaml) }
        val io = WorldArchiveIO()
        val backup = folder.resolve("backups/configuration/settings-${UUID.randomUUID()}.yml")
        try {
            io.writeDurable(backup, text)
            check(Files.readString(backup) == text) { "设置备份校验失败" }
            pending.forEach { (file, yaml) -> io.writeDurable(file, yaml.saveToString()) }
        } catch (error: Exception) {
            throw IllegalStateException("设置迁移失败 file=$path, path=components, version=combined->split, backup=$backup: ${error.message}", error)
        }
    }

    internal fun project(legacy: YamlConfiguration, defaults: YamlConfiguration, category: HomeFlag.Category?): YamlConfiguration {
        val result = YamlConfiguration().apply {
            options().parseComments(true)
            loadFromString(legacy.saveToString())
        }
        // The old file remains backed up verbatim; unknown root fields and comments also remain here.
        result.set("components", null)
        result.set("Shape", defaults.getStringList("Shape"))
        result.set("title", defaults.getString("title"))
        result.createSection("components", defaults.getConfigurationSection("components")!!.getValues(false))
        defaults.getConfigurationSection("status")?.getValues(false)?.forEach { (key, value) ->
            if (!result.contains("status.$key")) result.set("status.$key", value)
        }
        val oldComponents = legacy.getConfigurationSection("components")!!
        val allowed = HomeFlag.entries.filter { it.category == category }.map { "toggle:${it.name.lowercase()}" }.toSet()
        oldComponents.getKeys(false).forEach { id ->
            val action = legacy.getString("components.$id.action") ?: return@forEach
            if (action !in allowed) return@forEach
            val destination = defaults.getConfigurationSection("components")!!.getKeys(false).firstOrNull {
                defaults.getString("components.$it.action") == action
            } ?: return@forEach
            val symbol = defaults.getString("components.$destination.symbol")
            result.createSection("components.$destination", oldComponents.getConfigurationSection(id)!!.getValues(false))
            result.set("components.$destination.symbol", symbol)
        }
        return result
    }
}
