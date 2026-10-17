package ym.dreamSkyblock.value

import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import ym.dreamSkyblock.api.*
import ym.dreamSkyblock.model.Home
import ym.dreamSkyblock.model.HomeTemplate
import ym.dreamSkyblock.service.HomeLevelService
import java.util.UUID

class HomeValueLogicTest {
    private val levels = HomeLevelService(YamlConfiguration())
    private fun home() = Home(UUID.randomUUID(), "Owner", "skyblock", HomeTemplate.Point(0.0, 100.0, 0.0), 48.0)
    private val machine = HomeValueKey("machines", "machine-1")

    @Test fun `all cumulative thresholds and max level are exact`() {
        val thresholds = listOf(0L, 100L, 300L, 600L, 1000L, 1500L, 2100L, 2800L, 3600L)
        thresholds.forEachIndexed { index, threshold ->
            assertEquals(index + 1, levels.levelFor(threshold))
            if (index > 0) assertEquals(index, levels.levelFor(threshold - 1))
            assertEquals(threshold, levels.totalExperienceForLevel(index + 1))
        }
        assertEquals(9, levels.levelFor(Long.MAX_VALUE))
        assertEquals(0, levels.requiredExperienceForNext(9))
        val home = home().apply { experience = 650 }
        levels.sanitize(home)
        assertEquals(4, home.level)
        assertEquals(350, levels.remainingValue(home))
        assertEquals(50, levels.currentLevelExperience(home))
        assertEquals(.125, levels.progress(home))
    }

    @Test fun `placing updating and removing recompute levels without shrinking unlocked border`() {
        val home = home()
        HomeValueLogic.update(home, machine, 550, 1, levels)
        val second = HomeValueKey.block("machines", 1, 100, 2)
        HomeValueLogic.update(home, second, 100, 1, levels)
        assertEquals(650, home.experience); assertEquals(4, home.level); assertEquals(128.0, home.borderSize)
        HomeValueLogic.update(home, second, 0, 2, levels)
        assertEquals(550, home.experience); assertEquals(3, home.level); assertEquals(128.0, home.borderSize)
        HomeValueLogic.update(home, machine, 0, 2, levels)
        assertEquals(0, home.experience); assertEquals(1, home.level); assertEquals(4, home.borderLevel)
    }

    @Test fun `duplicate revisions do not accumulate and tombstones reject delayed placements`() {
        val home = home()
        assertEquals(HomeValueStatus.APPLIED, HomeValueLogic.update(home, machine, 100, 1, levels))
        assertEquals(HomeValueStatus.UNCHANGED, HomeValueLogic.update(home, machine, 100, 1, levels))
        assertThrows(IllegalArgumentException::class.java) { HomeValueLogic.update(home, machine, 200, 1, levels) }
        HomeValueLogic.update(home, machine, 0, 3, levels)
        assertEquals(HomeValueStatus.STALE, HomeValueLogic.update(home, machine, 100, 2, levels))
        assertEquals(0, home.experience)
        HomeValueLogic.update(home, machine, 300, 4, levels)
        assertEquals(300, home.experience)
    }

    @Test fun `overflow and invalid values do not mutate the candidate`() {
        val home = home()
        HomeValueLogic.update(home, machine, Long.MAX_VALUE, 1, levels)
        val second = HomeValueKey("machines", "second")
        assertThrows(ArithmeticException::class.java) { HomeValueLogic.update(home, second, 1, 1, levels) }
        assertFalse(home.valueRecords.containsKey(second))
        assertEquals(Long.MAX_VALUE, home.experience)
        assertThrows(IllegalArgumentException::class.java) { HomeValueLogic.update(home, machine, -1, 2, levels) }
    }

    @Test fun `threshold reload is atomic and rejects incomplete or non-increasing tables`() {
        for (invalid in listOf<Any>(-1, 0, 1.5, "100")) {
            val yaml = YamlConfiguration().apply {
                (1..9).forEach { set("progression.thresholds.$it", levels.totalExperienceForLevel(it)) }
                set("progression.thresholds.2", invalid)
            }
            assertThrows(IllegalArgumentException::class.java) { levels.reload(yaml) }
            assertEquals(100, levels.totalExperienceForLevel(2))
        }
    }
}
