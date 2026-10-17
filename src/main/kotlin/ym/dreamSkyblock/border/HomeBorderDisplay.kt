package ym.dreamSkyblock.border

import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import org.bukkit.Color
import org.bukkit.Particle
import org.bukkit.WorldBorder
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import ym.dreamSkyblock.DreamSkyblock
import ym.dreamSkyblock.service.HomeManager
import ym.dreamSkyblock.world.HomeWorldManager
import java.util.UUID

/** Paper main thread only. The real world border still enforces collisions/build limits. */
class HomeBorderDisplay(private val plugin: DreamSkyblock, private val homes: HomeManager,
                        private val worlds: HomeWorldManager) : Listener {
    private data class View(val worldId: UUID, val hidden: WorldBorder, val previous: WorldBorder?)
    private val views = mutableMapOf<UUID, View>()
    private val dust = Particle.DustOptions(Color.fromRGB(116, 185, 255), 1.0f)
    private var task: ScheduledTask? = null

    fun start() {
        if (task != null) return
        task = plugin.server.globalRegionScheduler.runAtFixedRate(plugin, {
            if (plugin.isEnabled) plugin.server.onlinePlayers.forEach(::update)
        }, 1L, 10L)
        plugin.server.onlinePlayers.forEach(::update)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun join(event: PlayerJoinEvent) { if (task != null) update(event.player) }

    @EventHandler(priority = EventPriority.MONITOR)
    fun changed(event: PlayerChangedWorldEvent) { if (task != null) update(event.player) }

    @EventHandler
    fun quit(event: PlayerQuitEvent) { views.remove(event.player.uniqueId) }

    /** Also used after entering an island; includes visitors regardless of ownership or use permission. */
    fun update(player: Player) {
        if (task == null || !plugin.isEnabled || !player.isOnline) return
        val home = if (homes.ready) worlds.homeByWorld(player.world)?.let(homes::byId) else null
        val old = views[player.uniqueId]
        if (old != null && (home == null || old.worldId != player.world.uid)) restore(player)
        if (home == null) return
        val view = views.getOrPut(player.uniqueId) {
            val hidden = plugin.server.createWorldBorder().apply {
                size = maxSize
                setCenter(home.center.x, home.center.z)
                warningDistance = 0
                warningTime = 0
            }
            View(player.world.uid, hidden, player.worldBorder).also { player.worldBorder = hidden }
        }
        // Respawn/world changes may clear a client override; don't replace a border owned by another plugin.
        if (player.worldBorder == null) player.worldBorder = view.hidden
        val location = player.location
        HomeBorderOutline.points(home.center.x, home.center.z, home.borderSize, location.x, location.y, location.z).forEach {
            player.spawnParticle(Particle.DUST, it.x, it.y, it.z, 1, dust)
        }
    }

    private fun restore(player: Player) {
        val view = views.remove(player.uniqueId) ?: return
        if (player.worldBorder == null || player.worldBorder === view.hidden) {
            player.worldBorder = view.previous?.takeIf { it.world == null || it.world == player.world }
        }
    }

    fun stop() {
        task?.cancel()
        task = null
        plugin.server.onlinePlayers.forEach(::restore)
        views.clear()
    }
}
