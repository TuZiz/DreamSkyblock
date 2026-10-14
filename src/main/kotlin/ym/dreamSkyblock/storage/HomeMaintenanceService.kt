package ym.dreamSkyblock.storage

import ym.dreamSkyblock.model.Home
import ym.dreamSkyblock.world.HomeWorldManager
import ym.dreamSkyblock.world.NativeWorldFiles
import ym.dreamSkyblock.world.WorldArchiveIO
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor

/** 世界备份含不可变SQLite元数据；持久操作记录弥合文件系统与数据库事务边界。 */
class HomeMaintenanceService(
    private val storage: HomeStorage,
    private val files: NativeWorldFiles,
    private val worlds: HomeWorldManager,
    private val pluginRoot: Path,
    private val io: Executor
) {
    fun backup(home: Home): CompletableFuture<String> = worlds.maintain(home) {
        val metadata = storage.snapshot(home).toYaml()
        CompletableFuture.supplyAsync({ files.backup(home.id, metadata) }, io)
    }

    fun restore(home: Home, backupId: String): CompletableFuture<Home> = worlds.maintain(home) {
        CompletableFuture.supplyAsync({ HomeSnapshot.fromYaml(files.readBackup(home.id, backupId)).withStatus("ACTIVE") }, io)
            .thenCompose { snapshot ->
                // 换盘前预检唯一成员；SQLite提交仍有唯一约束作为最终防线。
                storage.loadAll(io).thenApply { current ->
                    check(current.none { it.id != home.id && it.owners.any(snapshot.owners::contains) }) { "备份成员已加入其他空岛，拒绝恢复" }
                    snapshot
                }
            }.thenCompose { snapshot ->
                CompletableFuture.supplyAsync({ files.restore(home.id, backupId); snapshot }, io)
            }.thenCompose { snapshot -> storage.restoreSnapshot(snapshot, io) }
            .thenApplyAsync({ restored -> files.acknowledgeRestore(home.id); restored }, io)
    }

    fun delete(home: Home): CompletableFuture<Void> = worlds.maintain(home, evacuate = true) {
        val metadata = storage.snapshot(home).toYaml()
        CompletableFuture.runAsync({
            val marker = deleteMarker(home)
            Files.createDirectories(marker.parent)
            if (!Files.exists(marker)) {
                WorldArchiveIO().writeDurable(marker, metadata)
            }
            check(Files.readString(marker) == metadata) { "删除操作记录与家园快照不一致: $marker" }
            files.archiveDeleted(home.id, metadata)
        }, io).thenCompose { storage.delete(home, io) }.thenRunAsync({ Files.deleteIfExists(deleteMarker(home)) }, io)
    }

    fun recover(): CompletableFuture<Void> = CompletableFuture.supplyAsync({
        files.recover()
        files.pendingRestores()
    }, io).thenCompose { pending ->
        var chain = CompletableFuture.completedFuture<Void>(null)
        pending.forEach { (id, metadata) ->
            chain = chain.thenCompose {
                val snapshot = HomeSnapshot.fromYaml(metadata).withStatus("ACTIVE")
                check(snapshot.id == id) { "恢复journal归属错误: $id" }
                storage.restoreSnapshot(snapshot, io).thenAcceptAsync({ files.acknowledgeRestore(id) }, io)
            }
        }
        chain
    }.thenCompose {
        CompletableFuture.supplyAsync({
            val root = pluginRoot.resolve("data/operations")
            if (!Files.exists(root)) emptyList() else Files.list(root).use { entries ->
                entries.filter { it.fileName.toString().endsWith(".delete.yml") }.toList().map { marker ->
                    WorldArchiveIO().checked(marker)
                    val home = HomeSnapshot.fromYaml(Files.readString(marker)).toHome()
                    check(marker.fileName.toString() == "${home.id}.delete.yml") { "删除journal归属错误: $marker" }
                    marker to home
                }
            }
        }, io)
    }.thenCompose { pending ->
        var chain = CompletableFuture.completedFuture<Void>(null)
        pending.forEach { (marker, home) ->
            chain = chain.thenCompose {
                CompletableFuture.runAsync({ files.archiveDeleted(home.id, storage.snapshot(home).toYaml()) }, io)
                    .thenCompose { storage.delete(home, io) }.thenRunAsync({ Files.deleteIfExists(marker) }, io)
            }
        }
        chain
    }

    private fun deleteMarker(home: Home): Path = pluginRoot.resolve("data/operations/${home.id}.delete.yml")

    fun requiresRecovery(home: Home): CompletableFuture<Boolean> = CompletableFuture.supplyAsync({
        Files.exists(deleteMarker(home)) || files.hasPendingTransaction(home.id)
    }, io)
}
