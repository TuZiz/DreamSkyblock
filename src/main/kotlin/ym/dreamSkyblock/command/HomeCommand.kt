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
import java.util.UUID

class HomeCommand(private val plugin: DreamSkyblock, private val config: ConfigService, private val homes: HomeManager, private val gui: HomeGuiManager, private val messages: MessageService) : CommandExecutor, TabCompleter {
    private val admin = HomeAdminCommand(plugin, homes, messages)
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        val sub = args.firstOrNull()?.let(HomeCommandNames::resolve)
        if (sub in admin.commands) { admin.execute(sender, arrayOf(sub!!) + args.drop(1)); return true }
        if (sub == "reload") {
            if (!sender.hasPermission("dreamskyblock.admin")) {
                send(sender, "admin-only")
                return true
            }
            config.reloadAsync(
                { gui.closeAll(); messages.reload(config.messages); plugin.levelService.reload(config.levels); homes.refreshRules(); send(sender, "reload.success") },
                { plugin.logger.warning("配置重载失败: ${it.message}"); send(sender, "reload.failed") }
            )
            return true
        }
        if (sub == "gui" && args.size > 1) { openGuiFor(sender, args); return true }
        if (sender !is Player) { messages.send(sender, if (sub == "gui") "gui.command-usage" else "player-only"); return true }
        if (!homes.ready) { messages.send(sender, "home.loading"); return true }
        if (!sender.hasPermission("dreamskyblock.use") && !sender.hasPermission("dreamskyblock.admin")) {
            messages.send(sender, "no-permission")
            return true
        }
        if (sub == null) {
            if (homes.byPlayer(sender.uniqueId) == null) gui.openMain(sender) else homes.teleport(sender)
            return true
        }
        when (sub) {
            "gui" -> gui.openMain(sender)
            "menu-action" -> args.getOrNull(1)?.let { component ->
                val homeId = homes.byPlayer(sender.uniqueId)?.id
                val inventory = sender.openInventory.topInventory
                sender.scheduler.execute(plugin, {
                    if (plugin.isEnabled && sender.isOnline && homes.byPlayer(sender.uniqueId)?.id == homeId &&
                        sender.openInventory.topInventory === inventory) gui.performMenuAction(sender, component)
                }, null, 1L)
            }
            "create" -> if (args.size < 2) {
                gui.openConfirm(sender)
            } else gui.openConfirm(sender, args[1])
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
            "help" -> gui.openHelp(sender)
            else -> messages.send(sender, "command.usage")
        }
        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> = when (args.size) {
        1 -> HomeCommandNames.suggestions(sender.hasPermission("dreamskyblock.admin"))
            .filter { it.startsWith(args[0], true) }
        2 -> when {
            sender.hasPermission("dreamskyblock.admin") && HomeCommandNames.resolve(args[0]) == "gui" ->
                sender.server.onlinePlayers.map { it.name }.filter { it.startsWith(args[1], true) }
            sender.hasPermission("dreamskyblock.admin") && HomeCommandNames.resolve(args[0]) in setOf("backup", "restore") -> homes.all().map { it.id.toString() }.filter { it.startsWith(args[1], true) }
            HomeCommandNames.resolve(args[0]) == "create" -> config.creationTemplates.keys.filter { it.startsWith(args[1], true) }
            HomeCommandNames.resolve(args[0]) == "invite" -> sender.server.onlinePlayers.map { it.name }.filter { it.startsWith(args[1], true) }
            HomeCommandNames.resolve(args[0]) in setOf("accept", "deny") ->
                (sender as? Player)?.let { homes.inviteTokens(it.uniqueId).filter { token -> token.startsWith(args[1], true) } } ?: emptyList()
            else -> emptyList()
        }
        else -> emptyList()
    }

    /** Paper 主线程命令入口；目标始终使用自己的归属、权限和现有菜单路由。 */
    private fun openGuiFor(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("dreamskyblock.admin")) { messages.send(sender, "admin-only"); return }
        if (args.size != 2) { messages.send(sender, "gui.command-usage"); return }
        if (!homes.ready) { messages.send(sender, "home.loading"); return }
        val target = sender.server.getPlayerExact(args[1])
            ?: runCatching { UUID.fromString(args[1]) }.getOrNull()?.let(sender.server::getPlayer)
        if (target == null || !target.isOnline) { messages.send(sender, "player-not-found"); return }
        gui.openMain(target)
    }

    private fun send(sender: CommandSender, key: String) {
        if (sender is Player) {
            sender.getScheduler().execute(plugin, { if (sender.isOnline) messages.send(sender, key) }, null, 0L)
        } else {
            messages.send(sender, key)
        }
    }
}
