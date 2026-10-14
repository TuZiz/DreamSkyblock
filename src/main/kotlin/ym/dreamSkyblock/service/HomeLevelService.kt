package ym.dreamSkyblock.service

import org.bukkit.configuration.file.YamlConfiguration
import ym.dreamSkyblock.model.Home
import kotlin.math.max

/** 只负责等级公式与边界计算；经验来源和升级触发暂未接入。 */
class HomeLevelService(private val config: YamlConfiguration) {
    @Volatile var initialLevel: Int = config.getInt("default-level", 1).coerceAtLeast(1)
        private set
    @Volatile private var firstUpgradeExperience = config.getLong("progression.first-upgrade-experience", 1L).coerceAtLeast(1L)
    @Volatile private var experienceMultiplier = config.getLong("progression.multiplier", 2L).coerceAtLeast(2L)
    @Volatile private var baseBorderSize = config.getDouble("border.base-size", 100.0).coerceAtLeast(1.0)
    @Volatile private var borderPerLevel = config.getDouble("border.per-level", 10.0).coerceAtLeast(0.0)

    fun reload(newConfig: YamlConfiguration) {
        initialLevel = newConfig.getInt("default-level", 1).coerceAtLeast(1)
        firstUpgradeExperience = newConfig.getLong("progression.first-upgrade-experience", 1L).coerceAtLeast(1L)
        experienceMultiplier = newConfig.getLong("progression.multiplier", 2L).coerceAtLeast(2L)
        baseBorderSize = newConfig.getDouble("border.base-size", 100.0).coerceAtLeast(1.0)
        borderPerLevel = newConfig.getDouble("border.per-level", 10.0).coerceAtLeast(0.0)
    }

    fun requiredExperienceForNext(level: Int): Long {
        val safeLevel = level.coerceAtLeast(1)
        return firstUpgradeExperience.saturatingPower(experienceMultiplier, safeLevel - 1L)
    }

    /** 总经验达到此值时进入指定等级。1 级的总经验为 0。 */
    fun totalExperienceForLevel(level: Int): Long {
        val safeLevel = level.coerceAtLeast(1)
        if (safeLevel > 64) return Long.MAX_VALUE
        var total = 0L
        for (current in 1 until safeLevel) total = total.saturatingAdd(requiredExperienceForNext(current))
        return total
    }

    fun currentLevelExperience(home: Home): Long =
        (home.experience - totalExperienceForLevel(home.level)).coerceAtLeast(0L)

    fun nextLevelTotalExperience(level: Int): Long =
        totalExperienceForLevel(level).saturatingAdd(requiredExperienceForNext(level))

    fun borderSizeFor(level: Int): Double = (baseBorderSize + borderPerLevel * (level.coerceAtLeast(1) - 1)).coerceIn(16.0, 384.0)

    fun progress(home: Home): Double {
        val required = requiredExperienceForNext(home.level)
        return (currentLevelExperience(home).toDouble() / required.toDouble()).coerceIn(0.0, 1.0)
    }

    fun sanitize(home: Home) {
        home.level = max(initialLevel, home.level)
        home.experience = home.experience.coerceAtLeast(0L)
        home.borderSize = borderSizeFor(home.level)
    }

    private fun Long.saturatingMultiply(other: Long): Long =
        if (this == 0L || other == 0L) 0L
        else if (this > Long.MAX_VALUE / other) Long.MAX_VALUE
        else this * other

    private fun Long.saturatingPower(base: Long, exponent: Long): Long {
        var result = this
        repeat(exponent.coerceAtMost(63).toInt()) { result = result.saturatingMultiply(base) }
        return result
    }

    private fun Long.saturatingAdd(other: Long): Long =
        if (Long.MAX_VALUE - this < other) Long.MAX_VALUE else this + other
}
