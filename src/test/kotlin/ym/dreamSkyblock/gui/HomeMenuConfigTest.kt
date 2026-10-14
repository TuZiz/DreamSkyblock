package ym.dreamSkyblock.gui

import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import ym.dreamSkyblock.config.HomeGuiConfig
import ym.dreamSkyblock.config.HomeMainMenuMigration
import ym.dreamSkyblock.text.MessageService
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import java.nio.file.Files
import java.nio.file.Path

class HomeMenuConfigTest {
    @Test fun `every menu resource has a valid shape and action mapping`() {
        HomeMenu.entries.forEach { menu ->
            val path = Path.of("src/main/resources/gui/${menu.file}.yml")
            val yaml = YamlConfiguration().apply { loadFromString(Files.readString(path)) }
            assertEquals(1, yaml.getInt("schema-version"), path.toString())
            HomeGuiConfig.validate(path.toString(), yaml)
            assertTrue(yaml.getStringList("Shape").all { !it.contains('<') && it.length == 9 })
            assertFalse(Files.readString(path).contains("<italic:false>"), "统一渲染入口处理默认斜体")
        }
    }

    @Test fun `layout markup and unknown actions fail with configuration context`() {
        val path = Path.of("src/main/resources/gui/main.yml")
        val yaml = YamlConfiguration().apply { loadFromString(Files.readString(path)) }
        yaml.set("Shape", listOf("<italic:false>#########"))
        assertThrows(IllegalArgumentException::class.java) { HomeGuiConfig.validate("gui/main.yml", yaml) }
        yaml.set("Shape", listOf("#########"))
        yaml.set("components.create.action", "missing:action")
        assertThrows(IllegalArgumentException::class.java) { HomeGuiConfig.validate("gui/main.yml", yaml) }
    }

    @Test fun `single help migration replaces old links while preserving custom fields`() {
        val defaults = YamlConfiguration().apply { loadFromString(Files.readString(Path.of("src/main/resources/gui/help.yml"))) }
        val yaml = YamlConfiguration().apply { loadFromString(defaults.saveToString()) }
        val categories = mapOf("start" to "help_start", "team" to "help_team", "settings" to "settings", "levels" to "help_levels")
        categories.forEach { (id, target) ->
            assertFalse(defaults.contains("components.$id.action"))
            assertTrue(defaults.getStringList("components.$id.lore").size >= 10)
            yaml.set("components.$id.action", "menu:$target")
            yaml.set("components.$id.lore", listOf("old"))
        }
        yaml.set("title", "custom title")
        yaml.set("components.team.name", "custom name")
        yaml.set("custom-field", "retained")
        HomeGuiConfig.migrateHelp(yaml, defaults)
        val migrated = yaml.saveToString()
        HomeGuiConfig.migrateHelp(yaml, defaults)
        assertEquals(migrated, yaml.saveToString(), "migration is idempotent")
        assertEquals("custom title", yaml.getString("title"))
        assertEquals("custom name", yaml.getString("components.team.name"))
        assertEquals("retained", yaml.getString("custom-field"))
        assertEquals(defaults.getStringList("Shape"), yaml.getStringList("Shape"))
        categories.keys.forEach { id ->
            assertFalse(yaml.contains("components.$id.action"))
            assertEquals(defaults.getStringList("components.$id.lore"), yaml.getStringList("components.$id.lore"))
        }
        HomeGuiConfig.validate("gui/help.yml", yaml)
        assertEquals("menu:main", yaml.getString("components.back.action"))
    }

    @Test fun `legacy main merges preview into creation and keeps custom fields`() {
        val yaml = YamlConfiguration().apply { loadFromString(Files.readString(Path.of("src/main/resources/gui/main.yml"))) }
        yaml.set("Shape", listOf("#########", "# C P J #", "####H####", "####X####"))
        yaml.createSection("components.preview").apply {
            set("symbol", "P")
            set("material", "OAK_SAPLING")
            set("name", "custom preview")
            set("action", "menu:template_selection")
        }
        yaml.set("components.create.name", "custom creation")
        yaml.set("custom-field", "preserved")
        val originalLore = yaml.getStringList("components.create.lore")
        val defaults = YamlConfiguration().apply { loadFromString(Files.readString(Path.of("src/main/resources/gui/main.yml"))) }
        HomeMainMenuMigration.mergeCreationEntry(yaml, defaults)
        assertFalse(yaml.contains("components.preview"))
        assertEquals("# C   J #", yaml.getStringList("Shape")[1])
        assertEquals("custom creation", yaml.getString("components.create.name"))
        assertEquals(originalLore, yaml.getStringList("components.create.lore"))
        assertEquals("preserved", yaml.getString("custom-field"))
        assertEquals("menu:create_confirm", yaml.getString("components.create.action"))
        HomeGuiConfig.validate("gui/main.yml", yaml)
        val merged = yaml.saveToString()
        HomeMainMenuMigration.mergeCreationEntry(yaml, defaults)
        assertEquals(merged, yaml.saveToString())
        yaml.set("components.create.action", "menu:template_selection")
        yaml.set("components.create.name", "模板预览")
        yaml.set("components.create.lore", listOf("custom detail", "点击预览模板"))
        HomeMainMenuMigration.mergeCreationEntry(yaml, defaults)
        assertEquals("menu:create_confirm", yaml.getString("components.create.action"))
        assertEquals(defaults.getString("components.create.name"), yaml.getString("components.create.name"))
        assertTrue(yaml.getStringList("components.create.lore").contains("custom detail"))
        assertTrue(yaml.getStringList("components.create.lore").any { "<description>" in it })
        assertTrue(yaml.getStringList("components.create.lore").any { "<platform>" in it })
        assertFalse(yaml.getStringList("components.create.lore").any { "预览" in it })
        assertFalse(Files.exists(Path.of("src/main/resources/gui/template_selection.yml")))
    }

    @Test fun `dynamic lore inherits non italic and RGB from renderer`() {
        val messages = MessageService(YamlConfiguration())
        val component = messages.componentRaw("<#CBD5E1>邀请人：<inviter>", mapOf("inviter" to "<#C4B5FD>TestPlayer"))
        fun verify(node: Component, italic: TextDecoration.State = TextDecoration.State.NOT_SET) {
            val effective = node.decoration(TextDecoration.ITALIC).takeUnless { it == TextDecoration.State.NOT_SET } ?: italic
            assertEquals(TextDecoration.State.FALSE, effective)
            node.children().forEach { verify(it, effective) }
        }
        verify(component)
    }

    @Test fun `only skyblock is offered while legacy vanilla remains available for load`() {
        val fresh = YamlConfiguration().apply { loadFromString(Files.readString(Path.of("src/main/resources/templates.yml"))) }
        assertFalse(fresh.contains("templates.vanilla"))
        assertEquals("SKYBLOCK", fresh.getString("templates.skyblock.generator"))
        val retired = YamlConfiguration().apply { loadFromString(Files.readString(Path.of("src/main/resources/retired_templates.yml"))) }
        assertEquals("VANILLA", retired.getString("templates.vanilla.generator"))
    }
}
