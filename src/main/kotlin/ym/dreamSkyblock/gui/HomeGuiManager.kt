package ym.dreamSkyblock.gui

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import ym.dreamSkyblock.DreamSkyblock
import ym.dreamSkyblock.config.ConfigService
import ym.dreamSkyblock.model.HomeFlag
import ym.dreamSkyblock.service.HomeManager
import ym.dreamSkyblock.text.MessageService
import java.util.UUID

private class HomeMenuHolder(val playerId: UUID, val actions: MutableMap<Int, String>) : InventoryHolder {
    private lateinit var inventory: Inventory
    fun bind(inventory: Inventory) { this.inventory = inventory }
    override fun getInventory(): Inventory = inventory
}

class HomeGuiManager(
    private val plugin: DreamSkyblock,
    private val config: ConfigService,
    private val homes: HomeManager,
    private val messages: MessageService
) {
    fun openTemplate(player: Player) {
        val yaml = config.templateGui
        val inventory = createInventory(player.uniqueId, yaml) { action ->
            val templateId = action.removePrefix("create:")
            mapOf("name" to (config.templates[templateId]?.displayName ?: templateId))
        }
        player.openInventory(inventory)
    }

    fun openSettings(player: Player) {
        val home = homes.byPlayer(player.uniqueId) ?: return messages.send(player, "home.none")
        if (!home.isOwner(player.uniqueId)) return messages.send(player, "home.not-owner")
        val yaml = config.settingsGui
        val statusOn = yaml.getString("status.enabled", "<#55FF55>开启")!!
        val statusOff = yaml.getString("status.disabled", "<#FF5555>关闭")!!
        val inventory = createInventory(player.uniqueId, yaml) { action ->
            val flag = runCatching { HomeFlag.valueOf(action.removePrefix("toggle:").uppercase()) }.getOrNull()
            val enabled = flag?.let { home.flags[it] ?: it.defaultValue } ?: false
            mapOf("status" to if (enabled) statusOn else statusOff)
        }
        player.openInventory(inventory)
    }

    private fun createInventory(playerId: UUID, yaml: org.bukkit.configuration.file.YamlConfiguration, placeholders: (String) -> Map<String, String>): Inventory {
        val shape = yaml.getStringList("Shape")
        require(shape.isNotEmpty() && shape.size <= 6 && shape.all { it.length == 9 }) { "GUI Shape 必须为 1-6 行且每行 9 个字符" }
        val title = yaml.getString("title") ?: error("GUI 缺少 title 配置")
        val holder = HomeMenuHolder(playerId, mutableMapOf())
        val inventory = Bukkit.createInventory(holder, shape.size * 9, messages.componentRaw(title))
        holder.bind(inventory)
        shape.forEachIndexed { row, line ->
            line.forEachIndexed { column, symbol ->
                if (symbol == ' ') return@forEachIndexed
                val component = yaml.getConfigurationSection("components")?.getKeys(false)?.firstNotNullOfOrNull { id ->
                    yaml.getString("components.$id.symbol")?.singleOrNull()?.takeIf { it == symbol }?.let { id }
                } ?: if (yaml.isConfigurationSection("components.filler")) "filler" else return@forEachIndexed
                val section = yaml.getConfigurationSection("components.$component") ?: error("GUI 组件不存在: $component")
                val action = section.getString("action") ?: ""
                putComponent(inventory, section, action, placeholders(action), row * 9 + column)
            }
        }
        return inventory
    }

    private fun putComponent(
        inventory: Inventory,
        section: org.bukkit.configuration.ConfigurationSection,
        action: String,
        placeholders: Map<String, String>,
        forcedSlot: Int? = null
    ) {
        val slot = forcedSlot ?: section.getInt("slot", -1)
        if (slot !in 0 until inventory.size) return
        val material = Material.matchMaterial(section.getString("material", "STONE")!!) ?: Material.STONE
        val item = ItemStack(material)
        val meta = item.itemMeta
        section.getString("custom-model-data")?.toIntOrNull()?.let(meta::setCustomModelData)
        meta.itemName(messages.componentRaw(section.getString("name", "<#A1B2C3> ")!!, placeholders))
        meta.lore(messages.listRaw(section.getStringList("lore"), placeholders))
        item.itemMeta = meta
        inventory.setItem(slot, item)
        (inventory.holder as HomeMenuHolder).actions[slot] = action
    }

    fun click(event: InventoryClickEvent) {
        val holder = event.view.topInventory.holder as? HomeMenuHolder ?: return
        event.isCancelled = true
        val action = holder.actions[event.rawSlot] ?: return
        val player = event.whoClicked as? Player ?: return
        when {
            action == "close" -> player.closeInventory()
            action.startsWith("create:") -> {
                player.closeInventory()
                homes.create(player, action.removePrefix("create:"))
            }
            action.startsWith("toggle:") -> {
                val home = homes.byPlayer(player.uniqueId) ?: return
                val flag = runCatching { HomeFlag.valueOf(action.removePrefix("toggle:").uppercase()) }.getOrNull() ?: return
                if (homes.toggleFlag(player, flag)) openSettings(player)
            }
        }
    }

    fun close(event: InventoryCloseEvent) {
        if (event.inventory.holder is HomeMenuHolder) return
    }
}
