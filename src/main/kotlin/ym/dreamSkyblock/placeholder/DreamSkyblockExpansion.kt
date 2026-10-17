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
        "value", "remaining_value", "next_value_threshold",
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
        "border_size",
        "owner_name", "member_count", "member_limit", "member_names", "invite_count", "platform_size", "template_name"
    )

    override fun onPlaceholderRequest(player: Player?, params: String): String? {
        val key = params.lowercase()
        if (key == "invite_count") return player?.let { plugin.homeManager.invites.invitationsFor(it.uniqueId).size.toString() } ?: "0"
        val template = plugin.configService.creationTemplates.values.firstOrNull()
        if (key == "platform_size") return template?.platformSize?.toString() ?: ""
        if (key == "template_name") return template?.displayName?.replace(Regex("<[^>]+>"), "") ?: ""
        val home = player?.let { plugin.homeManager.byPlayer(it.uniqueId) } ?: return ""
        return when (key) {
            "owner_name" -> home.ownerName
            "member_count" -> home.owners.size.toString()
            "member_limit" -> home.memberLimit.toString()
            "member_names" -> home.ownerNames.values.sorted().joinToString("、")
            "level" -> home.level.toString()
            "experience", "exp", "value" -> home.experience.toString()
            "remaining_value" -> levels.remainingValue(home).toString()
            "next_value_threshold" -> levels.nextLevelTotalExperience(home.level).toString()
            "next_experience", "next_exp", "required_experience", "required_exp" -> levels.requiredExperienceForNext(home.level).toString()
            "current_level_experience", "current_level_exp" -> levels.currentLevelExperience(home).toString()
            "next_level_total_experience", "next_level_total_exp" -> levels.nextLevelTotalExperience(home.level).toString()
            "progress", "exp_progress" -> "%.4f".format(java.util.Locale.ROOT, levels.progress(home))
            "progress_percent", "exp_percent" -> "%.2f".format(java.util.Locale.ROOT, levels.progress(home) * 100.0)
            "border", "border_size" -> "%.2f".format(java.util.Locale.ROOT, home.borderSize)
            else -> null
        }
    }
}
