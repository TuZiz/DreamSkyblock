package ym.dreamSkyblock.world

import ym.dreamSkyblock.DreamSkyblock
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException
import java.util.logging.Level

/** 清理 WorldGuard 为 DreamSkyblock 临时世界生成的单世界配置。 */
class WorldGuardConfigCleaner(
    private val plugin: DreamSkyblock,
    private val worldsDirectory: Path?,
    private val ioExecutor: Executor
) {
    private val generatedWorldName = Regex("dreamskyblock_[0-9a-f]{32}", RegexOption.IGNORE_CASE)

    fun cleanupWorldAsync(worldName: String) {
        if (!generatedWorldName.matches(worldName)) return
        submit { deleteWorldConfig(worldName) }
    }

    fun cleanupStaleAsync(activeWorldNames: Set<String>) {
        submit {
            val directory = worldsDirectory
            if (directory != null) {
                runCatching {
                    if (!Files.isDirectory(directory)) return@runCatching
                    Files.list(directory).use { entries ->
                        entries.filter { Files.isDirectory(it) }
                            .filter { generatedWorldName.matches(it.fileName.toString()) }
                            .filter { it.fileName.toString() !in activeWorldNames }
                            .forEach { deleteDirectory(it) }
                    }
                }.onFailure {
                    plugin.logger.log(Level.WARNING, "清理 WorldGuard 家园世界配置失败: ${it.message}", it)
                }
            }
        }
    }

    private fun submit(task: () -> Unit) {
        try {
            ioExecutor.execute(task)
        } catch (_: RejectedExecutionException) {
            // onDisable 可能已关闭插件自己的 IO 执行器，清理仍需完成但不能重新注册插件任务。
            CompletableFuture.runAsync(task)
        }
    }

    private fun deleteWorldConfig(worldName: String) {
        val directory = worldsDirectory?.resolve(worldName)?.normalize() ?: return
        if (directory.parent != worldsDirectory?.normalize()) return
        runCatching {
            if (Files.isDirectory(directory)) deleteDirectory(directory)
        }.onFailure {
            plugin.logger.log(Level.WARNING, "删除 WorldGuard 家园世界配置失败 $worldName: ${it.message}", it)
        }
    }

    private fun deleteDirectory(directory: Path) {
        Files.walk(directory).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
    }
}
