package ym.dreamSkyblock

import org.bukkit.event.Listener
import org.bukkit.plugin.ServicePriority
import org.bukkit.plugin.java.JavaPlugin
import ym.dreamSkyblock.api.DefaultDreamSkyblockApi
import ym.dreamSkyblock.api.DreamSkyblockApi
import ym.dreamSkyblock.command.HomeCommand
import ym.dreamSkyblock.config.ConfigService
import ym.dreamSkyblock.gui.HomeGuiManager
import ym.dreamSkyblock.listener.HomeOccupancyListener
import ym.dreamSkyblock.listener.HomeProtectionListener
import ym.dreamSkyblock.placeholder.DreamSkyblockExpansion
import ym.dreamSkyblock.service.HomeManager
import ym.dreamSkyblock.service.HomeLevelService
import ym.dreamSkyblock.storage.HomeMaintenanceService
import ym.dreamSkyblock.storage.HomeStorage
import ym.dreamSkyblock.storage.LegacyHomeMigration
import ym.dreamSkyblock.text.MessageService
import ym.dreamSkyblock.world.HomeWorldManager
import ym.dreamSkyblock.world.NativeWorldFiles
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.logging.Level

class DreamSkyblock : JavaPlugin() {
    lateinit var configService: ConfigService
        private set
    lateinit var homeManager: HomeManager
        private set
    lateinit var worldManager: HomeWorldManager
        private set
    lateinit var levelService: HomeLevelService
        private set
    private var storage: HomeStorage? = null
    private var expansion: DreamSkyblockExpansion? = null
    private var gui: HomeGuiManager? = null
    private val ioExecutor = Executors.newFixedThreadPool(2) { runnable -> Thread(runnable, "DreamSkyblock-IO").apply { isDaemon = false } }
    @Volatile private var stopping = false

    override fun onEnable() {
        // Purpur/Leaf 使用Paper主线程模型。禁止以Folia声明绕过不支持的世界生命周期。
        val regionThreaded = runCatching { Class.forName("io.papermc.paper.threadedregions.RegionizedServer") }.isSuccess
        if (regionThreaded) {
            logger.severe("当前运行路径只支持 Paper/Purpur/Leaf 26.2 主线程模型；拒绝在 Folia/Canvas/Arbor 注册世界生命周期")
            server.pluginManager.disablePlugin(this)
            return
        }
        logger.info("DreamSkyblock ${pluginMeta.version}, server=${server.version}, Minecraft=${server.minecraftVersion}, Java=${System.getProperty("java.version")}; SQLite schema=1, native namespace=dreamskyblock")
        configService = ConfigService(this)
        CompletableFuture.runAsync({ configService.load() }, ioExecutor).whenComplete { _, error ->
            if (stopping || !isEnabled) return@whenComplete
            server.globalRegionScheduler.execute(this) {
                if (error != null) {
                    logger.log(Level.SEVERE, "配置启动失败；保留原配置并停止插件", error)
                    server.pluginManager.disablePlugin(this)
                } else assemble()
            }
        }
    }

    private fun assemble() {
        levelService = HomeLevelService(configService.levels)
        val messages = MessageService(configService.messages)
        val database = HomeStorage(this, configService).also { storage = it }
        val files = NativeWorldFiles(dataFolder.toPath(), server.levelDirectory, dataFolder.toPath().resolve(configService.dataFolderName))
        worldManager = HomeWorldManager(this, configService, files, ioExecutor)
        val migration = LegacyHomeMigration(this, configService, database, files, ioExecutor)
        val maintenance = HomeMaintenanceService(database, files, worldManager, dataFolder.toPath(), ioExecutor)
        homeManager = HomeManager(this, configService, database, worldManager, levelService, messages, ioExecutor, migration, maintenance)
        val gui = HomeGuiManager(this, configService, homeManager, messages).also { this.gui = it }
        val command = HomeCommand(this, configService, homeManager, gui, messages)
        getCommand("home")?.apply { setExecutor(command); setTabCompleter(command) }
        server.pluginManager.registerEvents(HomeProtectionListener(homeManager, worldManager), this)
        server.pluginManager.registerEvents(HomeOccupancyListener(homeManager, worldManager, configService), this)
        server.pluginManager.registerEvents(object : Listener {
            @org.bukkit.event.EventHandler fun onClick(event: org.bukkit.event.inventory.InventoryClickEvent) = gui.click(event)
            @org.bukkit.event.EventHandler fun onDrag(event: org.bukkit.event.inventory.InventoryDragEvent) = gui.drag(event)
        }, this)
        server.servicesManager.register(DreamSkyblockApi::class.java, DefaultDreamSkyblockApi(homeManager), this, ServicePriority.Normal)
        if (server.pluginManager.isPluginEnabled("PlaceholderAPI")) expansion = DreamSkyblockExpansion(this, levelService).also { it.register() }
        homeManager.load {
            logger.info("DreamSkyblock 已加载 ${homeManager.all().size} 个家园，SQLite=${database.databasePath}, world-root=${server.levelDirectory}")
            CompletableFuture.runAsync({ files.audit(homeManager.all().map { it.id }.toSet()).forEach { path ->
                logger.severe("检测到无归属世界，保留原目录，禁止自动删除: $path")
            } }, ioExecutor).exceptionally { logger.log(Level.SEVERE, "世界归属审计失败", it); null }
        }
    }

    override fun onDisable() {
        stopping = true
        gui?.closeAll(); gui = null
        if (::worldManager.isInitialized) worldManager.shutdown()
        if (::homeManager.isInitialized) homeManager.shutdown()
        expansion?.unregister(); expansion = null
        server.servicesManager.unregisterAll(this)
        server.globalRegionScheduler.cancelTasks(this)
        server.asyncScheduler.cancelTasks(this)
        // 收到的事务顺序排空后关闭连接，不在主线程阻塞等待；持久journal可在重启时续接。
        storage?.closeAsync()?.whenComplete { _, error -> if (error != null) logger.log(Level.SEVERE, "homes.db 关闭失败", error) }
        ioExecutor.shutdown()
    }
}
