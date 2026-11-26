package ym.dreamSkyblock.world

import org.bukkit.entity.Entity
import org.bukkit.World
import org.bukkit.plugin.java.JavaPlugin
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

/** Purpur/Leaf 的世界注册、设置、保存及卸载必须在 Paper 主线程执行。 */
class PaperWorldTasks(private val plugin: JavaPlugin) {
    private val pending = ConcurrentHashMap.newKeySet<CompletableFuture<*>>()
    @Volatile private var stopped = false

    fun <T> global(action: () -> T): CompletableFuture<T> = submit(action) { run, _ ->
        plugin.server.globalRegionScheduler.execute(plugin, run)
    }

    fun <T> chunk(world: World, x: Int, z: Int, action: () -> T): CompletableFuture<T> = submit(action) { run, _ ->
        plugin.server.regionScheduler.execute(plugin, world, x, z, run)
    }

    fun <T> entity(entity: Entity, onRetired: (() -> T)? = null, action: () -> T): CompletableFuture<T> = submit(action, onRetired) { run, retired ->
        if (!entity.scheduler.execute(plugin, run, retired, 1L)) retired.run()
    }

    private fun <T> submit(action: () -> T, onRetired: (() -> T)? = null, schedule: (Runnable, Runnable) -> Unit): CompletableFuture<T> {
        val result = CompletableFuture<T>()
        pending.add(result)
        result.whenComplete { _, _ -> pending.remove(result) }
        if (stopped || !plugin.isEnabled) {
            result.completeExceptionally(IllegalStateException("插件已经停止"))
            return result
        }
        try {
            schedule(Runnable {
                if (stopped || !plugin.isEnabled) result.completeExceptionally(IllegalStateException("插件已经停止"))
                else try { result.complete(action()) } catch (error: Throwable) { result.completeExceptionally(error) }
            }, Runnable {
                if (onRetired == null) result.completeExceptionally(IllegalStateException("实体已经离开调度器"))
                else result.complete(onRetired())
            })
        } catch (error: Throwable) { result.completeExceptionally(error) }
        return result
    }

    fun close() {
        stopped = true
        pending.toList().forEach { it.completeExceptionally(IllegalStateException("插件已经停止")) }
    }
}
