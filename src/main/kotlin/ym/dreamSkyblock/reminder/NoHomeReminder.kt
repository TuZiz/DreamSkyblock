package ym.dreamSkyblock.reminder

import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import ym.dreamSkyblock.DreamSkyblock
import ym.dreamSkyblock.gui.HomeMenuHolder
import ym.dreamSkyblock.service.HomeManager
import ym.dreamSkyblock.text.MessageService
import java.util.UUID

/** Paper/Purpur/Leaf main thread only. A single owned task handles online reminder sessions, without IO. */
class NoHomeReminder @JvmOverloads constructor(private val plugin: DreamSkyblock, private val homes: HomeManager,
                     private val messages: MessageService,
                     private val viewing: (Player) -> Boolean = { it.openInventory.topInventory.holder is HomeMenuHolder }) : Listener {
    private data class Session(val player: Player, var seconds: Int = 3)
    private val sessions = mutableMapOf<UUID, Session>()
    private var task: ScheduledTask? = null

    fun start() {
        if (task != null) return
        task = plugin.server.globalRegionScheduler.runAtFixedRate(plugin, { pulse() }, 20L, 20L)
        plugin.server.onlinePlayers.forEach(::track)
    }

    @EventHandler
    fun join(event: PlayerJoinEvent) {
        sessions.remove(event.player.uniqueId)
        if (task != null) track(event.player)
    }

    @EventHandler
    fun quit(event: PlayerQuitEvent) {
        sessions.remove(event.player.uniqueId)
    }

    private fun track(player: Player) {
        if (player.isOnline && player.hasPermission("dreamskyblock.use") && homes.byPlayer(player.uniqueId) == null) {
            sessions.putIfAbsent(player.uniqueId, Session(player))
        }
    }

    private fun pulse() {
        if (!plugin.isEnabled || task == null || !homes.ready) return
        // Includes players who leave or delete an island while staying online.
        plugin.server.onlinePlayers.forEach(::track)
        sessions.toMap().forEach { (id, session) ->
            val player = session.player
            if (!player.isOnline || !player.hasPermission("dreamskyblock.use") || homes.byPlayer(id) != null) {
                sessions.remove(id)
                return@forEach
            }
            if (viewing(player)) return@forEach
            if (--session.seconds <= 0) {
                messages.send(player, "home.no-island-reminder")
                session.seconds = 15
            }
        }
    }

    fun stop() {
        task?.cancel()
        task = null
        sessions.clear()
    }
}
