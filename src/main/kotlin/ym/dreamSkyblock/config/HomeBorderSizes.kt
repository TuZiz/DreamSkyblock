package ym.dreamSkyblock.config

import org.bukkit.configuration.file.YamlConfiguration

/** The nine configurable island sizes; levels above nine keep the final tier. */
object HomeBorderSizes {
    private val defaults = listOf(48.0, 64.0, 96.0, 128.0, 160.0, 192.0, 256.0, 320.0, 384.0)

    fun read(yaml: YamlConfiguration): List<Double> {
        // Legacy files without the table use the new defaults; ConfigService logs this explicitly.
        if (!yaml.contains("border.sizes")) return defaults
        val section = yaml.getConfigurationSection("border.sizes")
            ?: error("levels.yml: border.sizes 必须为 1-9 级的映射，实际值=${yaml.get("border.sizes")}")
        val expected = (1..9).map(Int::toString).toSet()
        require(section.getKeys(false) == expected) { "levels.yml: border.sizes 必须恰好配置 1-9 级，实际键=${section.getKeys(false)}" }
        val sizes = (1..9).map { level ->
            val path = "border.sizes.$level"
            val value = yaml.get(path)
            require(value is Number && value.toDouble().isFinite() && value.toDouble() in 16.0..384.0) {
                "levels.yml: $path 必须为 16-384 的数值，实际值=$value"
            }
            value.toDouble()
        }
        require(sizes.zipWithNext().all { (previous, next) -> next >= previous }) {
            "levels.yml: border.sizes 必须按等级递增或保持不变，实际值=$sizes"
        }
        return sizes
    }
}
