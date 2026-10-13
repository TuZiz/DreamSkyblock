package ym.dreamSkyblock

import org.bukkit.event.Listener
import org.bukkit.plugin.java.JavaPlugin
import ym.dreamSkyblock.command.HomeCommand
import ym.dreamSkyblock.config.ConfigService
import ym.dreamSkyblock.gui.HomeGuiManager
import ym.dreamSkyblock.listener.HomeProtectionListener
import ym.dreamSkyblock.service.HomeManager
import ym.dreamSkyblock.service.HomeLevelService
import ym.dreamSkyblock.placeholder.DreamSkyblockExpansion
import ym.dreamSkyblock.storage.HomeStorage
import ym.dreamSkyblock.text.MessageService
import ym.dreamSkyblock.world.HomeWorldManager
import ym.dreamSkyblock.world.WorldGuardConfigCleaner
import java.util.concurrent.Executors

class DreamSkyblock : JavaPlugin() {
    lateinit var configService: ConfigService
        private set
    lateinit var homeManager: HomeManager
        private set
    lateinit var worldManager: HomeWorldManager
        private set
    lateinit var levelService: HomeLevelService
        private set
    private val ioExecutor = Executors.newFixedThreadPool(2) { runnable -> Thread(runnable, "DreamSkyblock-IO").apply { isDaemon = true } }

    override fun onEnable() {
        configService = ConfigService(this)
        configService.load()
        levelService = HomeLevelService(configService.levels)
        val messages = MessageService(configService.messages)
        val storage = HomeStorage(this, configService)
        val worldGuardWorlds = server.pluginManager.getPlugin("WorldGuard")?.dataFolder?.toPath()?.resolve("worlds")
        val worldGuardConfigCleaner = WorldGuardConfigCleaner(this, worldGuardWorlds, ioExecutor)
        val worlds = HomeWorldManager(this, configService, storage, ioExecutor, worldGuardConfigCleaner)
        worldManager = worlds
        homeManager = HomeManager(this, configService, storage, worlds, levelService, messages, ioExecutor)
        val gui = HomeGuiManager(this, configService, homeManager, messages)
        val command = HomeCommand(this, configService, homeManager, gui, messages)
        getCommand("home")?.apply { setExecutor(command); setTabCompleter(command) }
        server.pluginManager.registerEvents(HomeProtectionListener(homeManager, worlds), this)
        server.pluginManager.registerEvents(object : Listener {
            @org.bukkit.event.EventHandler fun onClick(event: org.bukkit.event.inventory.InventoryClickEvent) = gui.click(event)
            @org.bukkit.event.EventHandler fun onClose(event: org.bukkit.event.inventory.InventoryCloseEvent) = gui.close(event)
            @org.bukkit.event.EventHandler fun onJoin(event: org.bukkit.event.player.PlayerJoinEvent) {
                homeManager.byPlayer(event.player.uniqueId)?.let { home -> worlds.cancelUnload(home) }
            }
            @org.bukkit.event.EventHandler fun onQuit(event: org.bukkit.event.player.PlayerQuitEvent) {
                homeManager.byPlayer(event.player.uniqueId)?.let { home -> if (worlds.getLoaded(home)?.players?.none { it.uniqueId != event.player.uniqueId } == true) worlds.scheduleUnload(home, configService.unloadDelaySeconds) }
            }
        }, this)
        if (server.pluginManager.isPluginEnabled("PlaceholderAPI")) {
            DreamSkyblockExpansion(this, levelService).register()
        }
        homeManager.load {
            worldGuardConfigCleaner.cleanupStaleAsync(server.worlds.map { it.name }.toSet())
            logger.info("DreamSkyblock 已加载 ${homeManager.all().size} 个家园")
        }
    }

    override fun onDisable() {
        if (::homeManager.isInitialized) logger.info("DreamSkyblock 正在关闭")
        if (::worldManager.isInitialized) worldManager.shutdown()
        ioExecutor.shutdown()
    }
}
