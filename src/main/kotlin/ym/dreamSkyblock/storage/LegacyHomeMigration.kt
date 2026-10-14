package ym.dreamSkyblock.storage

import org.bukkit.NamespacedKey
import org.bukkit.WorldCreator
import ym.dreamSkyblock.DreamSkyblock
import ym.dreamSkyblock.config.ConfigService
import ym.dreamSkyblock.model.Home
import ym.dreamSkyblock.model.HomeTemplate
import ym.dreamSkyblock.world.NativeWorldFiles
import ym.dreamSkyblock.world.PaperWorldTasks
import ym.dreamSkyblock.world.VoidChunkGenerator
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor

/** 先保留原始YAML/世界备份，验证原生世界后才提交SQLite；任意失败均可重试。 */
class LegacyHomeMigration(
    private val plugin: DreamSkyblock,
    private val config: ConfigService,
    private val storage: HomeStorage,
    private val files: NativeWorldFiles,
    private val io: Executor
) {
    private val tasks = PaperWorldTasks(plugin)
    private data class Legacy(val home: Home, val directory: Path)

    fun run(): CompletableFuture<List<Home>> = storage.loadIncludingDeleted(io).thenCompose { existing ->
        CompletableFuture.supplyAsync({ scan(existing.map { it.id }.toSet()) }, io).thenCompose { legacy ->
            val known = existing.associateBy { it.id }
            var chain = CompletableFuture.completedFuture(Unit)
            legacy.filter { it.home.id !in known }.forEach { entry ->
                chain = chain.thenCompose {
                    prepare(entry.home, entry.directory).thenCompose { valid ->
                        check(valid) { "旧家园世界缺失，禁止创建空世界代替: ${entry.directory}" }
                        storage.importLegacy(listOf(entry.home), io).thenApply { Unit }
                    }
                }
            }
            chain.thenCompose {
                var verify = CompletableFuture.completedFuture(Unit)
                existing.filter { it.status != "DELETED" }.forEach { home ->
                    verify = verify.thenCompose {
                        if (home.status == "CREATING") {
                            CompletableFuture.supplyAsync({ runCatching { files.creationCompleted(home.id) }.getOrElse { error ->
                                plugin.logger.log(java.util.logging.Level.SEVERE, "家园创建凭据校验失败 home_id=${home.id}；保留存档", error)
                                false
                            } }, io).thenCompose { complete ->
                                plugin.logger.info("恢复中断的家园创建 home_id=${home.id}, 已完成生成及保存=$complete；保留世界")
                                storage.updateStatus(home, if (complete) "ACTIVE" else "RECOVERY", io).thenApply { Unit }
                            }
                        } else CompletableFuture.supplyAsync({
                            runCatching { files.hasWorldData(home.id) }.getOrElse { error ->
                                plugin.logger.log(java.util.logging.Level.SEVERE, "家园存档校验失败 home_id=${home.id}；保留目录及归属，进入RECOVERY", error)
                                false
                            }
                        }, io).thenCompose { exists ->
                            CompletableFuture.completedFuture(exists)
                        }.thenCompose { valid ->
                            if (valid) CompletableFuture.completedFuture(Unit)
                            else {
                                plugin.logger.severe("家园存档缺失 home_id=${home.id}, path=${files.worldDir(home.id)}；保留归属并进入 RECOVERY")
                                storage.updateStatus(home, "RECOVERY", io).thenApply { Unit }
                            }
                        }
                    }
                }
                verify.thenCompose { storage.loadAll(io) }
            }
        }
    }

    private fun scan(knownIds: Set<UUID>): List<Legacy> {
        val roots = setOf(plugin.dataFolder.toPath().resolve(config.dataFolderName), plugin.dataFolder.toPath().resolve("playerdata"))
        val entries = roots.flatMap { root ->
            if (!Files.exists(root)) emptyList()
            else Files.list(root).use { dirs ->
                dirs.filter { Files.isDirectory(it, LinkOption.NOFOLLOW_LINKS) && Files.isRegularFile(it.resolve("home.yml")) }.toList()
            }
        }.distinct().mapNotNull { directory ->
            // 成功导入的旧YAML仅作历史备份，不再成为重启时的数据权威或复活删除记录。
            val directoryId = runCatching { UUID.fromString(directory.fileName.toString().takeLast(36)) }.getOrNull()
            if (directoryId in knownIds) return@mapNotNull null
            require(!Files.isSymbolicLink(directory) && !Files.readAttributes(directory, java.nio.file.attribute.BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS).isOther) {
                "拒绝迁移链接家园目录: $directory"
            }
            val source = directory.resolve("home.yml")
            val text = Files.readString(source)
            val home = try { HomeSnapshot.fromYaml(text).toHome() } catch (error: Throwable) {
                throw IllegalStateException("玩家YAML迁移失败 file=$source: ${error.message}", error)
            }
            if (home.id in knownIds) return@mapNotNull null
            val template = config.templates[home.templateId] ?: error("$source: template=${home.templateId} 不存在")
            home.spawn = home.spawn ?: template.spawn
            home.borderSize = home.borderSize.coerceIn(16.0, 384.0)
            val backup = plugin.dataFolder.toPath().resolve("backups/migration/${home.id}/home.yml")
            Files.createDirectories(backup.parent)
            if (!Files.exists(backup)) Files.copy(source, backup)
            check(Files.readString(backup) == text) { "迁移备份与当前YAML不一致，保留两份数据并停止: $source, $backup" }
            Legacy(home, directory)
        }
        check(entries.map { it.home.id }.distinct().size == entries.size) { "多个旧目录使用相同home_id，拒绝任意选择来源" }
        val owners = mutableSetOf<UUID>()
        entries.forEach { entry -> entry.home.owners.forEach { check(owners.add(it)) { "旧YAML玩家存在重复归属: player=$it, home_id=${entry.home.id}" } } }
        return entries
    }

    private fun prepare(home: Home, directory: Path?): CompletableFuture<Boolean> = CompletableFuture.supplyAsync({
        files.prepareLegacyImport(home.id, directory, storage.snapshot(home).toYaml())
    }, io).thenCompose { needsImport ->
        if (!needsImport) CompletableFuture.completedFuture(Unit)
        else tasks.global {
            val template = config.templates[home.templateId] ?: error("旧家园模板不存在: ${home.templateId}")
            val creator = WorldCreator.ofKey(NamespacedKey("dreamskyblock", files.worldName(home.id))).environment(template.environment)
            if (template.generator == HomeTemplate.GeneratorType.SKYBLOCK) creator.generator(VoidChunkGenerator())
            val world = creator.createWorld() ?: error("Paper 旧level.dat导入失败 home_id=${home.id}")
            world.save(true)
            check(plugin.server.unloadWorld(world, true)) { "Paper 导入后无法安全卸载 home_id=${home.id}" }
        }
    }.thenApplyAsync({ files.migrate(home.id, directory, storage.snapshot(home).toYaml()); files.hasWorldData(home.id) }, io)

    fun close() = tasks.close()
}
