package ym.dreamSkyblock.gui

import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.inventory.Inventory
import ym.dreamSkyblock.DreamSkyblock
import ym.dreamSkyblock.config.ConfigService
import ym.dreamSkyblock.model.HomeFlag
import ym.dreamSkyblock.service.HomeManager
import ym.dreamSkyblock.text.MessageService
import java.util.UUID
import ym.dreamSkyblock.gui.arc.ArcHomeMenus
import ym.dreamSkyblock.gui.invero.InveroHomeMenus
import ym.dreamSkyblock.world.WorldSpawnTeleport

/** Main-thread menu navigation; mutations are delegated to existing transaction services. */
class HomeGuiManager(private val plugin: DreamSkyblock, private val config: ConfigService,
                     private val homes: HomeManager, private val messages: MessageService) {
    private val renderer = HomeMenuRenderer(messages)
    private val data = HomeMenuData(config, homes, plugin.levelService, messages)
    private var arc: ArcHomeMenus? = null
    private val invero = InveroHomeMenus(plugin, config)
    private val spawnTeleport = WorldSpawnTeleport(plugin, messages)

    /** Paper main thread; optional ArcMenu is resolved only after its plugin is enabled. */
    fun start() {
        if (config.inveroMenus.enabled) {
            plugin.logger.info("空岛主菜单选择 Invero；业务和子菜单继续使用 DreamSkyblock")
        } else if (plugin.server.pluginManager.isPluginEnabled("ArcMenu")) {
            arc = ArcHomeMenus(plugin, config, homes, ::openMain, ::performArcAction).also { it.start() }
        } else plugin.logger.info("ArcMenu 未启用，空岛入口保留原箱子菜单")
    }

    fun viewing(player: Player): Boolean = player.openInventory.topInventory.holder is HomeMenuHolder || arc?.viewing(player) == true || invero.viewing(player)

    fun stop() { closeAll(); arc?.stop(); arc = null }

    fun openMain(player: Player) = open(player, MenuRequest(if (homes.byPlayer(player.uniqueId) == null) HomeMenu.MAIN else HomeMenu.ISLAND))
    fun openSettings(player: Player) = open(player, MenuRequest(HomeMenu.SETTINGS))
    fun openHelp(player: Player) = open(player, MenuRequest(HomeMenu.HELP))
    fun openConfirm(player: Player, template: String? = null) {
        if (homes.byPlayer(player.uniqueId) != null) return messages.send(player, "home.already-member")
        val id = template ?: config.creationTemplates.keys.firstOrNull()
        if (id !in config.creationTemplates) return messages.send(player, "template.not-found")
        open(player, MenuRequest(HomeMenu.CONFIRM, template = id))
    }

    private fun allowed(player: Player): Boolean {
        if (!player.isOnline || !plugin.isEnabled) return false
        if (!player.hasPermission("dreamskyblock.use")) { messages.send(player, "no-permission"); return false }
        if (!homes.ready) { messages.send(player, "home.loading"); return false }
        return true
    }

    fun open(player: Player, request: MenuRequest) {
        if (!allowed(player)) return
        val home = homes.byPlayer(player.uniqueId)
        if (request.menu in setOf(HomeMenu.ISLAND, HomeMenu.MEMBERS, HomeMenu.PLAYERS, HomeMenu.MEMBER_CONFIRM) && home == null) {
            messages.send(player, "home.none"); openMain(player); return
        }
        if (request.menu == HomeMenu.CONFIRM && (home != null || request.template !in config.creationTemplates)) { openMain(player); return }
        if (request.menu == HomeMenu.MEMBER_CONFIRM && request.member !in home!!.owners) { open(player, MenuRequest(HomeMenu.MEMBERS)); return }
        if (request.menu == HomeMenu.MAIN && home != null) { openMain(player); return }
        if (request.menu in setOf(HomeMenu.MAIN, HomeMenu.ISLAND)) {
            if (config.inveroMenus.enabled) {
                if (invero.open(player, request.menu, data.values(player, home, request))) { tutorialOpened(player,request); return }
            } else if (arc?.open(player, request.menu) == true) { tutorialOpened(player,request); return }
        }
        arc?.close(player)
        val yaml = config.menus.getValue(request.menu)
        val existing = player.openInventory.topInventory
        val holder = existing.holder as? HomeMenuHolder
        if (holder != null && current(player, existing, holder) && holder.request.menu == request.menu) {
            refresh(player, existing, holder, request)
            return
        }
        val inventory = renderer.render(player, home?.id, request, yaml, data.values(player, home, request),
            data.entries(player, home, request.menu), home?.flags?.toMap() ?: emptyMap())
        player.openInventory(inventory)
        tutorialOpened(player,request)
    }

    private fun tutorialOpened(player: Player, request: MenuRequest) {
        if(request.menu != HomeMenu.ISLAND) return
        plugin.server.scheduler.runTask(plugin,Runnable {
            if(!plugin.isEnabled || !player.isOnline || !viewing(player)) return@Runnable
            val home=homes.byPlayer(player.uniqueId) ?: return@Runnable
            plugin.server.pluginManager.callEvent(ym.dreamSkyblock.api.HomeMenuOpenedEvent(home.id,player.uniqueId,request.menu.name))
        })
    }

    fun click(event: InventoryClickEvent) {
        val inventory = event.view.topInventory
        val holder = inventory.holder as? HomeMenuHolder ?: return
        event.isCancelled = true // Includes bottom-inventory shift clicks and number-key swaps.
        val player = event.whoClicked as? Player ?: return
        if (holder.playerId != player.uniqueId || event.rawSlot !in 0 until inventory.size) return
        val action = holder.actions[event.rawSlot]?.takeIf(String::isNotBlank) ?: return
        val revision = holder.revision
        // Paper documents that inventory transitions must occur after the click event.
        plugin.server.globalRegionScheduler.execute(plugin) {
            if (!current(player, inventory, holder) || holder.revision != revision || !allowed(player)) return@execute
            dispatch(player, inventory, holder, action)
        }
    }

    fun drag(event: InventoryDragEvent) {
        if (event.view.topInventory.holder is HomeMenuHolder) event.isCancelled = true
    }

    private fun current(player: Player, inventory: Inventory, holder: HomeMenuHolder): Boolean =
        plugin.isEnabled && player.isOnline && player.openInventory.topInventory === inventory &&
            holder.playerId == player.uniqueId && homes.byPlayer(player.uniqueId)?.id == holder.homeId

    /** Main thread, after a click or committed mutation; never updates a closed/replaced session. */
    private fun refresh(player: Player, inventory: Inventory, holder: HomeMenuHolder, request: MenuRequest = holder.request) {
        if (!current(player, inventory, holder) || !allowed(player)) return
        val home = homes.byPlayer(player.uniqueId)
        renderer.refresh(inventory, request, config.menus.getValue(request.menu), data.values(player, home, request),
            data.entries(player, home, request.menu), home?.flags?.toMap() ?: emptyMap())
    }

    private fun dispatch(player: Player, inventory: Inventory, holder: HomeMenuHolder, action: String) {
        val request = holder.request
        when {
            action == "close" -> player.closeInventory()
            action == "previous" -> open(player, request.copy(page = request.page - 1))
            action == "next" -> open(player, request.copy(page = request.page + 1))
            action == "menu:main" -> openMain(player)
            action == "menu:create_confirm" -> openConfirm(player)
            action.startsWith("menu:") -> HomeMenu.entries.firstOrNull { it.file == action.substringAfter(':') }?.let { open(player, MenuRequest(it)) }
            action.startsWith("confirm:") -> openConfirm(player, action.substringAfter(':'))
            action == "create" -> {
                if (request.menu != HomeMenu.CONFIRM) return
                val template = request.template ?: return
                player.closeInventory(); homes.create(player, template)
            }
            action == "teleport" -> { player.closeInventory(); homes.teleport(player) }
            action == "spawn" -> spawnTeleport.teleport(player)
            action == "warehouse" -> {
                if (request.menu != HomeMenu.ISLAND || homes.byPlayer(player.uniqueId) == null) return
                if (!plugin.server.pluginManager.isPluginEnabled("Genlite")) {
                    messages.send(player, "gui.warehouse-unavailable")
                    return
                }
                player.closeInventory()
                if (!player.performCommand("genlite:genlite warehouse")) messages.send(player, "gui.warehouse-unavailable")
            }
            action == "holograms" -> {
                if (request.menu != HomeMenu.ISLAND || homes.byPlayer(player.uniqueId) == null) return
                if (!plugin.server.pluginManager.isPluginEnabled("Genlite")) {
                    messages.send(player, "gui.unavailable")
                    return
                }
                player.closeInventory()
                if (!player.performCommand("genlite:genlite holograms menu")) messages.send(player, "gui.unavailable")
            }
            action == "upgrade" -> messages.send(player, "gui.upgrade-pending")
            action.startsWith("accept:") -> { player.closeInventory(); homes.accept(player, action.substringAfter(':')) }
            action.startsWith("invite:") -> {
                val target = uuid(action.substringAfter(':'))?.let(player.server::getPlayer)
                if (target == null || !target.isOnline) messages.send(player, "player-not-found") else homes.invite(player, target)
                refresh(player, inventory, holder)
            }
            action.startsWith("member:") -> uuid(action.substringAfter(':'))?.let { open(player, MenuRequest(HomeMenu.MEMBER_CONFIRM, member = it)) }
            action == "remove-member" -> {
                if (request.menu != HomeMenu.MEMBER_CONFIRM) return
                val member = request.member ?: return
                if (member == player.uniqueId) { player.closeInventory(); homes.leave(player) }
                else homes.removeMember(player, member) { if (current(player, inventory, holder)) open(player, MenuRequest(HomeMenu.MEMBERS)) }
            }
            action.startsWith("toggle:") -> {
                val flag = HomeFlag.entries.firstOrNull { it.name.equals(action.substringAfter(':'), true) } ?: return
                if (homes.byPlayer(player.uniqueId) == null) { messages.send(player, "home.none"); return }
                homes.toggleFlag(player, flag) { refresh(player, inventory, holder) }
            }
        }
    }

    private fun uuid(value: String): UUID? = runCatching { UUID.fromString(value) }.getOrNull()

    /** Player command bridge: same component whitelist, permissions and live home checks as the original menu. */
    fun performMenuAction(player: Player, component: String) {
        if (!allowed(player)) return
        val home = homes.byPlayer(player.uniqueId)
        performArcAction(player, if (home == null) HomeMenu.MAIN else HomeMenu.ISLAND, home?.id, component)
    }

    /** Arc routes accept component IDs only; the original YAML remains the action authority. */
    private fun performArcAction(player: Player, menu: HomeMenu, homeId: UUID?, component: String) {
        if (!allowed(player) || homes.byPlayer(player.uniqueId)?.id != homeId) return
        val allowedComponents = when (menu) {
            HomeMenu.MAIN -> setOf("create", "join", "help", "close")
            HomeMenu.ISLAND -> setOf("members", "settings", "help", "upgrade", "teleport", "spawn", "warehouse", "close")
            else -> return
        }
        if (component !in allowedComponents || (menu == HomeMenu.MAIN) != (homeId == null)) return
        val yaml = config.menus.getValue(menu)
        val action = yaml.getString("components.$component.action")?.takeIf(String::isNotBlank) ?: return
        val request = MenuRequest(menu)
        val home = homes.byPlayer(player.uniqueId)
        // Render an unopened original inventory to reuse the existing dispatch contract.
        val inventory = renderer.render(player, homeId, request, yaml, data.values(player, home, request), emptyList(), home?.flags?.toMap() ?: emptyMap())
        dispatch(player, inventory, inventory.holder as HomeMenuHolder, action)
    }

    /** Main-thread lifecycle cleanup, including configuration reload. */
    fun closeAll() {
        invero.closeAll()
        arc?.closeAll()
        plugin.server.onlinePlayers.filter { it.openInventory.topInventory.holder is HomeMenuHolder }.forEach(Player::closeInventory)
    }
}
