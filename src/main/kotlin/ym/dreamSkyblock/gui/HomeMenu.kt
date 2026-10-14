package ym.dreamSkyblock.gui

import org.bukkit.Material
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import java.util.UUID

enum class HomeMenu(val file: String) {
    MAIN("main"), ISLAND("island"), CONFIRM("create_confirm"),
    INVITES("invites"), MEMBERS("members"), PLAYERS("invite_players"), MEMBER_CONFIRM("member_confirm"),
    SETTINGS("settings"), HELP("help")
}

data class MenuRequest(val menu: HomeMenu, val page: Int = 0, val template: String? = null, val member: UUID? = null)
data class MenuEntry(val values: Map<String, String>, val material: Material? = null)

/** Inventory identity is the session; no cross-reload or per-player global session cache. */
class HomeMenuHolder(val playerId: UUID, val homeId: UUID?, val request: MenuRequest) : InventoryHolder {
    val actions = mutableMapOf<Int, String>()
    private lateinit var inventory: Inventory
    fun bind(inventory: Inventory) { this.inventory = inventory }
    override fun getInventory(): Inventory = inventory
}
