package ym.dreamSkyblock.config

import org.bukkit.configuration.file.YamlConfiguration

data class InveroMenuConfig(val enabled: Boolean, val mainMenu: String, val islandMenu: String) {
    companion object {
        fun read(yaml: YamlConfiguration): InveroMenuConfig {
            require(yaml.isBoolean("enabled")) { "invero.yml: enabled 必须为布尔值" }
            fun id(key: String): String = yaml.getString(key)?.takeIf {
                (key == "main-menu" && it.isEmpty()) || it.matches(Regex("[\\p{L}\\p{N}][\\p{L}\\p{N}_-]*"))
            }
                ?: error("invero.yml: $key 无效: ${yaml.get(key)}")
            return InveroMenuConfig(yaml.getBoolean("enabled"), id("main-menu"), id("island-menu"))
        }
    }
}
