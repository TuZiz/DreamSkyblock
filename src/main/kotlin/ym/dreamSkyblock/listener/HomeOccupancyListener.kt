package ym.dreamSkyblock.listener

import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.event.world.WorldUnloadEvent
import ym.dreamSkyblock.config.ConfigService
import ym.dreamSkyblock.service.HomeManager
import ym.dreamSkyblock.world.HomeWorldManager

/** 按实际世界人数统计，包括访客、跨空岛传送及退出；不按在线成员推断占用。 */
class HomeOccupancyListener(private val homes: HomeManager, private val worlds: HomeWorldManager, private val config: ConfigService) : Listener {
    @EventHandler
    fun join(event: PlayerJoinEvent) { worlds.homeByWorld(event.player.world)?.let(homes::byId)?.let(worlds::cancelUnload) }

    @EventHandler(priority = EventPriority.MONITOR)
    fun quit(event: PlayerQuitEvent) {
        val home = worlds.homeByWorld(event.player.world)?.let(homes::byId) ?: return
        if (event.player.world.players.none { it.uniqueId != event.player.uniqueId }) worlds.scheduleUnload(home, config.unloadDelaySeconds)
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun teleport(event: PlayerTeleportEvent) {
        val destination = worlds.homeByWorld(event.to.world)?.let(homes::byId) ?: return
        if (worlds.isBusy(destination) || destination.status == "DELETED" || destination.status == "RECOVERY") event.isCancelled = true
        else worlds.cancelUnload(destination)
    }

    @EventHandler
    fun changed(event: PlayerChangedWorldEvent) {
        worlds.homeByWorld(event.from)?.let(homes::byId)?.let { home ->
            if (event.from.players.isEmpty()) worlds.scheduleUnload(home, config.unloadDelaySeconds)
        }
        worlds.homeByWorld(event.player.world)?.let(homes::byId)?.let(worlds::cancelUnload)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun unloaded(event: WorldUnloadEvent) { worlds.forgot(event.world) }
}
