package ym.dreamSkyblock.config

import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class InveroMenuConfigTest {
    @Test fun `valid root config chooses separate main and island menus`() {
        val yaml = YamlConfiguration().apply { loadFromString(Files.readString(Path.of("src/main/resources/invero.yml"))) }
        val config = InveroMenuConfig.read(yaml)
        assertFalse(config.enabled, "服务器主菜单由 Invero 提供，空岛主页保留原菜单")
        assertEquals("dreamskyblock-main", config.mainMenu)
        assertEquals("dreamskyblock-island", config.islandMenu)
    }
    @Test fun `invalid enabled and ids report the exact configuration key`() {
        val yaml = YamlConfiguration().apply { set("enabled", "yes"); set("main-menu", "main"); set("island-menu", "island") }
        assertTrue(assertThrows(IllegalArgumentException::class.java) { InveroMenuConfig.read(yaml) }.message!!.contains("invero.yml: enabled"))
        yaml.set("enabled", true); yaml.set("island-menu", "../bad")
        assertTrue(assertThrows(IllegalStateException::class.java) { InveroMenuConfig.read(yaml) }.message!!.contains("invero.yml: island-menu"))
    }
}
