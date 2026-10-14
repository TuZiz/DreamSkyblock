package ym.dreamSkyblock.world

import java.util.UUID
import java.util.concurrent.CompletableFuture

/** 每个 home_id 的加载、卸载及维护共享一条串行队列。失败不会阻塞下一次重试。 */
class HomeOperationQueue {
    private val tails = mutableMapOf<UUID, CompletableFuture<*>>()
    private var closed = false

    @Synchronized
    fun <T> submit(id: UUID, operation: () -> CompletableFuture<T>): CompletableFuture<T> {
        if (closed) return CompletableFuture.failedFuture(IllegalStateException("世界操作队列已停止"))
        val result = CompletableFuture<T>()
        val previous = tails.put(id, result)
        val ready = previous?.handle { _, _ -> Unit } ?: CompletableFuture.completedFuture(Unit)
        ready.thenCompose {
            synchronized(this) {
                if (closed) CompletableFuture.failedFuture(IllegalStateException("世界操作队列已停止"))
                else try { operation() } catch (error: Throwable) { CompletableFuture.failedFuture(error) }
            }
        }.whenComplete { value, error ->
            if (error == null) result.complete(value) else result.completeExceptionally(error)
            synchronized(this) { tails.remove(id, result) }
        }
        return result
    }

    @Synchronized
    fun close() { closed = true }
}
