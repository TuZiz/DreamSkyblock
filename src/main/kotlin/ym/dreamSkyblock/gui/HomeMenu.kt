package ym.dreamSkyblock.gui

import org.bukkit.Material
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import java.util.UUID

enum class HomeMenu(val file: String) {
    MAIN("main"), ISLAND("island"), CONFIRM("create_confirm"),
    INVITES("invites"), MEMBERS("members"), PLAYERS("invite_players"), MEMBER_CONFIRM("member_confirm"),
    SETTINGS("settings"), WORLD_RULES("world_rules"), VISITOR_PERMISSIONS("visitor_permissions"), HELP("help")
}

data class MenuRequest(val menu: HomeMenu, val page: Int = 0, val template: String? = null, val member: UUID? = null)
data class MenuEntry(val values: Map<String, String>, val material: Material? = null)

/** Inventory identity is the session; no cross-reload or per-player global session cache. */
class HomeMenuHolder(val playerId: UUID, val homeId: UUID?, request: MenuRequest) : InventoryHolder {
    var request: MenuRequest = request
        private set
    var revision: Long = 0
        private set
    val actions = mutableMapOf<Int, String>()
    private lateinit var inventory: Inventory
    fun bind(inventory: Inventory) { this.inventory = inventory }
    fun update(request: MenuRequest, actions: Map<Int, String>) {
        require(request.menu == this.request.menu) { "局部更新不能切换菜单" }
        this.request = request
        this.actions.clear()
        this.actions.putAll(actions)
        revision++
    }
    override fun getInventory(): Inventory = inventory
}
