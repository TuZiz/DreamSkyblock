package ym.dreamSkyblock.config

import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration

/** Groups old home entries in memory; disk comments and custom management entries stay intact. */
internal object HomeManagementMigration {
    fun apply(island: YamlConfiguration, management: YamlConfiguration, defaults: YamlConfiguration) {
        val info = island.getConfigurationSection("components.info") ?: return
        info.set("action", "menu:management")
        val hint = defaults.getString("info-hint")!!
        val lore = info.getStringList("lore")
        if (hint !in lore) info.set("lore", lore + "" + hint)
        listOf("members", "settings", "help").forEach { id ->
            val old = island.getConfigurationSection("components.$id") ?: return@forEach
            val destination = management.getConfigurationSection("components.$id")!!
            val baseline = defaults.getConfigurationSection("components.$id")!!
            if (destination.getValues(true) == baseline.getValues(true)) {
                val symbol = destination.getString("symbol")!!
                old.getValues(true).filterValues { it !is ConfigurationSection }.forEach { (key, value) -> destination.set(key, value) }
                destination.set("symbol", symbol)
            }
            old.getString("symbol")?.singleOrNull()?.let { symbol ->
                island.set("Shape", island.getStringList("Shape").mapIndexed { rowIndex, row ->
                    // Old help occupies the top border; restore its border decoration.
                    row.mapIndexed { column, value ->
                        if (value == symbol) { if (id == "help" && rowIndex == 0 && column in listOf(0, 8)) '#' else ' ' } else value
                    }.joinToString("")
                })
            }
            island.set("components.$id", null)
        }
    }
}
