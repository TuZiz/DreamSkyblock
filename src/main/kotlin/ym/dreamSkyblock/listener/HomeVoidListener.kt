package ym.dreamSkyblock.listener

import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.world.WorldUnloadEvent
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.util.Vector
import java.util.UUID

/** Paper main thread only. Same-column rescue uses the player's already loaded chunk. */
class HomeVoidListener(private val isHomeWorld: (World) -> Boolean) : Listener {
    private data class Descent(val destination: Location, var arriving: Boolean = true)
    private val descents = java.util.concurrent.ConcurrentHashMap<UUID, Descent>()

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun move(event: PlayerMoveEvent) {
        if (event is PlayerTeleportEvent) return
        val player = event.player
        val to = event.to
        if (!isHomeWorld(to.world)) { descents.remove(player.uniqueId); return }
        if (to.y < to.world.minHeight - 8.0) {
            val target = top(to)
            descents[player.uniqueId] = Descent(target)
            player.fallDistance = 0f
            player.velocity = Vector()
            event.to = target
        } else {
            val descent = descents[player.uniqueId] ?: return
            // Reset throughout this rescue descent, including the landing move. A later
            // ordinary fall must start accumulating its own distance again.
            player.fallDistance = 0f
            if (descent.arriving) { descent.arriving = false; return }
            // Landing, flying or entering water ends this descent, not all future falls.
            if (player.isOnGround || player.isFlying || player.isGliding || player.isInWater) descents.remove(player.uniqueId)
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun moved(event: PlayerMoveEvent) {
        if (event is PlayerTeleportEvent) return
        val descent = descents[event.player.uniqueId] ?: return
        if (descent.arriving && (event.isCancelled || event.to != descent.destination)) {
            descents.remove(event.player.uniqueId)
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun damage(event: EntityDamageEvent) {
        val player = event.entity as? Player ?: return
        if (!isHomeWorld(player.world)) return
        if (event.cause == EntityDamageEvent.DamageCause.FALL && descents.remove(player.uniqueId) != null) {
            event.isCancelled = true
            player.fallDistance = 0f
        } else if (event.cause == EntityDamageEvent.DamageCause.VOID) {
            // Fallback for players already below the void threshold without another move packet.
            val target = top(player.location)
            val descent = Descent(target)
            descents[player.uniqueId] = descent
            if (player.teleport(target, PlayerTeleportEvent.TeleportCause.PLUGIN)) {
                event.isCancelled = true
                player.fallDistance = 0f
                player.velocity = Vector()
            } else descents.remove(player.uniqueId, descent)
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun teleport(event: PlayerTeleportEvent) {
        val descent = descents[event.player.uniqueId] ?: return
        if (!event.isCancelled && descent.arriving && event.to == descent.destination) descent.arriving = false
        else descents.remove(event.player.uniqueId)
    }

    @EventHandler fun quit(event: PlayerQuitEvent) { descents.remove(event.player.uniqueId) }
    @EventHandler fun changed(event: PlayerChangedWorldEvent) { descents.remove(event.player.uniqueId) }
    @EventHandler fun death(event: PlayerDeathEvent) { descents.remove(event.entity.uniqueId) }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun unload(event: WorldUnloadEvent) { descents.entries.removeIf { it.value.destination.world.uid == event.world.uid } }

    private fun top(location: Location): Location = location.clone().apply { y = world.maxHeight - 2.0 }
    fun stop() { descents.clear() }
}
