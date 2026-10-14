package ym.dreamSkyblock.world

import org.bukkit.plugin.java.JavaPlugin
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

/** Purpur/Leaf 的世界注册、设置、保存及卸载必须在 Paper 主线程执行。 */
class PaperWorldTasks(private val plugin: JavaPlugin) {
    private val pending = ConcurrentHashMap.newKeySet<CompletableFuture<*>>()
    @Volatile private var stopped = false

    fun <T> global(action: () -> T): CompletableFuture<T> {
        val result = CompletableFuture<T>()
        pending.add(result)
        result.whenComplete { _, _ -> pending.remove(result) }
        if (stopped || !plugin.isEnabled) {
            result.completeExceptionally(IllegalStateException("插件已经停止"))
            return result
        }
        try {
            plugin.server.globalRegionScheduler.execute(plugin) {
                if (stopped || !plugin.isEnabled) result.completeExceptionally(IllegalStateException("插件已经停止"))
                else try { result.complete(action()) } catch (error: Throwable) { result.completeExceptionally(error) }
            }
        } catch (error: Throwable) { result.completeExceptionally(error) }
        return result
    }

    fun close() {
        stopped = true
        pending.toList().forEach { it.completeExceptionally(IllegalStateException("插件已经停止")) }
    }
}
