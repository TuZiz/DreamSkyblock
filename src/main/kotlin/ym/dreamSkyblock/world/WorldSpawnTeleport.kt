package ym.dreamSkyblock.world

import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import ym.dreamSkyblock.text.MessageService
import java.util.logging.Level

/** Paper main thread only. Chunk loading is delegated to Paper's asynchronous teleport. */
class WorldSpawnTeleport(private val plugin: JavaPlugin, private val messages: MessageService) {
    fun teleport(player: Player) {
        if (!plugin.isEnabled || !player.isOnline || !player.hasPermission("dreamskyblock.use")) return
        val world = plugin.server.getWorld("world")
        if (world == null) {
            messages.send(player, "spawn.unavailable")
            return
        }
        val destination = world.spawnLocation
        player.closeInventory()
        try {
            player.teleportAsync(destination).whenComplete { success, error ->
                if (error == null && success == true) return@whenComplete
                if (!plugin.isEnabled) return@whenComplete
                if (error != null) plugin.logger.log(Level.WARNING,
                    "主城传送失败 world=world, player=${player.uniqueId}", error)
                player.scheduler.run(plugin, { _ ->
                    if (plugin.isEnabled && player.isOnline && player.hasPermission("dreamskyblock.use") &&
                        plugin.server.getPlayer(player.uniqueId) === player) messages.send(player, "spawn.failed")
                }, null)
            }
        } catch (error: Exception) {
            plugin.logger.log(Level.WARNING, "主城传送发起失败 world=world, player=${player.uniqueId}", error)
            messages.send(player, "spawn.failed")
        }
    }
}
