package ym.dreamSkyblock.command

import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import ym.dreamSkyblock.DreamSkyblock
import ym.dreamSkyblock.config.ConfigService
import ym.dreamSkyblock.gui.HomeGuiManager
import ym.dreamSkyblock.service.HomeManager
import ym.dreamSkyblock.text.MessageService

class HomeCommand(private val plugin: DreamSkyblock, private val config: ConfigService, private val homes: HomeManager, private val gui: HomeGuiManager, private val messages: MessageService) : CommandExecutor, TabCompleter {
    private val admin = HomeAdminCommand(plugin, homes, messages)
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        val sub = args.firstOrNull()?.lowercase()
        if (sub in admin.commands) { admin.execute(sender, args); return true }
        if (sub == "reload") {
            if (!sender.hasPermission("dreamskyblock.admin")) {
                send(sender, "admin-only")
                return true
            }
            config.reloadAsync(
                { messages.reload(config.messages); plugin.levelService.reload(config.levels); homes.refreshRules(); send(sender, "reload.success") },
                { plugin.logger.warning("配置重载失败: ${it.message}"); send(sender, "reload.failed") }
            )
            return true
        }
        if (sender !is Player) { messages.send(sender, "player-only"); return true }
        if (!homes.ready) { messages.send(sender, "home.loading"); return true }
        if (!sender.hasPermission("dreamskyblock.use") && !sender.hasPermission("dreamskyblock.admin")) {
            messages.send(sender, "no-permission")
            return true
        }
        if (sub == null) {
            homes.teleport(sender)
            return true
        }
        when (sub) {
            "create" -> if (args.size < 2) {
                if (homes.byPlayer(sender.uniqueId) != null) messages.send(sender, "home.already-member") else gui.openTemplate(sender)
            } else homes.create(sender, args[1])
            "tp", "teleport" -> homes.teleport(sender)
            "invite" -> {
                val target = args.getOrNull(1)?.let { sender.server.getPlayerExact(it) }
                if (target == null) messages.send(sender, "player-not-found") else homes.invite(sender, target)
            }
            "accept" -> args.getOrNull(1)?.let { homes.accept(sender, it) } ?: messages.send(sender, "invite.token-required")
            "deny" -> args.getOrNull(1)?.let { homes.deny(sender, it) } ?: messages.send(sender, "invite.token-required")
            "leave" -> homes.leave(sender)
            "delete" -> homes.delete(sender)
            "settings" -> gui.openSettings(sender)
            "help" -> messages.send(sender, "home.help")
            else -> messages.send(sender, "command.usage")
        }
        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> = when (args.size) {
        1 -> (listOf("create", "tp", "invite", "accept", "deny", "leave", "delete", "settings", "help") +
            if (sender.hasPermission("dreamskyblock.admin")) listOf("reload", "backup", "restore", "migrate") else emptyList())
            .filter { it.startsWith(args[0], true) }
        2 -> when {
            sender.hasPermission("dreamskyblock.admin") && args[0].lowercase() in setOf("backup", "restore") -> homes.all().map { it.id.toString() }.filter { it.startsWith(args[1], true) }
            args[0].equals("create", true) -> config.templates.keys.toList()
            args[0].equals("invite", true) -> sender.server.onlinePlayers.map { it.name }.filter { it.startsWith(args[1], true) }
            args[0].equals("accept", true) || args[0].equals("deny", true) ->
                (sender as? Player)?.let { homes.inviteTokens(it.uniqueId).filter { token -> token.startsWith(args[1], true) } } ?: emptyList()
            else -> emptyList()
        }
        else -> emptyList()
    }

    private fun send(sender: CommandSender, key: String) {
        if (sender is Player) {
            sender.getScheduler().execute(plugin, { if (sender.isOnline) messages.send(sender, key) }, null, 0L)
        } else {
            messages.send(sender, key)
        }
    }
}
