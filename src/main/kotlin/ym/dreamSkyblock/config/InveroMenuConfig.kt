package ym.dreamSkyblock.config

import org.bukkit.configuration.file.YamlConfiguration

data class InveroMenuConfig(val enabled: Boolean, val mainMenu: String, val islandMenu: String) {
    companion object {
        fun read(yaml: YamlConfiguration): InveroMenuConfig {
            require(yaml.isBoolean("enabled")) { "invero.yml: enabled 必须为布尔值" }
            fun id(key: String): String = yaml.getString(key)?.takeIf { it.matches(Regex("[a-z0-9][a-z0-9_-]*")) }
                ?: error("invero.yml: $key 无效: ${yaml.get(key)}")
            return InveroMenuConfig(yaml.getBoolean("enabled"), id("main-menu"), id("island-menu"))
        }
    }
}
