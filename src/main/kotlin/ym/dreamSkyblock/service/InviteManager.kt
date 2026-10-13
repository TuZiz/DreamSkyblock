package ym.dreamSkyblock.service

import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import org.bukkit.entity.Player
import ym.dreamSkyblock.DreamSkyblock
import ym.dreamSkyblock.config.ConfigService
import ym.dreamSkyblock.model.Home
import ym.dreamSkyblock.text.MessageService
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class HomeInvite(val token: String, val homeId: UUID, val inviter: UUID, val target: UUID, val expiresAt: Instant)

class InviteManager(private val plugin: DreamSkyblock, private val config: ConfigService, private val homes: HomeManager, private val messages: MessageService) {
    private val invites = ConcurrentHashMap<String, HomeInvite>()

    fun tokensFor(target: UUID): List<String> {
        val now = Instant.now()
        invites.entries.removeIf { it.value.expiresAt.isBefore(now) }
        return invites.values.filter { it.target == target && it.expiresAt.isAfter(now) }.map { it.token }.sorted()
    }

    fun create(owner: Player, target: Player, home: Home) {
        val now = Instant.now()
        invites.entries.removeIf { it.value.expiresAt.isBefore(now) }
        if (owner.uniqueId == target.uniqueId) return messages.send(owner, "invite.self")
        if (homes.byPlayer(target.uniqueId) != null) return messages.send(owner, "invite.target-has-home")
        if (!home.hasRoom()) return messages.send(owner, "invite.full")
        if (invites.values.any { it.homeId == home.id && it.target == target.uniqueId && it.expiresAt.isAfter(now) }) return messages.send(owner, "invite.duplicate")
        val invite = HomeInvite(UUID.randomUUID().toString().replace("-", "").take(12), home.id, owner.uniqueId, target.uniqueId, now.plusSeconds(config.inviteExpirySeconds))
        invites[invite.token] = invite
        val message = messages.component("invite.received", mapOf("player" to owner.name))
            .append(messages.component("invite.accept-button").clickEvent(ClickEvent.runCommand("/dsb accept ${invite.token}")).hoverEvent(HoverEvent.showText(messages.component("invite.accept-hover"))))
            .append(messages.component("invite.deny-button").clickEvent(ClickEvent.runCommand("/dsb deny ${invite.token}")))
        target.getScheduler().execute(plugin, { if (target.isOnline) target.sendMessage(message) }, null, 0L)
        messages.send(owner, "invite.sent", mapOf("player" to target.name))
    }

    fun accept(player: Player, token: String) {
        val invite = invites[token] ?: return messages.send(player, "invite.invalid")
        if (invite.target != player.uniqueId) return messages.send(player, "invite.invalid")
        if (invite.expiresAt.isBefore(Instant.now())) {
            invites.remove(token, invite)
            return messages.send(player, "invite.expired")
        }
        if (!invites.remove(token, invite)) return messages.send(player, "invite.invalid")
        val home = homes.byId(invite.homeId) ?: return messages.send(player, "invite.invalid")
        if (!homes.addOwner(home, player)) return messages.send(player, "invite.cannot-accept")
        messages.send(player, "invite.accepted")
        homes.teleport(player)
    }

    fun deny(player: Player, token: String) {
        val invite = invites[token] ?: return messages.send(player, "invite.invalid")
        if (invite.target != player.uniqueId) return messages.send(player, "invite.invalid")
        if (!invites.remove(token, invite)) return messages.send(player, "invite.invalid")
        messages.send(player, "invite.denied")
    }
}
