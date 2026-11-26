package ym.dreamSkyblock.config

import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class HomeManagementMigrationTest {
    private fun menu(name: String) = YamlConfiguration().apply {
        loadFromString(Files.readString(Path.of("src/main/resources/gui/$name.yml")))
    }

    @Test fun `grouped menu retains three actions and info statistics`() {
        val island = menu("island")
        val management = menu("management")
        HomeGuiConfig.validate("gui/island.yml", island)
        HomeGuiConfig.validate("gui/management.yml", management)
        assertEquals("menu:management", island.getString("components.info.action"))
        assertTrue(island.getStringList("components.info.lore").any { "<remaining_value>" in it })
        listOf("members", "settings", "help").forEach { id ->
            assertFalse(island.contains("components.$id"))
            assertEquals("menu:$id", management.getString("components.$id.action"))
        }
        assertEquals("menu:main", management.getString("components.back.action"))
    }

    @Test fun `legacy member customization moves once and custom management settings remain`() {
        val island = menu("island")
        island.set("Shape", listOf("W###I###H", "# M S   #", "#   T   #", "#      C#", "+###X###+"))
        val management = menu("management")
        val defaults = menu("management")
        listOf("members", "settings", "help").forEach { id ->
            defaults.getConfigurationSection("components.$id")!!.getValues(false).forEach { (key, value) -> island.set("components.$id.$key", value) }
        }
        island.set("components.members.name", "<#67E8F9>我的自定义成员")
        island.set("components.members.custom.extra", "preserved")
        management.set("components.settings.name", "<#67E8F9>自定义设置")
        HomeManagementMigration.apply(island, management, defaults)
        assertEquals("<#67E8F9>我的自定义成员", management.getString("components.members.name"))
        assertEquals("preserved", management.getString("components.members.custom.extra"))
        assertEquals("<#67E8F9>自定义设置", management.getString("components.settings.name"))
        assertEquals("W###I####", island.getStringList("Shape")[0])
        val snapshot = island.saveToString() + management.saveToString()
        HomeManagementMigration.apply(island, management, defaults)
        assertEquals(snapshot, island.saveToString() + management.saveToString())
        assertEquals(1, island.getStringList("components.info.lore").count { "点击管理空岛" in it })
    }
}
