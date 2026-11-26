package ym.dreamSkyblock.config

import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import ym.dreamSkyblock.gui.HomeMenu

/** In-memory menu migration: keeps disk comments and custom fields untouched. */
internal object HomeInformationMigration {
    fun apply(menus: Map<HomeMenu, YamlConfiguration>, helpDefaults: YamlConfiguration) {
        val island = menus.getValue(HomeMenu.ISLAND)
        mergeInformation(island)
        moveHolograms(island, menus.getValue(HomeMenu.HELP), helpDefaults)
        val help = menus.getValue(HomeMenu.HELP)
        if (help.contains("components.levels")) {
            help.set("components.levels.lore", help.getStringList("components.levels.lore").map {
                it.replace("可在主菜单升级信息中", "可在主菜单的我的空岛信息中")
            })
        }
    }

    private fun mergeInformation(yaml: YamlConfiguration) {
        val info = yaml.getConfigurationSection("components.info") ?: return
        val upgrade = yaml.getConfigurationSection("components.upgrade") ?: return
        if (upgrade.getString("action") != "upgrade") return
        val lore = info.getStringList("lore")
        val extra = upgrade.getStringList("lore").filter { line ->
            line !in lore && !("<level>" in line && lore.any { "<level>" in it })
        }
        if (extra.isNotEmpty()) info.set("lore", lore + "" + extra)
        remove(yaml, "upgrade", upgrade)
    }

    private fun moveHolograms(island: YamlConfiguration, help: YamlConfiguration, defaults: YamlConfiguration) {
        val old = island.getConfigurationSection("components.holograms")
        val existing = help.getConfigurationSection("components")!!.getKeys(false)
            .firstOrNull { help.getString("components.$it.action") == "holograms" }
        if (existing == null) {
            val source = old ?: defaults.getConfigurationSection("components.holograms") ?: return
            val shape = help.getStringList("Shape").toMutableList()
            val flat = shape.joinToString("")
            // Keep custom occupied cells; use the default position only when available.
            val preferred = 3 * 9 + 6
            val slot = preferred.takeIf { it < flat.length && flat[it] == ' ' }
                ?: flat.indexOf(' ').takeIf { it >= 0 }
                ?: error("gui/help.yml: Shape 没有空格可放置全息字开关，迁移来源 gui/island.yml")
            val used = help.getConfigurationSection("components")!!.getKeys(false)
                .mapNotNull { help.getString("components.$it.symbol")?.singleOrNull() }
            val symbol = "GABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789".firstOrNull { it !in used }
                ?: error("gui/help.yml: components.holograms.symbol 没有可用字符")
            var id = "holograms"
            while (help.contains("components.$id")) id += "_"
            source.getValues(true).filterValues { it !is ConfigurationSection }.forEach { (key, value) ->
                help.set("components.$id.$key", value)
            }
            help.set("components.$id.symbol", symbol.toString())
            // Rendering owns non-italic style, including migrated legacy configuration.
            help.set("components.$id.name", help.getString("components.$id.name")!!.replace("<italic:false>", ""))
            help.set("components.$id.lore", help.getStringList("components.$id.lore").map { it.replace("<italic:false>", "") })
            val row = slot / 9
            shape[row] = shape[row].replaceRange(slot % 9, slot % 9 + 1, symbol.toString())
            help.set("Shape", shape)
        }
        if (old != null) remove(island, "holograms", old)
    }

    private fun remove(yaml: YamlConfiguration, id: String, section: ConfigurationSection) {
        section.getString("symbol")?.singleOrNull()?.let { symbol ->
            yaml.set("Shape", yaml.getStringList("Shape").map { it.replace(symbol, ' ') })
        }
        yaml.set("components.$id", null)
    }
}
