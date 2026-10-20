package ym.dreamSkyblock.config

import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ym.dreamSkyblock.gui.HomeMenu
import java.nio.file.Files
import java.nio.file.Path

class HomeSettingsMigrationTest {
    @TempDir lateinit var folder: Path
    private fun defaults(menu: HomeMenu) = YamlConfiguration().apply {
        loadFromString(Files.readString(Path.of("src/main/resources/gui/${menu.file}.yml")))
    }

    @Test fun `migration preserves original backup custom flag style and existing child then is idempotent`() {
        Files.createDirectories(folder.resolve("gui"))
        val legacy = defaults(HomeMenu.WORLD_RULES).apply {
            set("custom-field", "保留")
            set("components.pvp.name", "<#123456>自定义对战")
            set("components.pvp.material", "DIAMOND_SWORD")
            createSection("components.customVisitor", mapOf("symbol" to "!", "action" to "toggle:stranger_place", "material" to "STONE", "name" to "<#123456>自定义放置"))
        }
        val original = "# 原始中文注释\n" + legacy.saveToString()
        Files.writeString(folder.resolve("gui/settings.yml"), original)
        val existingChild = defaults(HomeMenu.VISITOR_PERMISSIONS).saveToString() + "# 玩家自定义菜单\n"
        Files.writeString(folder.resolve("gui/visitor_permissions.yml"), existingChild)
        HomeSettingsMigration.migrate(folder, ::defaults)
        val hub = VersionedYaml.load(folder.resolve("gui/settings.yml"))
        assertEquals("保留", hub.getString("custom-field"))
        assertEquals("menu:world_rules", hub.getString("components.rules.action"))
        assertFalse(hub.contains("components.pvp"))
        val rules = VersionedYaml.load(folder.resolve("gui/world_rules.yml"))
        assertEquals("<#123456>自定义对战", rules.getString("components.pvp.name"))
        assertEquals("DIAMOND_SWORD", rules.getString("components.pvp.material"))
        assertFalse(rules.contains("components.customVisitor"))
        assertEquals(existingChild, Files.readString(folder.resolve("gui/visitor_permissions.yml")))
        val backups = Files.list(folder.resolve("backups/configuration")).use { it.toList() }
        assertEquals(1, backups.size)
        assertEquals(original, Files.readString(backups.single()))
        val migrated = Files.readString(folder.resolve("gui/settings.yml"))
        HomeSettingsMigration.migrate(folder, ::defaults)
        assertEquals(migrated, Files.readString(folder.resolve("gui/settings.yml")))
        assertEquals(1, Files.list(folder.resolve("backups/configuration")).use { it.count() })
    }

    @Test fun `each category contains exactly its flags and no internal decay toggle`() {
        val all = mutableSetOf<String>()
        listOf(HomeMenu.WORLD_RULES, HomeMenu.VISITOR_PERMISSIONS).forEach { menu ->
            val yaml = defaults(menu)
            HomeGuiConfig.validate(menu.file, yaml)
            val actions = yaml.getConfigurationSection("components")!!.getKeys(false).mapNotNull { yaml.getString("components.$it.action") }.filter { it.startsWith("toggle:") }
            assertTrue(actions.all { it.startsWith("toggle:stranger_") == (menu == HomeMenu.VISITOR_PERMISSIONS) })
            all.addAll(actions)
        }
        assertEquals(ym.dreamSkyblock.model.HomeFlag.entries.map { "toggle:${it.name.lowercase()}" }.toSet(), all)
    }
}
