package ym.dreamSkyblock.login

import io.papermc.paper.event.player.AsyncPlayerSpawnLocationEvent
import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import ym.dreamSkyblock.DreamSkyblock
import ym.dreamSkyblock.world.PaperWorldTasks
import java.util.concurrent.TimeUnit
import java.util.logging.Level

/** Paper configuration thread waits; all home/world access runs on the global owner thread. */
class HomeInitialSpawn(private val plugin: DreamSkyblock, private val spawns: HomeSpawnService) : Listener {
    private val tasks = PaperWorldTasks(plugin)
    private val pending = mutableMapOf<io.papermc.paper.connection.PlayerConfigurationConnection, HomeSpawnService.Ticket>()
    private var cleanup: ScheduledTask? = plugin.server.globalRegionScheduler.runAtFixedRate(plugin, {
        pending.keys.filter { !it.isConnected }.toList().forEach { connection -> pending.remove(connection)?.let(spawns::release) }
    }, 20L, 20L)

    @EventHandler(priority = EventPriority.HIGHEST)
    fun spawn(event: AsyncPlayerSpawnLocationEvent) {
        val connection = event.connection
        val id = connection.profile.id ?: return
        // Only this async configuration callback may wait. Never block a world/player scheduler.
        val request = spawns.prepare(id)
        val result = request.thenCompose { ticket ->
            if (ticket == null) java.util.concurrent.CompletableFuture.completedFuture(null)
            else tasks.global {
                if (!connection.isConnected) { spawns.release(ticket); null }
                else { pending.remove(connection)?.let(spawns::release); pending[connection] = ticket; ticket }
            }.thenCompose { retained -> retained?.ready ?: java.util.concurrent.CompletableFuture.completedFuture(null) }
        }
        try {
            val location = result.get(30, TimeUnit.SECONDS) ?: return
            val valid = tasks.global { pending[connection]?.let { spawns.valid(id, it) } == true && connection.isConnected }.get(5, TimeUnit.SECONDS)
            if (valid) event.spawnLocation = location
        } catch (error: Exception) {
            plugin.logger.log(Level.WARNING, "登录出生预加载失败 player=$id；保留服务器出生流程及原有回岛兜底", error)
            // The task may finish after timeout; remove its reservation when it eventually settles.
            result.whenComplete { _, _ -> tasks.global { pending.remove(connection)?.let(spawns::release) } }
            if (error is InterruptedException) Thread.currentThread().interrupt()
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun joined(event: PlayerJoinEvent) {
        pending.keys.filter { it.profile.id == event.player.uniqueId }.toList().forEach { connection ->
            pending.remove(connection)?.let(spawns::release)
        }
    }

    fun stop() {
        cleanup?.cancel(); cleanup = null
        pending.values.toList().forEach(spawns::release); pending.clear()
        tasks.close()
    }
}
