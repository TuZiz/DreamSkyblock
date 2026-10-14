package ym.dreamSkyblock.world

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID
import java.util.concurrent.CompletableFuture

class HomeOperationQueueTest {
    @Test
    fun `same home serializes failure and retry while other home proceeds`() {
        val queue = HomeOperationQueue()
        val id = UUID.randomUUID()
        val first = CompletableFuture<Int>()
        val order = mutableListOf<String>()
        val a = queue.submit(id) { order.add("load"); first }
        val b = queue.submit(id) { order.add("unload"); CompletableFuture.failedFuture<Int>(IllegalStateException("event cancelled")) }
        val c = queue.submit(id) { order.add("reload"); CompletableFuture.completedFuture(3) }
        assertEquals(7, queue.submit(UUID.randomUUID()) { CompletableFuture.completedFuture(7) }.get())
        assertEquals(listOf("load"), order)
        first.complete(1)
        assertEquals(1, a.get())
        assertTrue(b.isCompletedExceptionally)
        assertEquals(3, c.get())
        assertEquals(listOf("load", "unload", "reload"), order)
    }

    @Test
    fun `stopping rejects queued callbacks before they can mutate a world`() {
        val queue = HomeOperationQueue()
        val id = UUID.randomUUID()
        val first = CompletableFuture<Int>()
        queue.submit(id) { first }
        var called = false
        val queued = queue.submit(id) { called = true; CompletableFuture.completedFuture(2) }
        queue.close()
        first.complete(1)
        assertTrue(queued.isCompletedExceptionally)
        assertFalse(called)
        assertTrue(queue.submit(id) { CompletableFuture.completedFuture(4) }.isCompletedExceptionally)
    }
}
