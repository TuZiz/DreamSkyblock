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
        assertTrue(config.enabled)
        assertEquals("", config.mainMenu, "无岛玩家保留创建空岛流程")
        assertEquals("空岛菜单", config.islandMenu)
    }
    @Test fun `invalid enabled and ids report the exact configuration key`() {
        val yaml = YamlConfiguration().apply { set("enabled", "yes"); set("main-menu", "main"); set("island-menu", "island") }
        assertTrue(assertThrows(IllegalArgumentException::class.java) { InveroMenuConfig.read(yaml) }.message!!.contains("invero.yml: enabled"))
        yaml.set("enabled", true); yaml.set("island-menu", "../bad")
        assertTrue(assertThrows(IllegalStateException::class.java) { InveroMenuConfig.read(yaml) }.message!!.contains("invero.yml: island-menu"))
    }
    @Test fun `legacy ascii ids remain supported but unsafe file paths are rejected`() {
        val yaml = YamlConfiguration().apply {
            set("enabled", false)
            set("main-menu", "dreamskyblock-main")
            set("island-menu", "dreamskyblock-island")
        }
        assertEquals("dreamskyblock-island", InveroMenuConfig.read(yaml).islandMenu)
        for (id in listOf("", "../空岛菜单", "folder/menu", "菜单.yml", "菜单 name")) {
            yaml.set("island-menu", id)
            assertThrows(IllegalStateException::class.java) { InveroMenuConfig.read(yaml) }
        }
    }
}
