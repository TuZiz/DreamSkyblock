package ym.dreamSkyblock.value

import ym.dreamSkyblock.DreamSkyblock
import ym.dreamSkyblock.api.HomeValueKey
import ym.dreamSkyblock.api.HomeValueResult
import ym.dreamSkyblock.service.HomeManager
import ym.dreamSkyblock.world.HomeOperationQueue
import ym.dreamSkyblock.world.PaperWorldTasks
import java.util.UUID
import java.util.concurrent.CompletableFuture

/** Any-thread API ingress; per-home FIFO, then Paper main-thread validation and transaction submission. */
class HomeValueService(plugin: DreamSkyblock, private val homes: HomeManager) {
    private val queue = HomeOperationQueue()
    private val tasks = PaperWorldTasks(plugin)

    fun set(homeId: UUID, key: HomeValueKey, value: Long, revision: Long): CompletableFuture<HomeValueResult> {
        val expected = homes.byId(homeId) ?: return CompletableFuture.failedFuture(IllegalArgumentException("空岛不存在: $homeId"))
        return queue.submit(homeId) { tasks.global { homes.changeValue(expected, key, value, revision) }.thenCompose { it } }
    }

    fun stop() { queue.close(); tasks.close() }
}
