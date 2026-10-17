package ym.dreamSkyblock.config

import org.bukkit.configuration.file.YamlConfiguration

object HomeValueThresholds {
    private val defaults = listOf(0L, 100L, 300L, 600L, 1000L, 1500L, 2100L, 2800L, 3600L)

    fun read(yaml: YamlConfiguration): List<Long> {
        require(yaml.getInt("default-level", 1) == 1) { "levels.yml: default-level 必须为 1，等级由当前空岛价值计算" }
        if (!yaml.contains("progression.thresholds")) return defaults
        val section = yaml.getConfigurationSection("progression.thresholds")
            ?: error("levels.yml: progression.thresholds 必须是 1-9 级累计价值映射")
        require(section.getKeys(false) == (1..9).map(Int::toString).toSet()) {
            "levels.yml: progression.thresholds 必须恰好包含 1-9 级，实际键=${section.getKeys(false)}"
        }
        val values = (1..9).map { level ->
            val value = section.get(level.toString())
            require(value is Int || value is Long) { "levels.yml: progression.thresholds.$level 必须为非负整数，实际值=$value" }
            (value as Number).toLong().also { require(it >= 0) { "levels.yml: progression.thresholds.$level 不能为负数" } }
        }
        require(values.first() == 0L && values.zipWithNext().all { (a, b) -> b > a }) {
            "levels.yml: progression.thresholds 的 1 级必须为 0，后续门槛必须严格递增，实际值=$values"
        }
        return values
    }
}
