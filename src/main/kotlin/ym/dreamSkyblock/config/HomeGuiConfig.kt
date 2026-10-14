package ym.dreamSkyblock.config

import org.bukkit.Material
import org.bukkit.configuration.file.YamlConfiguration
import ym.dreamSkyblock.DreamSkyblock
import ym.dreamSkyblock.gui.HomeMenu
import ym.dreamSkyblock.model.HomeFlag
import java.io.File

/** Loads and validates one chest screen per resource. Disk access belongs to the configuration IO task. */
class HomeGuiConfig(private val plugin: DreamSkyblock) {
    fun load(): Map<HomeMenu, YamlConfiguration> = HomeMenu.entries.associateWith { menu ->
        val file = "gui/${menu.file}.yml"
        val path = File(plugin.dataFolder, file)
        if (!path.exists()) plugin.saveResource(file, false)
        VersionedYaml.load(path.toPath()).also { yaml ->
            migrate(menu, yaml)
            validate(file, yaml)
        }
    }

    private fun migrate(menu: HomeMenu, yaml: YamlConfiguration) {
        val components = yaml.getConfigurationSection("components") ?: return
        if (menu == HomeMenu.MAIN) {
            val defaults = YamlConfiguration().apply {
                loadFromString(plugin.getResource("gui/main.yml")!!.bufferedReader(Charsets.UTF_8).use { it.readText() })
            }
            HomeMainMenuMigration.mergeCreationEntry(yaml, defaults)
        }
        if (menu == HomeMenu.HELP) {
            val defaults = YamlConfiguration().apply {
                loadFromString(plugin.getResource("gui/help.yml")!!.bufferedReader(Charsets.UTF_8).use { it.readText() })
            }
            migrateHelp(yaml, defaults)
        }
        if (menu == HomeMenu.SETTINGS && yaml.getStringList("Shape").let { it.size == 6 && it[4] == "#vwxyzA #" && it[5] == "####C####" }) {
            yaml.set("Shape", listOf("#########", "#abcdefg#", "#hijklmn#", "#opqrstu#", "#vwxyz  #", "####A####"))
            yaml.set("components.C", null)
        }
        if (menu == HomeMenu.SETTINGS && yaml.contains("components.keep_inventory")) {
            val symbol = yaml.getString("components.keep_inventory.symbol")
            yaml.set("Shape", yaml.getStringList("Shape").map { if (symbol?.length == 1) it.replace(symbol[0], ' ') else it })
            yaml.set("components.keep_inventory", null)
        }
        if (menu == HomeMenu.SETTINGS) {
            val defaults = YamlConfiguration().apply {
                loadFromString(plugin.getResource("gui/settings.yml")!!.bufferedReader(Charsets.UTF_8).use { it.readText() })
            }
            listOf("status.preview", "status.manage").forEach { path -> if (!yaml.contains(path)) yaml.set(path, defaults.get(path)) }
            if (!yaml.contains("components.guide")) {
                val shape = yaml.getStringList("Shape").toMutableList()
                val row = shape.indices.reversed().firstOrNull { shape[it].contains(' ') }
                if (row != null && shape.none { '?' in it }) {
                    shape[row] = shape[row].replaceFirst(' ', '?')
                    yaml.set("Shape", shape)
                    yaml.createSection("components.guide", defaults.getConfigurationSection("components.guide")!!.getValues(false))
                }
            }
        }
        components.getKeys(false).toList().forEach { id ->
            val path = "components.$id"
            val action = yaml.getString("$path.action", "")!!
            if (menu == HomeMenu.SETTINGS && id == "back" && action == "close") {
                yaml.set("$path.action", "menu:main")
            }
        }
    }

    companion object {
        /** In-memory migration retains the original file and custom layout/style/unknown fields. */
        internal fun migrateHelp(yaml: YamlConfiguration, defaults: YamlConfiguration) {
            val oldActions = mapOf("menu:help_start" to "start", "menu:help_team" to "team",
                "menu:settings" to "settings", "menu:help_levels" to "levels")
            val components = yaml.getConfigurationSection("components") ?: return
            components.getKeys(false).forEach { id ->
                val path = "components.$id"
                val category = oldActions[yaml.getString("$path.action")] ?: return@forEach
                yaml.set("$path.lore", defaults.getStringList("components.$category.lore"))
                yaml.set("$path.action", null)
            }
        }

        fun validate(file: String, yaml: YamlConfiguration) {
            val shape = yaml.getStringList("Shape")
            require(shape.size in 1..6 && shape.all { it.length == 9 }) { "$file: Shape 必须为 1-6 行，每行 9 个字符" }
            require(!yaml.getString("title").isNullOrBlank()) { "$file: title 不能为空" }
            val components = yaml.getConfigurationSection("components") ?: error("$file: components 缺失")
            val symbols = mutableSetOf<Char>()
            components.getKeys(false).forEach { id ->
                val path = "components.$id"
                val section = components.getConfigurationSection(id) ?: error("$file: $path 必须为映射")
                val symbol = section.getString("symbol")?.singleOrNull() ?: error("$file: $path.symbol 必须为单个字符")
                require(symbol != ' ' && symbols.add(symbol)) { "$file: $path.symbol 重复或为空格: $symbol" }
                require(Material.matchMaterial(section.getString("material", "")!!) != null) { "$file: $path.material 无效" }
                require(section.isString("name")) { "$file: $path.name 缺失" }
                val repeat = section.getString("repeat")
                require(repeat == null || repeat in setOf("invites", "members", "players")) { "$file: $path.repeat 无效: $repeat" }
                val action = section.getString("action", "")!!
                require(validAction(action)) { "$file: $path.action 无效: $action" }
            }
            require(shape.flatMap(String::toList).filter { it != ' ' }.all { it in symbols }) { "$file: Shape 含未定义组件字符" }
        }

        private fun validAction(action: String): Boolean {
            if (action in setOf("", "close", "create", "upgrade", "teleport", "previous", "next", "remove-member")) return true
            val value = action.substringAfter(':', "")
            return when (action.substringBefore(':')) {
                "menu" -> value == "main" || HomeMenu.entries.any { it.file == value }
                "toggle" -> HomeFlag.entries.any { it.name.equals(value, true) }
                "confirm", "accept", "invite", "member" -> value.isNotBlank()
                else -> false
            }
        }
    }
}
