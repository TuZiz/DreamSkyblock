package ym.dreamSkyblock.world

import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import io.papermc.paper.math.Position
import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.World
import org.bukkit.WorldCreator
import org.bukkit.entity.Player
import ym.dreamSkyblock.DreamSkyblock
import ym.dreamSkyblock.config.ConfigService
import ym.dreamSkyblock.model.Home
import ym.dreamSkyblock.model.HomeTemplate
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.logging.Level

/** 世界生命周期共享 home_id 队列；磁盘操作在 IO，Paper API 在主线程。 */
class HomeWorldManager(
    private val plugin: DreamSkyblock,
    private val config: ConfigService,
    val files: NativeWorldFiles,
    private val ioExecutor: Executor
) {
    private val loaded = ConcurrentHashMap<UUID, World>()
    private val records = ConcurrentHashMap<UUID, Home>()
    private val pendingUnload = ConcurrentHashMap<UUID, ScheduledTask>()
    private val maintenance = ConcurrentHashMap.newKeySet<UUID>()
    private val operations = HomeOperationQueue()
    private val tasks = PaperWorldTasks(plugin)
    @Volatile private var stopped = false

    fun getLoaded(home: Home): World? = loaded[home.id]
    fun isManaged(home: Home): Boolean = records.containsKey(home.id)
    fun isBusy(home: Home): Boolean = maintenance.contains(home.id)
    fun isHomeWorld(world: World): Boolean = homeByWorld(world) != null
    fun homeByWorld(world: World): UUID? = loaded.entries.firstOrNull { it.value.uid == world.uid }?.key
    private fun key(home: Home) = NamespacedKey("dreamskyblock", files.worldName(home.id))

    fun create(home: Home, template: HomeTemplate, player: Player, done: (Boolean) -> Unit) = visit(home, template, player, true, done)
    fun load(home: Home, template: HomeTemplate, player: Player?, done: (Boolean) -> Unit) = visit(home, template, player, false, done)

    /** 主线程入口/门禁；加载可以继续完成，但失效登录会话不得触发迟到传送。 */
    fun loadForLogin(home: Home, template: HomeTemplate, player: Player, current: () -> Boolean, done: (Boolean) -> Unit) =
        visit(home, template, player, false, done, current)

    private fun visit(home: Home, template: HomeTemplate, player: Player?, fresh: Boolean, done: (Boolean) -> Unit, current: () -> Boolean = { true }) {
        if (stopped || isBusy(home) || home.status == "DELETED" || home.status == "RECOVERY") { done(false); return }
        cancelUnload(home)
        operations.submit(home.id) {
            if (isBusy(home) || home.status == "DELETED" || home.status == "RECOVERY") {
                return@submit CompletableFuture.failedFuture(IllegalStateException("家园不可加载: ${home.status}"))
            }
            open(home, template, fresh).thenCompose { world ->
                tasks.global {
                    if (!current()) CompletableFuture.completedFuture(false)
                    else if (player == null || !player.isOnline) CompletableFuture.completedFuture(true)
                    else {
                        check(home.isOwner(player.uniqueId) && player.hasPermission("dreamskyblock.use")) { "访问期间权限或家园归属已改变" }
                        val point = home.spawn ?: template.spawn
                        player.teleportAsync(Location(world, point.x, point.y, point.z, point.yaw, point.pitch))
                    }
                }.thenCompose { it }.thenApply { teleported -> fresh || teleported }
            }
        }.whenComplete { ok, error ->
            if (error != null) report("创建/加载", home, error)
            if (!stopped) tasks.global {
                done(error == null && ok == true)
                if (loaded[home.id]?.players?.isEmpty() == true) scheduleUnload(home, config.unloadDelaySeconds)
            }
        }
    }

    private fun open(home: Home, template: HomeTemplate, fresh: Boolean): CompletableFuture<World> {
        return CompletableFuture.supplyAsync({
            // 缺失存档禁止由 createWorld 意外生成空世界替代。
            if (fresh) check(!files.hasWorldData(home.id)) { "新家园ID已有存档，拒绝覆盖" }
            else check(files.hasWorldData(home.id)) { "家园存档缺失: ${files.worldDir(home.id)}" }
        }, ioExecutor).thenCompose {
            tasks.global {
                val existing = loaded[home.id] ?: plugin.server.getWorld(key(home))
                val world = if (existing != null) {
                    check(!fresh) { "新家园ID已加载，拒绝覆盖" }
                    track(home, existing)
                    HomeWorldSettings.apply(existing, home, template, false)
                    StarterTreeLeaves.repairLoaded(existing, template)
                    existing
                } else {
                    val point = home.spawn ?: template.spawn
                    val creator = WorldCreator.ofKey(key(home)).environment(template.environment)
                        .generateStructures(template.generator == HomeTemplate.GeneratorType.VANILLA)
                        .forcedSpawnPosition(Position.block(point.x.toInt(), point.y.toInt(), point.z.toInt()), point.yaw, point.pitch)
                    if (template.generator == HomeTemplate.GeneratorType.SKYBLOCK) creator.generator(VoidChunkGenerator())
                    val world = creator.createWorld() ?: error("Paper 世界创建/加载返回空值")
                    track(home, world)
                    check(world.key == key(home)) { "Paper 返回了错误的世界Key: ${world.key}" }
                    check(world.worldFolder.toPath().toAbsolutePath().normalize() == files.worldDir(home.id).toAbsolutePath().normalize()) {
                        "Paper 世界路径不符合原生存储目录: ${world.worldFolder}"
                    }
                    HomeWorldSettings.apply(world, home, template, fresh)
                    if (fresh && template.generator == HomeTemplate.GeneratorType.SKYBLOCK) IslandGenerator.generate(world, template)
                    StarterTreeLeaves.repairLoaded(world, template)
                    if (fresh) world.save(true)
                    world
                }
                world to world.uid
            }
        }.thenApplyAsync({ (world, uid) -> if (fresh) files.recordCreation(home.id, uid); world }, ioExecutor)
    }

    private fun track(home: Home, world: World) {
        loaded[home.id] = world
        records[home.id] = home
        home.loadedWorldName = world.name
    }

    /** 重载时接管Paper已经注册的维度，保护与人数统计继续生效。主线程。 */
    fun adopt(homes: Collection<Home>) {
        homes.forEach { home -> plugin.server.getWorld(key(home))?.let { world ->
            track(home, world)
            config.templates[home.templateId]?.let { StarterTreeLeaves.repairLoaded(world, it) }
            if (world.players.isEmpty()) scheduleUnload(home, config.unloadDelaySeconds)
        } }
    }

    /** ChunkLoadEvent/main thread; unloaded portions are repaired only when Paper loads them naturally. */
    fun repairStarterLeaves(chunk: org.bukkit.Chunk) {
        if (stopped) return
        val home = homeByWorld(chunk.world)?.let(records::get) ?: return
        config.templates[home.templateId]?.let { StarterTreeLeaves.repair(chunk, it) }
    }

    fun forgot(world: World) {
        if (stopped) return
        tasks.global {
            if (plugin.server.getWorld(world.key) != null) return@global
            val id = homeByWorld(world) ?: return@global
            loaded.remove(id, world)
            records.remove(id)?.let { home -> home.loadedWorldName = null; cancelUnload(home) }
        }
    }

    fun applyFlags(home: Home) {
        if (stopped || isBusy(home)) return
        operations.submit(home.id) { tasks.global { loaded[home.id]?.let { HomeWorldSettings.flags(it, home) } } }
            .exceptionally { report("应用设置/边界", home, it); null }
    }

    fun scheduleUnload(home: Home, delaySeconds: Long) {
        if (stopped) return
        tasks.global {
            if (loaded[home.id] == null || pendingUnload.containsKey(home.id) || isBusy(home)) return@global
            pendingUnload[home.id] = plugin.server.globalRegionScheduler.runDelayed(plugin, {
                pendingUnload.remove(home.id)
                operations.submit(home.id) { tasks.global { unloadEmpty(home) } }.whenComplete { ok, error ->
                    if (error != null) report("无人卸载", home, error)
                    if (!stopped && (error != null || ok == false)) tasks.global {
                        if (loaded[home.id]?.players?.isEmpty() == true) scheduleUnload(home, config.unloadDelaySeconds)
                    }
                }
            }, delaySeconds.coerceIn(1, 86_400) * 20L)
        }
    }

    fun cancelUnload(home: Home) { pendingUnload.remove(home.id)?.cancel() }

    /** 主线程；false 时仍保留加载记录及原生存档供重试。 */
    private fun unloadEmpty(home: Home): Boolean {
        val world = loaded[home.id] ?: plugin.server.getWorld(key(home)) ?: return true
        if (world.players.isNotEmpty()) return false
        // Paper 的 save=true 已负责保存并关闭存档；不要在它之前再完整 flush 一次。
        if (!plugin.server.unloadWorld(world, true)) return false
        loaded.remove(home.id, world)
        records.remove(home.id)
        home.loadedWorldName = null
        return true
    }

    /** 维护期间禁止进入和修改数据；先安全卸载，再执行磁盘事务。 */
    fun <T> maintain(home: Home, evacuate: Boolean = false, action: () -> CompletableFuture<T>): CompletableFuture<T> {
        if (stopped || !maintenance.add(home.id)) return CompletableFuture.failedFuture(IllegalStateException("家园正在维护"))
        cancelUnload(home)
        return operations.submit(home.id) {
            tasks.global {
                val world = loaded[home.id] ?: plugin.server.getWorld(key(home))
                if (world != null && world.players.isNotEmpty()) {
                    check(evacuate && world.players.size == 1) { "家园内仍有玩家" }
                    val destination = plugin.server.worlds.firstOrNull { !isHomeWorld(it) && it.uid != world.uid } ?: error("找不到安全离开世界")
                    world.players.first().teleportAsync(destination.spawnLocation)
                } else CompletableFuture.completedFuture(true)
            }.thenCompose { it }.thenCompose { moved ->
                tasks.global { check(moved && unloadEmpty(home)) { "家园保存/卸载未成功" } }
            }.thenCompose { action() }
        }.whenComplete { _, error ->
            maintenance.remove(home.id)
            if (error != null) report("维护", home, error)
        }
    }

    /** onDisable 主线程，不等待IO、不复制存档；Paper保存直接写入永久目录。 */
    fun shutdown() {
        stopped = true
        operations.close()
        tasks.close()
        pendingUnload.values.forEach { it.cancel() }
        pendingUnload.clear()
        loaded.values.toList().forEach { world ->
            try {
                if (world.players.isNotEmpty()) world.save(true)
                else if (!plugin.server.unloadWorld(world, true)) {
                    // 其他插件取消卸载时世界仍然注册，必须补做一次停用保存。
                    world.save(true)
                    plugin.logger.warning("停止时无法卸载世界 ${world.key}；已保存并保留永久存档由服务器关闭流程继续保存")
                }
            } catch (error: Throwable) { plugin.logger.log(Level.SEVERE, "停止保存世界失败 ${world.key}，保留永久存档", error) }
        }
        loaded.clear()
        records.clear()
    }

    private fun report(operation: String, home: Home, error: Throwable) = plugin.logger.log(Level.SEVERE,
        "家园$operation 失败 home_id=${home.id}, key=${key(home)}, path=${files.worldDir(home.id)}", error)
}
