package ym.dreamSkyblock.login

import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.persistence.PersistentDataType
import ym.dreamSkyblock.DreamSkyblock
import ym.dreamSkyblock.service.HomeManager
import ym.dreamSkyblock.world.HomeWorldManager
import java.util.UUID

/** Paper/Purpur/Leaf 主线程。原生玩家 PDC 只记录返回意图，归属始终由 homes.db 决定。 */
class HomeLoginReturn(private val plugin: DreamSkyblock, private val homes: HomeManager, private val worlds: HomeWorldManager) : Listener {
    private class Session(val player: Player, val homeId: UUID, var returning: Boolean = false)
    private val key = NamespacedKey(plugin, "login_return_home")
    private val sessions = mutableMapOf<UUID, Session>()
    private var task: ScheduledTask? = null

    fun start() {
        if (task != null) return
        task = plugin.server.globalRegionScheduler.runAtFixedRate(plugin, { pulse() }, 20L, 20L)
        plugin.server.onlinePlayers.forEach(::track)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun join(event: PlayerJoinEvent) { if (task != null) track(event.player) }

    @EventHandler(priority = EventPriority.MONITOR)
    fun quit(event: PlayerQuitEvent) {
        // 尚在等待加载时退出，保留意图供下次登录；旧 Player 对象的回调自动失效。
        val pending = sessions.remove(event.player.uniqueId)
        if (pending == null && homes.ready) remember(event.player)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun teleport(event: PlayerTeleportEvent) {
        val session = sessions[event.player.uniqueId] ?: return
        if (session.player !== event.player) return
        if (session.returning && worlds.homeByWorld(event.to.world) == session.homeId) return
        // 玩家或其他插件已选择目的地，取消自动返回，包括同世界内的传送。
        sessions.remove(event.player.uniqueId)
        if (homes.ready) remember(event.player, worlds.homeByWorld(event.to.world))
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun changed(event: PlayerChangedWorldEvent) {
        sessions.remove(event.player.uniqueId)
        if (homes.ready) remember(event.player)
    }

    private fun track(player: Player) {
        sessions.remove(player.uniqueId)
        val raw = player.persistentDataContainer.get(key, PersistentDataType.STRING) ?: return
        val id = try { UUID.fromString(raw) } catch (_: IllegalArgumentException) {
            plugin.logger.warning("登录返回标记无效 player=${player.uniqueId}；已清理")
            player.persistentDataContainer.remove(key)
            return
        }
        sessions[player.uniqueId] = Session(player, id)
    }

    private fun pulse() {
        if (task == null || !plugin.isEnabled || !homes.ready) return
        sessions.values.toList().forEach { session ->
            val player = session.player
            if (!current(session)) { sessions.remove(player.uniqueId, session); return@forEach }
            val home = homes.byPlayer(player.uniqueId)
            if (home?.id != session.homeId || !home.isOwner(player.uniqueId) || !player.hasPermission("dreamskyblock.use")) {
                sessions.remove(player.uniqueId, session)
                player.persistentDataContainer.remove(key)
                return@forEach
            }
            if (worlds.homeByWorld(player.world) == home.id) {
                worlds.cancelUnload(home)
                sessions.remove(player.uniqueId, session)
                return@forEach
            }
            if (session.returning) return@forEach
            session.returning = true
            val started = homes.returnOnLogin(player, home.id, { current(session) }) {
                // 不自动反复重试失败存档；保留 PDC，玩家可用 /home 或重登再试。
                ok -> if (ok) sessions.remove(player.uniqueId, session)
            }
            if (!started) session.returning = false
        }
    }

    private fun current(session: Session): Boolean = task != null && plugin.isEnabled &&
        session.player.isOnline && sessions[session.player.uniqueId] === session

    private fun remember(player: Player, worldHome: UUID? = worlds.homeByWorld(player.world)) {
        val home = worldHome?.let(homes::byId)?.takeIf { it.isOwner(player.uniqueId) }
        if (home == null) player.persistentDataContainer.remove(key)
        else player.persistentDataContainer.set(key, PersistentDataType.STRING, home.id.toString())
    }

    fun stop() {
        // 先记录仍在线玩家，再由服务器原生玩家保存处理 PDC；不在主线程强制 saveData/写盘。
        if (homes.ready) plugin.server.onlinePlayers.filter { it.uniqueId !in sessions }.forEach { remember(it) }
        task?.cancel()
        task = null
        sessions.clear()
    }
}
