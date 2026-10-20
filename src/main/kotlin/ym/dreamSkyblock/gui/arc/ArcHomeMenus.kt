package ym.dreamSkyblock.gui.arc

import com.fentai.arcmenu.api.ArcMenuApi
import com.fentai.arcmenu.api.ArcMenuRoute
import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryOpenEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.server.PluginDisableEvent
import ym.dreamSkyblock.DreamSkyblock
import ym.dreamSkyblock.config.ConfigService
import ym.dreamSkyblock.gui.HomeMenu
import ym.dreamSkyblock.service.HomeManager
import java.util.UUID
import java.util.logging.Level

/** Paper main-thread navigation only. ArcMenu owns display/input; DreamSkyblock owns all business. */
class ArcHomeMenus(
    private val plugin: DreamSkyblock,
    private val config: ConfigService,
    private val homes: HomeManager,
    private val openHome: (Player) -> Unit,
    private val action: (Player, HomeMenu, UUID?, String) -> Unit
) : Listener {
    private data class Session(val token: String, val menu: HomeMenu, val homeId: UUID?)
    private val sessions = mutableMapOf<UUID, Session>()
    private val pending = mutableMapOf<UUID, Session>()
    private var api: ArcMenuApi? = null
    private var cleanup: ScheduledTask? = null

    fun start() {
        val bridge = plugin.server.servicesManager.load(ArcMenuApi::class.java)
        if (bridge == null || bridge.apiVersion() != 1) {
            plugin.logger.warning("ArcMenu 3.0.1 API v1 不可用；保留原箱子入口")
            return
        }
        api = bridge
        bridge.registerRoute(plugin, "dreamskyblock:home", ArcMenuRoute { player, _ -> openHome(player); true })
        bridge.registerRoute(plugin, "dreamskyblock:action", ArcMenuRoute(::activate))
        bridge.registerRoute(plugin, "dreamskyblock:released", ArcMenuRoute { player, args ->
            if (sessions[player.uniqueId]?.token == args.firstOrNull()) sessions.remove(player.uniqueId)
            false // Cleanup is not a navigation; ArcMenu must complete the original close/switch.
        })
        plugin.server.pluginManager.registerEvents(this, plugin)
        cleanup = plugin.server.globalRegionScheduler.runAtFixedRate(plugin, {
            sessions.keys.toList().forEach { id ->
                val player = plugin.server.getPlayer(id)
                val session = sessions[id] ?: return@forEach
                if (player == null) sessions.remove(id)
                else if (bridge.surface(player) == null) sessions.remove(id)
                else if (!valid(player, session)) close(player)
            }
        }, 20L, 20L)
        plugin.logger.info("空岛主界面已接入 ArcMenu API v1；业务与子菜单继续使用原 DreamSkyblock")
    }

    fun open(player: Player, menu: HomeMenu): Boolean {
        pending.remove(player.uniqueId)
        val bridge = api ?: return false
        if (!config.arcMenus.getBoolean("enabled") || !player.hasPermission("arcmenu.use")) return false
        val menuId = config.arcMenus.getString(if (menu == HomeMenu.MAIN) "main-menu" else "island-menu")!!
        val session = Session(UUID.randomUUID().toString(), menu, homes.byPlayer(player.uniqueId)?.id)
        close(player)
        // ArcMenu reload can discard its session registry while leaving a live surface.
        // Clear that stale surface before opening the replacement menu.
        if (bridge.surface(player) != null) bridge.close(player)
        player.closeInventory()
        return try {
            if (!bridge.open(player, menuId, listOf(session.token))) return false
            sessions[player.uniqueId] = session
            true
        } catch (error: Exception) {
            plugin.logger.log(Level.WARNING, "ArcMenu 打开失败: player=${player.uniqueId}, menu=$menuId；回退原箱子", error)
            false
        }
    }

    private fun activate(player: Player, args: List<String>): Boolean {
        val bridge = api ?: return false
        val session = sessions[player.uniqueId] ?: return false
        if (args.size != 2 || args[0] != session.token || !valid(player, session) || bridge.surface(player) == null) return false
        val component = args[1]
        val allowed = if (session.menu == HomeMenu.MAIN) setOf("create", "join", "help", "close")
            else setOf("members", "settings", "help", "upgrade", "teleport", "spawn", "warehouse", "close")
        if (component !in allowed) return false
        if (component != "upgrade") close(player)
        pending[player.uniqueId] = session
        plugin.server.globalRegionScheduler.execute(plugin) {
            if (pending[player.uniqueId] === session) {
                pending.remove(player.uniqueId)
                if (valid(player, session)) action(player, session.menu, session.homeId, component)
            }
        }
        return true
    }

    private fun valid(player: Player, session: Session): Boolean =
        plugin.isEnabled && player.isOnline && homes.ready && player.hasPermission("dreamskyblock.use") &&
            homes.byPlayer(player.uniqueId)?.id == session.homeId

    fun viewing(player: Player): Boolean = sessions.containsKey(player.uniqueId) && api?.surface(player) != null

    fun close(player: Player) {
        pending.remove(player.uniqueId)
        if (sessions.remove(player.uniqueId) != null) api?.close(player)
    }

    fun closeAll() {
        sessions.keys.toList().mapNotNull(plugin.server::getPlayer).forEach(::close)
        sessions.clear()
        pending.clear()
    }

    fun stop() {
        cleanup?.cancel(); cleanup = null
        closeAll()
        api?.unregisterRoutes(plugin)
        api = null
        HandlerList.unregisterAll(this)
    }

    @EventHandler fun onQuit(event: PlayerQuitEvent) { sessions.remove(event.player.uniqueId); pending.remove(event.player.uniqueId) }
    @EventHandler(ignoreCancelled = true) fun onInventory(event: InventoryOpenEvent) {
        (event.player as? Player)?.let(::close)
    }
    @EventHandler fun onPluginDisable(event: PluginDisableEvent) {
        if (event.plugin.name == "ArcMenu") {
            cleanup?.cancel(); cleanup = null
            sessions.clear(); pending.clear(); api = null
        }
    }
}
