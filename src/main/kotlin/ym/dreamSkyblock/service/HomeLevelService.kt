package ym.dreamSkyblock.service

import org.bukkit.configuration.file.YamlConfiguration
import ym.dreamSkyblock.config.HomeBorderSizes
import ym.dreamSkyblock.config.HomeValueThresholds
import ym.dreamSkyblock.model.Home
import kotlin.math.max

/** Pure value-to-level calculation. The full rules snapshot changes atomically on reload. */
class HomeLevelService(config: YamlConfiguration) {
    private data class Rules(val thresholds: List<Long>, val sizes: List<Double>)
    @Volatile private var rules = read(config)
    val initialLevel: Int get() = 1

    fun reload(newConfig: YamlConfiguration) { rules = read(newConfig) }
    private fun read(config: YamlConfiguration) = Rules(HomeValueThresholds.read(config), HomeBorderSizes.read(config))

    fun levelFor(value: Long): Int = rules.thresholds.indexOfLast { it <= value.coerceAtLeast(0) } + 1
    fun totalExperienceForLevel(level: Int): Long = rules.thresholds[level.coerceIn(1, 9) - 1]
    fun requiredExperienceForNext(level: Int): Long {
        val thresholds = rules.thresholds
        return if (level >= 9) 0 else thresholds[level.coerceAtLeast(1)] - thresholds[level.coerceAtLeast(1) - 1]
    }
    fun currentLevelExperience(home: Home): Long = if (home.level >= 9) 0 else
        (home.experience - totalExperienceForLevel(home.level)).coerceAtLeast(0)
    fun nextLevelTotalExperience(level: Int): Long = totalExperienceForLevel((level.coerceIn(1, 9) + 1).coerceAtMost(9))
    fun remainingValue(home: Home): Long = (nextLevelTotalExperience(home.level) - home.experience).coerceAtLeast(0)
    fun borderSizeFor(level: Int): Double = rules.sizes[level.coerceIn(1, 9) - 1]
    fun progress(home: Home): Double = if (home.level >= 9) 1.0 else
        (currentLevelExperience(home).toDouble() / requiredExperienceForNext(home.level)).coerceIn(0.0, 1.0)

    fun sanitize(home: Home) {
        home.experience = home.experience.coerceAtLeast(0)
        home.level = levelFor(home.experience)
        home.borderLevel = max(home.borderLevel, home.level)
        home.borderSize = max(home.borderSize, borderSizeFor(home.level)).coerceIn(16.0, 384.0)
    }
}
