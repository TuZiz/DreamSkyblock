package ym.dreamSkyblock.gui

import org.bukkit.entity.Player
import ym.dreamSkyblock.config.ConfigService
import ym.dreamSkyblock.model.Home
import ym.dreamSkyblock.service.HomeLevelService
import ym.dreamSkyblock.service.HomeManager
import ym.dreamSkyblock.text.MessageService
import java.time.Instant
import java.util.Locale

/** Captures display values from main-thread game state; no disk or database reads. */
class HomeMenuData(private val config: ConfigService, private val homes: HomeManager,
                   private val levels: HomeLevelService, private val messages: MessageService) {
    fun values(player: Player, home: Home?, request: MenuRequest): Map<String, String> {
        val template = (request.template ?: config.creationTemplates.keys.firstOrNull())?.let { config.creationTemplates[it] }
        val selected = request.member
        return mapOf(
            "player" to player.name,
            "invite_count" to homes.invites.invitationsFor(player.uniqueId).size.toString(),
            "owner" to (home?.ownerName ?: player.name),
            "home_id" to (home?.id?.toString() ?: ""),
            "level" to (home?.level ?: levels.initialLevel).toString(),
            "experience" to (home?.experience ?: 0).toString(),
            "required" to (home?.let { levels.requiredExperienceForNext(it.level) } ?: levels.requiredExperienceForNext(levels.initialLevel)).toString(),
            "border" to String.format(Locale.ROOT, "%.0f", home?.borderSize ?: levels.borderSizeFor(levels.initialLevel)),
            "members" to (home?.owners?.size ?: 0).toString(),
            "limit" to (home?.memberLimit ?: config.maxMembers).toString(),
            "state" to messages.raw("gui.state.${home?.status?.lowercase() ?: "active"}"),
            "settings_access" to config.menus.getValue(HomeMenu.SETTINGS).getString(if (home == null) "status.preview" else "status.manage")!!,
            "template" to (template?.displayName ?: ""),
            "description" to (template?.description?.joinToString("<newline>") ?: ""),
            "name" to (template?.displayName ?: ""),
            "template_id" to (template?.id ?: ""),
            "platform" to (template?.platformSize ?: 7).toString(),
            "member" to (selected?.let { home?.ownerNames?.get(it) ?: it.toString() } ?: ""),
            "member_id" to (selected?.toString() ?: ""),
            "member_action" to config.menus.getValue(HomeMenu.MEMBER_CONFIRM).getString(if (selected == player.uniqueId) "labels.leave" else "labels.remove")!!
        )
    }

    fun entries(player: Player, home: Home?, menu: HomeMenu): List<MenuEntry> = when (menu) {
        HomeMenu.INVITES -> homes.invites.invitationsFor(player.uniqueId).mapNotNull { invite ->
            homes.byId(invite.homeId)?.let { invited -> MenuEntry(mapOf(
                "inviter" to (invited.ownerNames[invite.inviter] ?: invite.inviter.toString()),
                "token" to invite.token, "members" to invited.owners.size.toString(), "limit" to invited.memberLimit.toString(),
                "seconds" to (invite.expiresAt.epochSecond - Instant.now().epochSecond).coerceAtLeast(0).toString()
            )) }
        }
        HomeMenu.MEMBERS -> home?.owners?.sortedBy { home.ownerNames[it] ?: it.toString() }?.map { member -> MenuEntry(mapOf(
            "member" to (home.ownerNames[member] ?: member.toString()), "member_id" to member.toString(),
            "online" to messages.raw(if (player.server.getPlayer(member)?.isOnline == true) "gui.online" else "gui.offline")
        )) } ?: emptyList()
        HomeMenu.PLAYERS -> player.server.onlinePlayers.filter { it.uniqueId != player.uniqueId && homes.byPlayer(it.uniqueId) == null }
            .sortedBy { it.name }.map { MenuEntry(mapOf("target" to it.name, "target_id" to it.uniqueId.toString())) }
        else -> emptyList()
    }

}
