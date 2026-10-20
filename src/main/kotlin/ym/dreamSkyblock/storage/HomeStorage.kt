package ym.dreamSkyblock.storage

import org.bukkit.NamespacedKey
import ym.dreamSkyblock.DreamSkyblock
import ym.dreamSkyblock.config.ConfigService
import ym.dreamSkyblock.model.Home
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

/** Owns the ordered database worker independently of world IO or server schedulers. */
class HomeStorage(val databasePath: Path) {
    @Suppress("UNUSED_PARAMETER")
    constructor(plugin: DreamSkyblock, config: ConfigService) : this(plugin.dataFolder.toPath().resolve("data/homes.db"))

    private val repository = SqliteHomeRepository(databasePath)
    private val databaseExecutor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "DreamSkyblock-homes-database").apply { isDaemon = false }
    }
    private val submissionLock = Any()
    private var closing = false
    private var closeFuture: CompletableFuture<Void>? = null

    /** Dedicated database worker; no Bukkit access and no live world file mutations. */
    fun recoverOwnership(files: ym.dreamSkyblock.world.NativeWorldFiles): CompletableFuture<List<String>> = submit {
        HomeOwnershipRecovery(databasePath.toAbsolutePath().parent.resolve("recovery/pending-worlds.properties")).recover(repository, files)
    }

    fun backupDatabase(reason: String): CompletableFuture<Path> = submit { repository.backupDatabase(reason) }

    @Suppress("UNUSED_PARAMETER")
    fun loadAll(executor: Executor): CompletableFuture<List<Home>> = submit { repository.loadAll().map(HomeSnapshot::toHome) }

    @Suppress("UNUSED_PARAMETER")
    fun loadIncludingDeleted(executor: Executor): CompletableFuture<List<Home>> = submit { repository.loadIncludingDeleted().map(HomeSnapshot::toHome) }

    /** Capture immediately; the worker must never inspect a mutable live Home. */
    @Suppress("UNUSED_PARAMETER")
    fun save(home: Home, executor: Executor): CompletableFuture<Void> = synchronized(home) {
        val captured = runCatching { snapshot(home) }.getOrElse { return@synchronized CompletableFuture.failedFuture(it) }
        submitVoid { repository.save(captured) }
    }

    @Suppress("UNUSED_PARAMETER")
    fun importLegacy(homes: List<Home>, executor: Executor): CompletableFuture<Void> {
        val captured = runCatching { homes.map(::snapshot) }.getOrElse { return CompletableFuture.failedFuture(it) }
        return submitVoid { repository.importLegacy(captured) }
    }

    @Suppress("UNUSED_PARAMETER")
    fun delete(home: Home, executor: Executor): CompletableFuture<Void> = submitVoid {
        repository.delete(home.id)
        synchronized(home) { home.status = "DELETED" }
    }

    @Suppress("UNUSED_PARAMETER")
    fun updateStatus(home: Home, status: String, executor: Executor): CompletableFuture<Void> = submitVoid {
        repository.updateStatus(home.id, status)
        synchronized(home) { home.status = status }
    }

    fun snapshot(home: Home): HomeSnapshot = HomeSnapshot.fromHome(home)

    @Suppress("UNUSED_PARAMETER")
    fun restoreSnapshot(snapshot: HomeSnapshot, executor: Executor): CompletableFuture<Home> =
        submit { repository.restoreSnapshot(snapshot).toHome() }

    fun worldName(home: Home): String = "dreamskyblock_${home.id.toString().replace("-", "")}"
    fun worldKey(home: Home): NamespacedKey = NamespacedKey("dreamskyblock", worldName(home))

    /** Enqueue closure after all accepted writes; no previously submitted work is cancelled. */
    fun closeAsync(): CompletableFuture<Void> = synchronized(submissionLock) {
        closeFuture?.let { return@synchronized it }
        closing = true
        val result = CompletableFuture<Void>()
        closeFuture = result
        try {
            databaseExecutor.execute {
                try {
                    repository.close()
                    result.complete(null)
                } catch (error: Throwable) {
                    result.completeExceptionally(error)
                } finally {
                    databaseExecutor.shutdown()
                }
            }
        } catch (error: RejectedExecutionException) {
            result.completeExceptionally(error)
        }
        result
    }

    private fun submitVoid(action: () -> Unit): CompletableFuture<Void> {
        val result = CompletableFuture<Void>()
        submit(action).whenComplete { _, error ->
            if (error == null) result.complete(null) else result.completeExceptionally(error)
        }
        return result
    }

    private fun <T> submit(action: () -> T): CompletableFuture<T> = synchronized(submissionLock) {
        if (closing) return@synchronized CompletableFuture.failedFuture(IllegalStateException("homes.db: 正在关闭，拒绝新的数据库操作"))
        val result = CompletableFuture<T>()
        try {
            databaseExecutor.execute {
                try {
                    result.complete(action())
                } catch (error: Throwable) {
                    result.completeExceptionally(error)
                }
            }
        } catch (error: RejectedExecutionException) {
            result.completeExceptionally(error)
        }
        result
    }
}
