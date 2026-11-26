package ym.dreamSkyblock.login

import org.bukkit.Location
import ym.dreamSkyblock.DreamSkyblock
import ym.dreamSkyblock.model.Home
import ym.dreamSkyblock.service.HomeManager
import ym.dreamSkyblock.world.HomeWorldManager
import ym.dreamSkyblock.world.PaperWorldTasks
import java.util.UUID
import java.util.concurrent.CompletableFuture

/** Main-thread home snapshots and world reservations shared by login and respawn. */
class HomeSpawnService(private val plugin: DreamSkyblock, private val homes: HomeManager,
                       private val worlds: HomeWorldManager) {
    class Ticket(val home: Home, val release: () -> Unit) {
        val ready = CompletableFuture<Location>()
    }
    private val tasks = PaperWorldTasks(plugin)
    private val tickets = java.util.concurrent.ConcurrentHashMap.newKeySet<Ticket>()
    @Volatile private var stopped = false

    fun prepare(playerId: UUID, loginWorldId: UUID? = null): CompletableFuture<Ticket?> = tasks.global {
        if (stopped || !homes.ready) return@global null
        val home = homes.byPlayer(playerId)?.takeIf { it.status == "ACTIVE" && !worlds.isBusy(it) } ?: return@global null
        // Login preserves the server-selected world. Unloaded-island recovery is handled
        // after join by HomeLoginReturn's persisted return intent, not by ownership alone.
        if (loginWorldId != null && worlds.getLoaded(home)?.uid != loginWorldId) return@global null
        val template = plugin.configService.templates[home.templateId] ?: return@global null
        val ticket = Ticket(home, worlds.holdSpawn(home))
        tickets.add(ticket)
        worlds.prepareSpawn(home, template).whenComplete { location, error ->
            tasks.global {
                if (error != null || stopped || homes.byPlayer(playerId) !== home || home.status != "ACTIVE") {
                    ticket.ready.completeExceptionally(error ?: IllegalStateException("出生期间家园归属或状态改变: $playerId"))
                    release(ticket)
                } else ticket.ready.complete(location)
            }
        }
        ticket
    }

    /** Main thread, including disconnect and disable. Idempotent per reservation. */
    fun release(ticket: Ticket) {
        if (!tickets.remove(ticket)) return
        tasks.global { ticket.release() }
    }

    fun valid(playerId: UUID, ticket: Ticket): Boolean = !stopped && tickets.contains(ticket) &&
        homes.byPlayer(playerId) === ticket.home && ticket.home.status == "ACTIVE" && !worlds.isBusy(ticket.home)

    fun stop() {
        stopped = true
        tickets.toList().forEach { ticket ->
            ticket.ready.completeExceptionally(IllegalStateException("插件停止，取消出生加载"))
            release(ticket)
        }
        tasks.close()
    }
}
