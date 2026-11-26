package ym.dreamSkyblock.gui.invero

import org.bukkit.entity.Player
import ym.dreamSkyblock.DreamSkyblock
import ym.dreamSkyblock.config.ConfigService
import ym.dreamSkyblock.gui.HomeMenu
import java.util.logging.Level

/** Paper player/main thread. Invero 1.4.17 API, resolved using its owning plugin loader. */
class InveroHomeMenus(private val plugin: DreamSkyblock, private val config: ConfigService) {
    private val managedIds = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private fun loader(): ClassLoader? = plugin.server.pluginManager.getPlugin("Invero")
        ?.takeIf { it.isEnabled }?.javaClass?.classLoader

    private fun manager(loader: ClassLoader): Any {
        val factory = loader.loadClass("cc.trixey.invero.taboolib.common.platform.PlatformFactory")
        val api = factory.getMethod("getAPI", String::class.java).invoke(factory.getField("INSTANCE").get(null), "cc.trixey.invero.common.api.InveroAPI")
        return api.javaClass.getMethod("getMenuManager").invoke(api)
    }

    private fun menuId(menu: HomeMenu): String = if (menu == HomeMenu.MAIN) config.inveroMenus.mainMenu else config.inveroMenus.islandMenu

    fun open(player: Player, menu: HomeMenu, values: Map<String, String>): Boolean {
        if (!config.inveroMenus.enabled || menuId(menu).isEmpty()) return false
        val loader = loader() ?: return false
        return try {
            val manager = manager(loader)
            val target = manager.javaClass.getMethod("getMenu", String::class.java, Boolean::class.javaPrimitiveType)
                .invoke(manager, menuId(menu), false)
            if (target == null) {
                plugin.logger.warning("Invero 主菜单不存在: menu=${menuId(menu)}, player=${player.uniqueId}；回退原箱子")
                false
            } else {
                loader.loadClass("cc.trixey.invero.common.Menu").getMethod("open", Player::class.java, Map::class.java)
                    .invoke(target, player, values)
                managedIds.add(menuId(menu))
                true
            }
        } catch (error: Exception) {
            plugin.logger.log(Level.WARNING, "Invero 主菜单打开失败: menu=${menuId(menu)}, player=${player.uniqueId}；回退原箱子", error)
            false
        }
    }

    private fun sessionMenu(player: Player, ids: Set<String> = managedIds): Any? {
        val loader = loader() ?: return null
        val viewerType = loader.loadClass("cc.trixey.invero.ui.bukkit.PlayerViewer")
        val viewer = viewerType.getConstructor(Player::class.java).newInstance(player)
        val sessionType = loader.loadClass("cc.trixey.invero.core.Session")
        val companion = sessionType.getField("Companion").get(null)
        val session = companion.javaClass.getMethod("getSession", viewerType).invoke(companion, viewer) ?: return null
        val menu = sessionType.getMethod("getMenu").invoke(session)
        val id = menu.javaClass.getMethod("getId").invoke(menu)
        return menu.takeIf { id in ids || id == config.inveroMenus.mainMenu || id == config.inveroMenus.islandMenu }
    }

    fun viewing(player: Player): Boolean = runCatching { sessionMenu(player) != null }.getOrElse {
        plugin.logger.log(Level.WARNING, "Invero 主菜单会话查询失败: player=${player.uniqueId}", it); false
    }

    fun closeAll() {
        val ids = managedIds.toSet()
        plugin.server.onlinePlayers.forEach { player ->
            val close = Runnable {
              try {
                val menu = sessionMenu(player, ids) ?: return@Runnable
                loader()!!.loadClass("cc.trixey.invero.common.Menu")
                    .getMethod("close", Player::class.java, Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
                    .invoke(menu, player, false, false)
              } catch (error: Exception) { plugin.logger.log(Level.WARNING, "Invero 主菜单关闭失败: player=${player.uniqueId}", error) }
            }
            if (org.bukkit.Bukkit.isOwnedByCurrentRegion(player)) close.run()
            else if (plugin.isEnabled) player.scheduler.execute(plugin, close, null, 1L)
        }
        managedIds.clear()
    }
}
