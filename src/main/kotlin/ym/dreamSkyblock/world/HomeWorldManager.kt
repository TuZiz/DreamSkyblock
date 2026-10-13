package ym.dreamSkyblock.world

import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import org.bukkit.*
import org.bukkit.block.data.type.Leaves
import org.bukkit.entity.Player
import ym.dreamSkyblock.DreamSkyblock
import ym.dreamSkyblock.config.ConfigService
import ym.dreamSkyblock.model.Home
import ym.dreamSkyblock.model.HomeTemplate
import ym.dreamSkyblock.storage.HomeStorage
import java.util.UUID
import java.util.logging.Level
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit

class HomeWorldManager(
    private val plugin: DreamSkyblock,
    private val config: ConfigService,
    private val storage: HomeStorage,
    private val ioExecutor: Executor,
    private val worldGuardConfigCleaner: WorldGuardConfigCleaner
) {
    private val loaded = ConcurrentHashMap<UUID, World>()
    private val records = ConcurrentHashMap<UUID, Home>()
    private val pendingUnload = ConcurrentHashMap<UUID, ScheduledTask>()
    private val worldOperations = ConcurrentHashMap.newKeySet<UUID>()
    @Volatile
    private var shuttingDown = false
    private val unloadAdapter = WorldUnloadAdapter(plugin)

    init {
        plugin.logger.info("家园世界卸载模式: ${unloadAdapter.mode}")
    }

    fun getLoaded(home: Home): World? = loaded[home.id]
    fun isManaged(home: Home): Boolean = records.containsKey(home.id)
    fun isHomeWorld(world: World): Boolean = loaded.values.any { it.uid == world.uid }
    fun homeByWorld(world: World): UUID? = loaded.entries.firstOrNull { it.value.uid == world.uid }?.key

    fun create(home: Home, template: HomeTemplate, player: Player, done: (Boolean) -> Unit) {
        if (shuttingDown || !plugin.isEnabled) return
        if (!worldOperations.add(home.id)) { done(false); return }
        cancelUnload(home)
        records[home.id] = home
        plugin.server.globalRegionScheduler.execute(plugin) {
            val name = storage.worldName(home)
            val existing = plugin.server.getWorld(name)
            if (existing != null) {
                applyLoadedWorld(home, template, player, existing)
                complete(home, done, true)
                return@execute
            }
            plugin.server.asyncScheduler.runNow(plugin) {
                try {
                    storage.prepareWorldForCreation(home, plugin.server.worldContainer.toPath())
                    plugin.server.globalRegionScheduler.execute(plugin) {
                        try {
                            val world = WorldCreator(storage.worldKey(home)).environment(template.environment)
                                .generateStructures(template.generator == HomeTemplate.GeneratorType.VANILLA)
                                .apply { if (template.generator == HomeTemplate.GeneratorType.SKYBLOCK) generator(VoidChunkGenerator()) }
                                .createWorld() ?: error("世界创建失败")
                            configureWorld(world, home, template, applyTemplateDefaults = true)
                            if (template.generator == HomeTemplate.GeneratorType.SKYBLOCK) {
                                generateIsland(world, template) { generated ->
                                    if (generated) {
                                        snapshotAndApply(home, template, player, world, done)
                                    } else {
                                        unloadWorldAsync(world, true) { complete(home, done, false) }
                                    }
                                }
                            } else {
                                snapshotAndApply(home, template, player, world, done)
                            }
                        } catch (ex: Throwable) {
                            plugin.logger.log(Level.SEVERE, "创建家园世界失败 ${home.id}: ${ex.message}", ex)
                            complete(home, done, false)
                        }
                    }
                } catch (ex: Throwable) {
                    plugin.logger.severe("准备家园世界目录失败 ${home.id}: ${ex.message}")
                    complete(home, done, false)
                }
            }
        }
    }

    fun load(home: Home, template: HomeTemplate, player: Player?, done: (Boolean) -> Unit) {
        if (shuttingDown || !plugin.isEnabled) return
        if (!worldOperations.add(home.id)) { done(false); return }
        cancelUnload(home)
        records[home.id] = home
        plugin.server.globalRegionScheduler.execute(plugin) {
            val name = storage.worldName(home)
            val existing = plugin.server.getWorld(name)
            if (existing != null) {
                applyLoadedWorld(home, template, player, existing)
                complete(home, done, true)
                return@execute
            }
            plugin.server.asyncScheduler.runNow(plugin) {
                try {
                    storage.prepareWorldDirectory(home, plugin.server.worldContainer.toPath())
                    plugin.server.globalRegionScheduler.execute(plugin) {
                        try {
                            val world = WorldCreator(storage.worldKey(home)).environment(template.environment)
                                .generateStructures(template.generator == HomeTemplate.GeneratorType.VANILLA)
                                .apply { if (template.generator == HomeTemplate.GeneratorType.SKYBLOCK) generator(VoidChunkGenerator()) }
                                .createWorld() ?: error("世界加载失败")
                            applyLoadedWorld(home, template, player, world)
                            complete(home, done, true)
                        } catch (ex: Throwable) {
                            plugin.logger.log(Level.SEVERE, "加载家园世界失败 ${home.id}: ${ex.message}", ex)
                            complete(home, done, false)
                        }
                    }
                } catch (ex: Throwable) {
                    plugin.logger.severe("准备家园世界目录失败 ${home.id}: ${ex.message}")
                    complete(home, done, false)
                }
            }
        }
    }

    private fun applyLoadedWorld(home: Home, template: HomeTemplate, player: Player?, world: World) {
        configureWorld(world, home, template)
        loaded[home.id] = world
        home.loadedWorldName = world.name
        if (player != null) player.getScheduler().execute(plugin, { if (player.isOnline) player.teleportAsync(spawn(world, template)) }, null, 0L)
    }

    private fun complete(home: Home, done: (Boolean) -> Unit, success: Boolean) {
        worldOperations.remove(home.id)
        if (!success) records.remove(home.id)
        done(success)
    }

    fun applyFlags(home: Home) {
        if (shuttingDown || !plugin.isEnabled) return
        val world = loaded[home.id] ?: return
        plugin.server.globalRegionScheduler.execute(plugin) {
            if (loaded[home.id]?.uid != world.uid) return@execute
            world.setPVP(home.flags[ym.dreamSkyblock.model.HomeFlag.PVP] ?: ym.dreamSkyblock.model.HomeFlag.PVP.defaultValue)
            world.setSpawnFlags(
                home.flags[ym.dreamSkyblock.model.HomeFlag.ANIMAL_SPAWN] ?: ym.dreamSkyblock.model.HomeFlag.ANIMAL_SPAWN.defaultValue,
                home.flags[ym.dreamSkyblock.model.HomeFlag.MONSTER_SPAWN] ?: ym.dreamSkyblock.model.HomeFlag.MONSTER_SPAWN.defaultValue
            )
            world.setGameRule(GameRule.DO_WEATHER_CYCLE, home.flags[ym.dreamSkyblock.model.HomeFlag.WEATHER] ?: ym.dreamSkyblock.model.HomeFlag.WEATHER.defaultValue)
            world.players.forEach { player ->
                val canFly = home.flags[ym.dreamSkyblock.model.HomeFlag.FLIGHT] ?: ym.dreamSkyblock.model.HomeFlag.FLIGHT.defaultValue
                player.getScheduler().execute(plugin, { if (player.isOnline) { player.allowFlight = canFly; if (!canFly) player.isFlying = false } }, null, 0L)
            }
        }
    }

    fun scheduleUnload(home: Home, delaySeconds: Long) {
        if (shuttingDown || !plugin.isEnabled) return
        if (loaded[home.id] == null || pendingUnload.containsKey(home.id)) return
        pendingUnload[home.id] = plugin.server.asyncScheduler.runDelayed(plugin, {
            if (shuttingDown || !plugin.isEnabled) {
                pendingUnload.remove(home.id)
                return@runDelayed
            }
            runCatching {
                plugin.server.globalRegionScheduler.execute(plugin) {
                    val world = loaded[home.id] ?: return@execute
                    if (world.players.isNotEmpty()) { pendingUnload.remove(home.id); return@execute }
                    unloadWorldAsync(world, true) { success ->
                        if (success) {
                            loaded.remove(home.id)
                            home.loadedWorldName = null
                            worldGuardConfigCleaner.cleanupWorldAsync(world.name)
                            if (!shuttingDown && plugin.isEnabled) {
                                plugin.server.asyncScheduler.runNow(plugin) { storage.syncWorldDirectory(home, plugin.server.worldContainer.toPath()) }
                            }
                        }
                        pendingUnload.remove(home.id)
                    }
                }
            }.onFailure {
                pendingUnload.remove(home.id)
            }
        }, delaySeconds, TimeUnit.SECONDS)
    }

    fun cancelUnload(home: Home) { pendingUnload.remove(home.id)?.cancel() }

    fun discardFailedCreation(home: Home) {
        ioExecutor.execute {
            runCatching {
                storage.removeWorldLink(home, plugin.server.worldContainer.toPath())
                storage.deleteDirectory(storage.homeDir(home))
            }.onFailure { plugin.logger.warning("清理失败的家园 ${home.id} 失败: ${it.message}") }
        }
    }

    fun delete(home: Home, done: (Boolean) -> Unit) {
        if (shuttingDown || !plugin.isEnabled) return
        cancelUnload(home)
        plugin.server.globalRegionScheduler.execute(plugin) {
            val world = loaded[home.id]
            if (world?.players?.size ?: 0 > 1) { done(false); return@execute }
            if (world?.players?.size == 1) {
                val player = world.players.first()
                val destination = plugin.server.worlds.firstOrNull { it.uid != world.uid }
                if (destination == null) { done(false); return@execute }
                player.teleportAsync(destination.spawnLocation).thenAccept {
                    plugin.server.globalRegionScheduler.execute(plugin) { deleteUnoccupied(home, world, done) }
                }
                return@execute
            }
            deleteUnoccupied(home, world, done)
        }
    }

    private fun deleteUnoccupied(home: Home, world: World?, done: (Boolean) -> Unit) {
        if (world != null) {
            unloadWorldAsync(world, true) { success ->
                if (success) deleteStorage(home, done) else done(false)
            }
            return
        }
        deleteStorage(home, done)
    }

    private fun deleteStorage(home: Home, done: (Boolean) -> Unit) {
        loaded.remove(home.id)
        records.remove(home.id)
        ioExecutor.execute {
            runCatching {
                storage.removeWorldLink(home, plugin.server.worldContainer.toPath())
                storage.deleteDirectory(storage.homeDir(home))
            }.onSuccess { done(true) }.onFailure { done(false) }
        }
    }

    fun shutdown() {
        shuttingDown = true
        pendingUnload.values.forEach { it.cancel() }
        pendingUnload.clear()
        val worlds = loaded.values.toList()
        worlds.forEach { world ->
            if (world.players.isNotEmpty()) {
                plugin.logger.warning("插件关闭时家园世界仍有玩家，跳过卸载: ${world.name}")
                return@forEach
            }

            val home = loaded.entries.firstOrNull { it.value.uid == world.uid }?.key?.let(records::get)
            val worldName = world.name
            val worldContainer = plugin.server.worldContainer.toPath()
            unloadWorldDuringShutdown(world) { success ->
                if (!success) return@unloadWorldDuringShutdown
                worldGuardConfigCleaner.cleanupWorldAsync(worldName)
                if (home == null) loaded.entries.removeIf { it.value.uid == world.uid }
                else {
                    loaded.remove(home.id)
                    try {
                        ioExecutor.execute {
                            runCatching { storage.syncWorldDirectory(home, worldContainer) }
                                .onFailure { plugin.logger.log(Level.WARNING, "关闭时保存家园世界副本失败 ${home.id}: ${it.message}", it) }
                        }
                    } catch (error: RejectedExecutionException) {
                        plugin.logger.log(Level.WARNING, "关闭后家园世界卸载才完成 ${home.id}: IO 执行器已停止，保留运行目录供下次加载恢复", error)
                    }
                }
            }
        }
    }

    private fun unloadWorldDuringShutdown(world: World, done: (Boolean) -> Unit) =
        unloadAdapter.unloadDuringShutdown(world, done)

    private fun unloadWorldAsync(world: World, save: Boolean, done: (Boolean) -> Unit) {
        if (shuttingDown || !plugin.isEnabled) return
        plugin.server.globalRegionScheduler.execute(plugin) {
            if (shuttingDown || !plugin.isEnabled) return@execute
            unloadAdapter.unload(world, save) { success ->
                if (!shuttingDown && plugin.isEnabled) done(success)
            }
        }
    }

    private fun snapshotAndApply(home: Home, template: HomeTemplate, player: Player, world: World, done: (Boolean) -> Unit) {
        ioExecutor.execute {
            runCatching { storage.snapshotWorldDirectory(home, plugin.server.worldContainer.toPath()) }
                .onFailure { plugin.logger.warning("保存新家园世界副本失败 ${home.id}: ${it.message}") }
            if (shuttingDown || !plugin.isEnabled) {
                complete(home, done, false)
            } else {
                runCatching {
                    plugin.server.globalRegionScheduler.execute(plugin) {
                        applyLoadedWorld(home, template, player, world)
                        complete(home, done, true)
                    }
                }.onFailure {
                    complete(home, done, false)
                }
            }
        }
    }

    private fun configureWorld(world: World, home: Home, template: HomeTemplate, applyTemplateDefaults: Boolean = false) {
        world.difficulty = template.difficulty
        world.setSpawnFlags(
            home.flags[ym.dreamSkyblock.model.HomeFlag.ANIMAL_SPAWN] ?: template.animals,
            home.flags[ym.dreamSkyblock.model.HomeFlag.MONSTER_SPAWN] ?: template.monsters
        )
        world.setPVP(home.flags[ym.dreamSkyblock.model.HomeFlag.PVP] ?: template.pvp)
        if (applyTemplateDefaults) {
            world.setGameRule(GameRule.KEEP_INVENTORY, template.keepInventory)
        }
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, home.flags[ym.dreamSkyblock.model.HomeFlag.WEATHER] ?: template.weather)
        world.setGameRule(GameRule.NATURAL_REGENERATION, template.naturalRegeneration)
        world.worldBorder.setCenter(template.center.x, template.center.z)
        world.worldBorder.size = home.borderSize
        world.setSpawnLocation(template.spawn.x.toInt(), template.spawn.y.toInt(), template.spawn.z.toInt(), template.spawn.yaw)
    }

    private fun spawn(world: World, template: HomeTemplate) = Location(world, template.spawn.x, template.spawn.y, template.spawn.z, template.spawn.yaw, template.spawn.pitch)

    private fun generateIsland(world: World, template: HomeTemplate, done: (Boolean) -> Unit) {
        val x = template.center.x.toInt(); val y = template.platformY; val z = template.center.z.toInt(); val half = template.platformSize / 2
        plugin.server.regionScheduler.execute(plugin, world, x shr 4, z shr 4) {
            try {
                for (dx in -half..half) for (dz in -half..half) {
                    world.getBlockAt(x + dx, y - 1, z + dz).type = Material.DIRT
                    world.getBlockAt(x + dx, y, z + dz).type = Material.GRASS_BLOCK
                }
                world.getBlockAt(x, y - 2, z).type = Material.BEDROCK
                placeOakTree(world, x + 2, y + 1, z + 2)
                done(true)
            } catch (ex: Throwable) {
                plugin.logger.warning("空岛初始地形生成失败 ${world.name}: ${ex.message}")
                done(false)
            }
        }
    }

    /** 纯方块生成，避免 World.generateTree 在 Folia/Arbor 中跨到 global-only 设置。 */
    private fun placeOakTree(world: World, x: Int, y: Int, z: Int) {
        for (dy in 0..3) world.getBlockAt(x, y + dy, z).setType(Material.OAK_LOG, false)
        val leaves = Material.OAK_LEAVES.createBlockData() as Leaves
        leaves.isPersistent = true
        leaves.distance = 1
        for (dy in 2..4) {
            val radius = if (dy == 4) 1 else 2
            for (dx in -radius..radius) for (dz in -radius..radius) {
                if (dy == 2 && kotlin.math.abs(dx) == 2 && kotlin.math.abs(dz) == 2) continue
                if (dx == 0 && dz == 0 && dy < 4) continue
                world.getBlockAt(x + dx, y + dy, z + dz).setBlockData(leaves.clone(), false)
            }
        }
    }
}
