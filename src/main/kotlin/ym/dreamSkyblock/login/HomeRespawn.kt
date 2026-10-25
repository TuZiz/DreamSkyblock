package ym.dreamSkyblock.login

import com.destroystokyo.paper.event.player.PlayerPostRespawnEvent
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerRespawnEvent
import org.bukkit.event.player.PlayerTeleportEvent
import ym.dreamSkyblock.DreamSkyblock
import ym.dreamSkyblock.service.HomeManager
import ym.dreamSkyblock.world.HomeWorldManager
import ym.dreamSkyblock.world.PaperWorldTasks
import java.util.UUID
import java.util.logging.Level

/** Main-thread death/respawn coordination; never blocks the synchronous respawn event. */
class HomeRespawn(private val plugin: DreamSkyblock, private val homes: HomeManager,
                  private val worlds: HomeWorldManager, private val spawns: HomeSpawnService) : Listener {
    private class Session(val player: Player) {
        var ticket: HomeSpawnService.Ticket? = null
        var location: Location? = null
        var placed = false
        var teleporting = false
    }
    private val tasks = PaperWorldTasks(plugin)
    private val sessions = mutableMapOf<UUID, Session>()
    private var stopped = false

    @EventHandler(priority = EventPriority.MONITOR)
    fun death(event: PlayerDeathEvent) { begin(event.entity) }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun respawn(event: PlayerRespawnEvent) {
        val player = event.player
        // Explicit beds/anchors remain valid; home is the default when no respawn block applies.
        if (event.isBedSpawn || event.isAnchorSpawn || event.respawnReason == PlayerRespawnEvent.RespawnReason.END_PORTAL) {
            clear(player.uniqueId); return
        }
        if (!homes.ready || !player.hasPermission("dreamskyblock.use")) return
        val home = homes.byPlayer(player.uniqueId)?.takeIf { it.status == "ACTIVE" && !worlds.isBusy(it) } ?: return
        val world = worlds.getLoaded(home)
        if (world != null) {
            val point = home.spawn ?: plugin.configService.templates[home.templateId]?.spawn ?: return
            worlds.cancelUnload(home)
            event.respawnLocation = Location(world, point.x, point.y, point.z, point.yaw, point.pitch)
            // Release death-screen reservations after the player has actually been placed.
        } else if (sessions[player.uniqueId]?.player !== player) begin(player)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun placed(event: PlayerPostRespawnEvent) {
        val session = sessions[event.player.uniqueId]?.takeIf { it.player === event.player } ?: return
        session.placed = true
        finish(session)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun teleport(event: PlayerTeleportEvent) {
        val session = sessions[event.player.uniqueId] ?: return
        if (!session.teleporting) clear(event.player.uniqueId)
    }

    @EventHandler fun quit(event: PlayerQuitEvent) { clear(event.player.uniqueId) }

    private fun begin(player: Player) {
        clear(player.uniqueId)
        if (stopped || !homes.ready || !player.hasPermission("dreamskyblock.use") || homes.byPlayer(player.uniqueId) == null) return
        val session = Session(player)
        sessions[player.uniqueId] = session
        spawns.prepare(player.uniqueId).whenComplete { ticket, error -> tasks.global {
            if (!current(session)) { ticket?.let(spawns::release); return@global }
            if (error != null || ticket == null) { clear(player.uniqueId); return@global }
            session.ticket = ticket
            ticket.ready.whenComplete { location, failure -> tasks.global loaded@{
                if (!current(session)) return@loaded
                if (failure != null) {
                    plugin.logger.log(Level.WARNING, "死亡重生家园预加载失败 player=${player.uniqueId}, home=${ticket.home.id}", failure)
                    clear(player.uniqueId)
                } else { session.location = location; finish(session) }
            } }
        } }
    }

    private fun current(session: Session): Boolean = !stopped && plugin.isEnabled && session.player.isOnline &&
        sessions[session.player.uniqueId] === session

    private fun finish(session: Session) {
        val ticket = session.ticket ?: return
        val location = session.location ?: return
        val player = session.player
        if (!session.placed || !current(session)) return
        if (!spawns.valid(player.uniqueId, ticket) || !player.hasPermission("dreamskyblock.use")) { clear(player.uniqueId); return }
        if (player.world.uid == location.world.uid) { clear(player.uniqueId); return }
        session.teleporting = true
        try {
            player.teleportAsync(location).whenComplete { ok, error -> tasks.global {
                if (!current(session)) return@global
                if (error != null || ok != true) plugin.logger.log(Level.WARNING,
                    "重生回岛传送失败 player=${player.uniqueId}, home=${ticket.home.id}", error)
                clear(player.uniqueId)
            } }
        } catch (error: Exception) {
            plugin.logger.log(Level.WARNING, "重生回岛传送发起失败 player=${player.uniqueId}, home=${ticket.home.id}", error)
            clear(player.uniqueId)
        }
    }

    private fun clear(id: UUID) { sessions.remove(id)?.ticket?.let(spawns::release) }
    fun stop() {
        stopped = true
        sessions.keys.toList().forEach(::clear)
        tasks.close()
    }
}
