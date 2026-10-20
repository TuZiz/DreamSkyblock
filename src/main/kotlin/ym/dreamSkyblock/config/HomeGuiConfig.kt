package ym.dreamSkyblock.config

import org.bukkit.Material
import org.bukkit.configuration.file.YamlConfiguration
import ym.dreamSkyblock.DreamSkyblock
import ym.dreamSkyblock.gui.HomeMenu
import ym.dreamSkyblock.model.HomeFlag
import java.io.File

/** Loads and validates one chest screen per resource. Disk access belongs to the configuration IO task. */
class HomeGuiConfig(private val plugin: DreamSkyblock) {
    fun load(): Map<HomeMenu, YamlConfiguration> {
        HomeSettingsMigration.migrate(plugin.dataFolder.toPath(), ::defaults)
        return HomeMenu.entries.associateWith { menu ->
            val file = "gui/${menu.file}.yml"
            val path = File(plugin.dataFolder, file)
            if (!path.exists()) plugin.saveResource(file, false)
            VersionedYaml.load(path.toPath()).also { yaml ->
                migrate(menu, yaml)
                validate(file, yaml)
            }
        }
    }

    private fun defaults(menu: HomeMenu) = YamlConfiguration().apply {
        loadFromString(plugin.getResource("gui/${menu.file}.yml")!!.bufferedReader(Charsets.UTF_8).use { it.readText() })
    }

    private fun migrate(menu: HomeMenu, yaml: YamlConfiguration) {
        if (menu == HomeMenu.ISLAND) {
            val defaults = defaults(menu)
            migrateSpawn(yaml, defaults)
            migrateWarehouse(yaml, defaults)
        }
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
    }

    companion object {
        /** Add the warehouse at row four, column eight without rewriting the user's file. */
        internal fun migrateWarehouse(yaml: YamlConfiguration, defaults: YamlConfiguration) {
            if (yaml.contains("components.warehouse")) return
            val shape = yaml.getStringList("Shape").toMutableList()
            require(shape.size >= 4 && shape[3].length == 9) { "gui/island.yml: Shape 第四行必须为 9 个字符，无法添加中央仓库" }
            val used = yaml.getConfigurationSection("components")?.getKeys(false)
                ?.mapNotNull { yaml.getString("components.$it.symbol")?.singleOrNull() }.orEmpty()
            val symbol = "CABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789".firstOrNull { it !in used }
                ?: error("gui/island.yml: components.warehouse.symbol 没有可用字符")
            defaults.getConfigurationSection("components.warehouse")!!.getValues(false).forEach { (key, value) ->
                yaml.set("components.warehouse.$key", value)
            }
            yaml.set("components.warehouse.symbol", symbol.toString())
            shape[3] = shape[3].substring(0, 7) + symbol + shape[3].substring(8)
            yaml.set("Shape", shape)
        }

        /** Add the entry in memory; the user's file, comments and other slots remain intact. */
        internal fun migrateSpawn(yaml: YamlConfiguration, defaults: YamlConfiguration) {
            if (yaml.contains("components.spawn")) return
            val shape = yaml.getStringList("Shape").toMutableList()
            require(shape.isNotEmpty() && shape.first().length == 9) { "gui/island.yml: Shape 首行必须为 9 个字符" }
            val used = yaml.getConfigurationSection("components")?.getKeys(false)
                ?.mapNotNull { yaml.getString("components.$it.symbol")?.singleOrNull() }.orEmpty()
            val symbol = ("WABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789").firstOrNull { it !in used }
                ?: error("gui/island.yml: components.spawn.symbol 没有可用字符")
            defaults.getConfigurationSection("components.spawn")!!.getValues(false).forEach { (key, value) ->
                yaml.set("components.spawn.$key", value)
            }
            yaml.set("components.spawn.symbol", symbol.toString())
            shape[0] = symbol + shape[0].substring(1)
            yaml.set("Shape", shape)
        }

        /** In-memory migration retains the original file and custom layout/style/unknown fields. */
        internal fun migrateHelp(yaml: YamlConfiguration, defaults: YamlConfiguration) {
            val oldActions = mapOf("menu:help_start" to "start", "menu:help_team" to "team",
                "menu:settings" to "settings", "menu:help_levels" to "levels")
            val components = yaml.getConfigurationSection("components") ?: return
            val lorePath = "components.start.lore"
            yaml.set(lorePath, yaml.getStringList(lorePath).map {
                it.replace("/回家", "/家").replace("/home <#67E8F9>· 呼出空岛星海主菜单", "/home gui 或 /家 菜单 <#67E8F9>· 打开空岛菜单")
            })
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
            if (action in setOf("", "close", "create", "upgrade", "teleport", "spawn", "warehouse", "holograms", "previous", "next", "remove-member")) return true
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
