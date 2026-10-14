package ym.dreamSkyblock.config

import org.bukkit.configuration.file.YamlConfiguration

/** Merges legacy duplicate entry buttons in memory without rewriting the user's configuration. */
internal object HomeMainMenuMigration {
    fun mergeCreationEntry(yaml: YamlConfiguration, defaults: YamlConfiguration) {
        val components = yaml.getConfigurationSection("components") ?: return
        val ids = components.getKeys(false).toList()
        val previews = ids.filter { yaml.getString("components.$it.action") == "menu:template_selection" }
        val create = ids.firstOrNull { yaml.getString("components.$it.action") == "menu:create_confirm" }
            ?: previews.firstOrNull()?.also { yaml.set("components.$it.action", "menu:create_confirm") }
            ?: return
        previews.filter { it != create }.forEach { id ->
            val path = "components.$id"
            val symbol = yaml.getString("$path.symbol")?.singleOrNull()
            if (symbol != null) {
                yaml.set("Shape", yaml.getStringList("Shape").map { it.replace(symbol, ' ') })
            }
            yaml.set(path, null)
        }
        val path = "components.$create"
        if (yaml.getString("$path.name", "")!!.contains("模板预览")) {
            yaml.set("$path.name", defaults.getString("components.create.name"))
        }
        val oldLore = yaml.getStringList("$path.lore")
        val lore = oldLore.filterNot { it.contains("预览") || it.contains("先查看模板") }.toMutableList()
        val defaultLore = defaults.getStringList("components.create.lore")
        listOf("<description>", "<platform>").forEach { marker ->
            if (lore.none { marker in it }) defaultLore.firstOrNull { marker in it }?.let(lore::add)
        }
        if (lore != oldLore) {
            val hint = defaultLore.lastOrNull()
            if (oldLore.any { it.contains("预览") } && hint != null && hint !in lore) lore.add(hint)
            yaml.set("$path.lore", lore)
        }
    }
}
