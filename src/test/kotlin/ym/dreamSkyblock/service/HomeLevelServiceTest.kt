package ym.dreamSkyblock.service

import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import ym.dreamSkyblock.model.Home
import ym.dreamSkyblock.model.HomeTemplate
import java.nio.file.Path
import java.util.UUID

class HomeLevelServiceTest {
    @Test fun `new default is 48 blocks with expansion capped at 384`() {
        val yaml = YamlConfiguration.loadConfiguration(Path.of("src/main/resources/levels.yml").toFile())
        val levels = HomeLevelService(yaml)
        val expected = listOf(48.0, 64.0, 96.0, 128.0, 160.0, 192.0, 256.0, 320.0, 384.0)
        expected.forEachIndexed { index, size -> assertEquals(size, levels.borderSizeFor(index + 1)) }
        assertEquals(48.0, levels.borderSizeFor(0))
        assertEquals(384.0, levels.borderSizeFor(100))
        assertEquals(384.0, levels.borderSizeFor(Int.MAX_VALUE))
        assertEquals(48.0, HomeLevelService(YamlConfiguration()).borderSizeFor(1))
    }

    @Test fun `reload preserves existing larger islands but still expands upgraded ones`() {
        val levels = HomeLevelService(YamlConfiguration())
        val home = Home(UUID.randomUUID(), "owner", "skyblock", HomeTemplate.Point(0.0, 100.0, 0.0), 100.0)
        levels.reload(YamlConfiguration())
        levels.sanitize(home)
        assertEquals(100.0, home.borderSize)
        home.experience = 2800
        levels.sanitize(home)
        assertEquals(320.0, home.borderSize)
    }

    @Test fun `explicit custom size remains configurable`() {
        val yaml = YamlConfiguration.loadConfiguration(Path.of("src/main/resources/levels.yml").toFile())
        yaml.set("border.sizes.1", 64)
        val levels = HomeLevelService(yaml)
        assertEquals(64.0, levels.borderSizeFor(1))
    }

    @Test fun `invalid tables fail with context and reload keeps previous settings`() {
        val levels = HomeLevelService(YamlConfiguration())
        for (value in listOf<Any>("large", 400, Double.NaN, 32)) {
            val yaml = YamlConfiguration.loadConfiguration(Path.of("src/main/resources/levels.yml").toFile())
            yaml.set("border.sizes.2", value)
            val error = assertThrows(IllegalArgumentException::class.java) { levels.reload(yaml) }
            assertTrue(error.message!!.contains("levels.yml: border.sizes"))
            assertEquals(1, levels.initialLevel)
            assertEquals(64.0, levels.borderSizeFor(2))
        }
        val incomplete = YamlConfiguration().apply { set("border.sizes.1", 48) }
        assertThrows(IllegalArgumentException::class.java) { HomeLevelService(incomplete) }
    }
}
