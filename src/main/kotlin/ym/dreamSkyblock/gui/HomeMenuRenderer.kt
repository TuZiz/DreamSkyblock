package ym.dreamSkyblock.gui

import org.bukkit.Bukkit
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.Material
import ym.dreamSkyblock.model.HomeFlag
import ym.dreamSkyblock.text.MessageService
import java.util.UUID

/** Main thread only; renders immutable menu data, never performs IO or business mutations. */
class HomeMenuRenderer(private val messages: MessageService) {
    fun render(player: Player, homeId: UUID?, request: MenuRequest, yaml: YamlConfiguration,
               values: Map<String, String>, entries: List<MenuEntry>, flags: Map<HomeFlag, Boolean>): Inventory {
        val content = content(request, yaml, values, entries, flags)
        val holder = HomeMenuHolder(player.uniqueId, homeId, content.request)
        val inventory = Bukkit.createInventory(holder, content.items.size, messages.componentRaw(yaml.getString("title")!!, content.values))
        holder.bind(inventory)
        apply(inventory, holder, content)
        return inventory
    }

    /** Keeps the open container; only changed slots are written, including slots that became empty. */
    fun refresh(inventory: Inventory, request: MenuRequest, yaml: YamlConfiguration,
                values: Map<String, String>, entries: List<MenuEntry>, flags: Map<HomeFlag, Boolean>) {
        val holder = inventory.holder as? HomeMenuHolder ?: error("不是空岛菜单")
        require(holder.request.menu == request.menu) { "局部更新不能切换菜单" }
        val content = content(request, yaml, values, entries, flags)
        require(inventory.size == content.items.size) { "菜单布局大小变化，需要重新打开" }
        apply(inventory, holder, content)
    }

    private fun apply(inventory: Inventory, holder: HomeMenuHolder, content: Content) {
        content.items.forEachIndexed { slot, item ->
            if (inventory.getItem(slot) != item) inventory.setItem(slot, item)
        }
        // Page and actions change together on the main thread, even if two entries look identical.
        holder.update(content.request, content.actions)
    }

    private data class Content(val request: MenuRequest, val values: Map<String, String>,
                               val items: Array<ItemStack?>, val actions: Map<Int, String>)

    private fun content(request: MenuRequest, yaml: YamlConfiguration, values: Map<String, String>,
                        entries: List<MenuEntry>, flags: Map<HomeFlag, Boolean>): Content {
        val shape = yaml.getStringList("Shape")
        val sections = yaml.getConfigurationSection("components")!!.getKeys(false).associate { id ->
            val section = yaml.getConfigurationSection("components.$id")!!
            section.getString("symbol")!!.single() to section
        }
        val repeated = shape.joinToString("").count { sections[it]?.contains("repeat") == true }
        val pages = if (repeated == 0) 1 else ((entries.size + repeated - 1) / repeated).coerceAtLeast(1)
        val page = request.page.coerceIn(0, pages - 1)
        val common = values + mapOf("page" to (page + 1).toString(), "pages" to pages.toString())
        val items = arrayOfNulls<ItemStack>(shape.size * 9)
        val actions = mutableMapOf<Int, String>()
        var index = 0
        shape.joinToString("").forEachIndexed { slot, symbol ->
            if (symbol == ' ') return@forEachIndexed
            val section = sections[symbol] ?: return@forEachIndexed
            val entry = if (section.contains("repeat")) entries.getOrNull(page * repeated + index++) else null
            if (section.contains("repeat") && entry == null) return@forEachIndexed
            val configuredAction = section.getString("action", "")!!
            val flag = HomeFlag.entries.firstOrNull { configuredAction.equals("toggle:${it.name.lowercase()}", true) }
            val status = flag?.let { yaml.getString(if (flags[it] ?: it.defaultValue) "status.enabled" else "status.disabled")!! }
            val placeholders = common + (entry?.values ?: emptyMap()) + (status?.let { mapOf("status" to it) } ?: emptyMap())
            var action = section.getString("action", "")!!
            placeholders.forEach { (key, value) -> action = action.replace("<$key>", value) }
            if ((action == "previous" && page == 0) || (action == "next" && page == pages - 1)) action = ""
            items[slot] = item(section, entry?.material, placeholders)
            actions[slot] = action
        }
        return Content(request.copy(page = page), common, items, actions)
    }

    private fun item(section: ConfigurationSection, material: Material?, values: Map<String, String>): ItemStack {
        val item = ItemStack(material ?: Material.matchMaterial(section.getString("material")!!)!!)
        item.itemMeta = item.itemMeta.apply {
            itemName(messages.componentRaw(section.getString("name")!!, values))
            lore(messages.listRaw(section.getStringList("lore"), values))
            section.getString("custom-model-data")?.toIntOrNull()?.let(::setCustomModelData)
        }
        return item
    }
}
