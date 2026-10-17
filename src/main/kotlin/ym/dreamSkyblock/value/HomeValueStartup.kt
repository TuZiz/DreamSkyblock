package ym.dreamSkyblock.value

import ym.dreamSkyblock.model.Home
import ym.dreamSkyblock.service.HomeLevelService
import ym.dreamSkyblock.storage.HomeStorage
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor

/** Detached database results only; persist new derived levels before exposing homes to players. */
internal object HomeValueStartup {
    fun reconcile(homes: List<Home>, levels: HomeLevelService, storage: HomeStorage, io: Executor): CompletableFuture<List<Home>> {
        val saves = homes.filter { it.status == "ACTIVE" }.mapNotNull { home ->
            val before = listOf(home.level.toDouble(), home.borderSize, home.borderLevel.toDouble())
            levels.sanitize(home)
            if (before == listOf(home.level.toDouble(), home.borderSize, home.borderLevel.toDouble())) null else storage.save(home, io)
        }
        return CompletableFuture.allOf(*saves.toTypedArray()).thenApply { homes }
    }
}
