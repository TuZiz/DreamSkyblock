package ym.dreamSkyblock.config

import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import ym.dreamSkyblock.gui.HomeMenu
import java.nio.file.Files
import java.nio.file.Path

class HomeInformationMigrationTest {
    private fun defaults(menu: HomeMenu) = YamlConfiguration().apply {
        loadFromString(Files.readString(Path.of("src/main/resources/gui/${menu.file}.yml")))
    }

    @Test fun `legacy information and holograms move idempotently without losing custom content`() {
        val island = defaults(HomeMenu.ISLAND)
        val help = defaults(HomeMenu.HELP)
        listOf("members", "settings", "help").forEach { id ->
            defaults(HomeMenu.MANAGEMENT).getConfigurationSection("components.$id")!!.getValues(false)
                .forEach { (key, value) -> island.set("components.$id.$key", value) }
        }
        help.set("components.holograms", null)
        help.set("Shape", help.getStringList("Shape").map { it.replace('G',' ') })
        island.set("components.upgrade.symbol", "U")
        island.set("components.upgrade.action", "upgrade")
        island.set("components.upgrade.lore", listOf("<#00FF87>等级：<level>","<#00FF87>自定义统计：<experience>"))
        island.set("components.holograms.symbol", "G")
        island.set("components.holograms.material", "NAME_TAG")
        island.set("components.holograms.name", "<italic:false><#67E8F9>自定义全息字")
        island.set("components.holograms.lore", listOf("<#94A3B8>自定义说明"))
        island.set("components.holograms.action", "holograms")
        island.set("components.holograms.custom.extra", "preserved")
        island.set("Shape", listOf("W###I###H","# M S U #","#   T   #","#G     C#","+###X###+"))
        help.set("custom-field", "retained")
        val menus = mapOf(HomeMenu.ISLAND to island, HomeMenu.HELP to help)
        HomeInformationMigration.apply(menus, defaults(HomeMenu.HELP))
        assertFalse(island.contains("components.upgrade"))
        assertFalse(island.contains("components.holograms"))
        assertTrue(island.getStringList("components.info.lore").any { "自定义统计" in it })
        assertEquals(1, island.getStringList("components.info.lore").count { "<level>" in it })
        assertEquals("preserved", help.getString("components.holograms.custom.extra"))
        assertEquals("retained", help.getString("custom-field"))
        assertFalse(help.getString("components.holograms.name")!!.contains("italic"))
        val snapshot = island.saveToString() + help.saveToString()
        HomeInformationMigration.apply(menus, defaults(HomeMenu.HELP))
        assertEquals(snapshot, island.saveToString() + help.saveToString())
        HomeGuiConfig.validate("gui/island.yml", island)
        HomeGuiConfig.validate("gui/help.yml", help)
    }

    @Test fun `occupied help position and symbol keep existing buttons`() {
        val island = defaults(HomeMenu.ISLAND)
        val help = defaults(HomeMenu.HELP)
        help.set("components.holograms.action", "menu:main")
        val oldShape = help.getStringList("Shape")
        HomeInformationMigration.apply(mapOf(HomeMenu.ISLAND to island, HomeMenu.HELP to help), defaults(HomeMenu.HELP))
        assertEquals("menu:main", help.getString("components.holograms.action"))
        assertEquals("holograms", help.getString("components.holograms_.action"))
        assertNotEquals("G", help.getString("components.holograms_.symbol"))
        assertEquals('G', help.getStringList("Shape")[3][6])
        assertEquals(oldShape[3], help.getStringList("Shape")[3])
        HomeGuiConfig.validate("gui/help.yml", help)
    }
}
