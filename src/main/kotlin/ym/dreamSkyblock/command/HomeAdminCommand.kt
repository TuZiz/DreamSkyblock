package ym.dreamSkyblock.command

import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import ym.dreamSkyblock.DreamSkyblock
import ym.dreamSkyblock.service.HomeManager
import ym.dreamSkyblock.text.MessageService
import java.util.UUID
import java.util.logging.Level

/** 管理操作只整理参数，备份/恢复/迁移交给具备生命周期边界的服务。 */
class HomeAdminCommand(private val plugin: DreamSkyblock, private val homes: HomeManager, private val messages: MessageService) {
    val commands = setOf("backup", "restore", "migrate")

    fun execute(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("dreamskyblock.admin")) { send(sender, "admin-only"); return }
        if (args[0].equals("migrate", true)) {
            homes.load { plugin.logger.info("DreamSkyblock 数据迁移重试完成，家园数=${homes.all().size}") }
                .whenComplete { _, error -> send(sender, if (error == null) "maintenance.migrated" else "maintenance.failed") }
            return
        }
        if (!homes.ready) { send(sender, "home.loading"); return }
        val id = args.getOrNull(1)?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            ?: if (args.size == 1 && sender is Player) homes.byPlayer(sender.uniqueId)?.id else null
        if (id == null) { send(sender, "maintenance.usage"); return }
        when (args[0].lowercase()) {
            "backup" -> homes.backup(id).whenComplete { backup, error ->
                if (error == null) send(sender, "maintenance.backup", mapOf("home_id" to id.toString(), "backup_id" to backup))
                else failed(sender, id, error)
            }
            "restore" -> {
                val backupId = args.getOrNull(2)?.let { runCatching { UUID.fromString(it).toString() }.getOrNull() }
                if (backupId == null) { send(sender, "maintenance.usage"); return }
                homes.restore(id, backupId).whenComplete { _, error ->
                    if (error == null) send(sender, "maintenance.restored", mapOf("home_id" to id.toString()))
                    else failed(sender, id, error)
                }
            }
        }
    }

    private fun failed(sender: CommandSender, id: UUID, error: Throwable) {
        plugin.logger.log(Level.SEVERE, "管理员空岛维护失败 home_id=$id", error)
        send(sender, "maintenance.failed")
    }

    private fun send(sender: CommandSender, key: String, placeholders: Map<String, String> = emptyMap()) {
        if (!plugin.isEnabled) return
        if (sender is Player) sender.scheduler.execute(plugin, { if (sender.isOnline) messages.send(sender, key, placeholders) }, null, 0L)
        else plugin.server.globalRegionScheduler.execute(plugin) { messages.send(sender, key, placeholders) }
    }
}
