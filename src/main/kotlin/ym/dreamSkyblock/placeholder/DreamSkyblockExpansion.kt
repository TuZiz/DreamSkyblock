package ym.dreamSkyblock.placeholder

import me.clip.placeholderapi.expansion.PlaceholderExpansion
import org.bukkit.entity.Player
import ym.dreamSkyblock.DreamSkyblock
import ym.dreamSkyblock.service.HomeLevelService

class DreamSkyblockExpansion(
    private val plugin: DreamSkyblock,
    private val levels: HomeLevelService
) : PlaceholderExpansion() {
    override fun getIdentifier(): String = "dreamskyblock"
    override fun getAuthor(): String = "ymxc"
    override fun getVersion(): String = plugin.description.version
    override fun persist(): Boolean = true
    override fun canRegister(): Boolean = plugin.server.pluginManager.isPluginEnabled("PlaceholderAPI")

    override fun getPlaceholders(): List<String> = listOf(
        "level",
        "experience",
        "exp",
        "next_experience",
        "next_exp",
        "required_experience",
        "required_exp",
        "current_level_experience",
        "current_level_exp",
        "next_level_total_experience",
        "next_level_total_exp",
        "progress",
        "exp_progress",
        "progress_percent",
        "exp_percent",
        "border",
        "border_size"
    )

    override fun onPlaceholderRequest(player: Player?, params: String): String? {
        val home = player?.let { plugin.homeManager.byPlayer(it.uniqueId) } ?: return ""
        return when (params.lowercase()) {
            "level" -> home.level.toString()
            "experience", "exp" -> home.experience.toString()
            "next_experience", "next_exp", "required_experience", "required_exp" -> levels.requiredExperienceForNext(home.level).toString()
            "current_level_experience", "current_level_exp" -> levels.currentLevelExperience(home).toString()
            "next_level_total_experience", "next_level_total_exp" -> levels.nextLevelTotalExperience(home.level).toString()
            "progress", "exp_progress" -> "%.4f".format(java.util.Locale.ROOT, levels.progress(home))
            "progress_percent", "exp_percent" -> "%.2f".format(java.util.Locale.ROOT, levels.progress(home) * 100.0)
            "border", "border_size" -> "%.2f".format(java.util.Locale.ROOT, levels.borderSizeFor(home.level))
            else -> null
        }
    }
}
