package ym.dreamSkyblock.listener

import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.entity.*
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.Cancellable
import org.bukkit.event.block.*
import org.bukkit.event.entity.*
import org.bukkit.event.inventory.InventoryOpenEvent
import org.bukkit.event.player.*
import org.bukkit.event.vehicle.VehicleEnterEvent
import org.bukkit.event.weather.ThunderChangeEvent
import org.bukkit.event.weather.WeatherChangeEvent
import org.bukkit.inventory.InventoryHolder
import ym.dreamSkyblock.model.Home
import ym.dreamSkyblock.model.HomeFlag
import ym.dreamSkyblock.service.HomeManager
import ym.dreamSkyblock.world.HomeWorldManager

class HomeProtectionListener(private val homes: HomeManager, private val worlds: HomeWorldManager) : Listener {
    private fun home(entity: Entity): Home? = worlds.homeByWorld(entity.world)?.let(homes::byId)
    private fun home(block: Block): Home? = worlds.homeByWorld(block.world)?.let(homes::byId)
    private fun stranger(player: Player, home: Home): Boolean =
        !home.isOwner(player.uniqueId) && !player.hasPermission("dreamskyblock.admin")
    private fun enabled(home: Home, flag: HomeFlag): Boolean = home.flags[flag] ?: flag.defaultValue
    private fun cancelIf(event: Cancellable, player: Player?, home: Home?, flag: HomeFlag) { if (player != null && home != null && stranger(player, home) && !enabled(home, flag)) event.isCancelled = true }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun place(event: BlockPlaceEvent) = cancelIf(event, event.player, home(event.block), HomeFlag.STRANGER_PLACE)
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun breakBlock(event: BlockBreakEvent) = cancelIf(event, event.player, home(event.block), HomeFlag.STRANGER_BREAK)
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun interact(event: PlayerInteractEvent) {
        val h = event.clickedBlock?.let(::home) ?: home(event.player) ?: return
        if (!stranger(event.player, h)) return
        val type = event.clickedBlock?.type
        val flag = when {
            type == null -> HomeFlag.STRANGER_BLOCK_INTERACT
            type.name.endsWith("DOOR") || type.name.endsWith("TRAPDOOR") || type.name.endsWith("FENCE_GATE") -> HomeFlag.STRANGER_DOOR
            type.name.endsWith("BUTTON") -> HomeFlag.STRANGER_BUTTON
            type.name.endsWith("PRESSURE_PLATE") -> HomeFlag.STRANGER_PRESSURE_PLATE
            type.name.contains("CHEST") || type.name.contains("BARREL") || type.name.contains("SHULKER") -> HomeFlag.STRANGER_CONTAINER
            event.item?.type?.name?.contains("BUCKET") == true -> HomeFlag.STRANGER_BUCKET
            else -> HomeFlag.STRANGER_BLOCK_INTERACT
        }
        if (!enabled(h, flag)) event.isCancelled = true
    }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun entityInteract(event: PlayerInteractEntityEvent) {
        val h = home(event.rightClicked) ?: return
        if (stranger(event.player, h) && !enabled(h, HomeFlag.STRANGER_ENTITY_INTERACT)) event.isCancelled = true
    }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun inventory(event: InventoryOpenEvent) { val player = event.player as? Player ?: return; val h = home(player) ?: return; if (stranger(player, h) && !enabled(h, HomeFlag.STRANGER_CONTAINER)) event.isCancelled = true }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun damage(event: EntityDamageByEntityEvent) {
        val damager = event.damager as? Player ?: return; val h = home(event.entity) ?: return
        if (event.entity is Player && !enabled(h, HomeFlag.PVP)) { event.isCancelled = true; return }
        if (!stranger(damager, h)) return
        val flag = if (event.entity is Animals) HomeFlag.STRANGER_ATTACK_ANIMAL else HomeFlag.STRANGER_ATTACK_MONSTER
        if (!enabled(h, flag)) event.isCancelled = true
    }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun pickup(event: EntityPickupItemEvent) { val p = event.entity as? Player ?: return; val h = home(p) ?: return; if (stranger(p, h) && !enabled(h, HomeFlag.STRANGER_PICKUP)) event.isCancelled = true }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun drop(event: PlayerDropItemEvent) { val h = home(event.player) ?: return; cancelIf(event, event.player, h, HomeFlag.STRANGER_DROP) }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun vehicle(event: VehicleEnterEvent) { val p = event.entered as? Player ?: return; val h = home(p) ?: return; cancelIf(event, p, h, HomeFlag.STRANGER_VEHICLE) }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun spawn(event: CreatureSpawnEvent) { val h = home(event.entity) ?: return; if ((event.entity is Monster && !enabled(h, HomeFlag.MONSTER_SPAWN)) || (event.entity is Animals && !enabled(h, HomeFlag.ANIMAL_SPAWN))) event.isCancelled = true }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun entityGrief(event: EntityChangeBlockEvent) { val h = home(event.entity) ?: return; if (!enabled(h, HomeFlag.ENTITY_GRIEF)) event.isCancelled = true }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun explode(event: EntityExplodeEvent) { val h = event.location.world.let { worlds.homeByWorld(it)?.let(homes::byId) } ?: return; if (!enabled(h, HomeFlag.TNT_EXPLOSION)) event.blockList().clear() }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun ignite(event: BlockIgniteEvent) { val h = home(event.block) ?: return; if (!enabled(h, HomeFlag.FIRE_SPREAD)) event.isCancelled = true }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun flow(event: BlockFromToEvent) { val h = home(event.block) ?: return; if (!enabled(h, HomeFlag.LIQUID_FLOW)) event.isCancelled = true }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun weather(event: WeatherChangeEvent) { val h = worlds.homeByWorld(event.world)?.let(homes::byId) ?: return; if (!enabled(h, HomeFlag.WEATHER)) event.isCancelled = true }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun thunder(event: ThunderChangeEvent) { val h = worlds.homeByWorld(event.world)?.let(homes::byId) ?: return; if (!enabled(h, HomeFlag.WEATHER)) event.isCancelled = true }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun portal(event: PlayerPortalEvent) { val h = home(event.player) ?: return; if ((event.cause == PlayerTeleportEvent.TeleportCause.NETHER_PORTAL && !enabled(h, HomeFlag.PORTAL_NETHER)) || (event.cause == PlayerTeleportEvent.TeleportCause.END_PORTAL && !enabled(h, HomeFlag.PORTAL_END))) event.isCancelled = true }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun portalCreate(event: org.bukkit.event.world.PortalCreateEvent) { val h = worlds.homeByWorld(event.world)?.let(homes::byId) ?: return; if (!enabled(h, HomeFlag.PORTAL_NETHER)) event.isCancelled = true }
    @EventHandler
    fun teleport(event: PlayerTeleportEvent) {
        val fromHome = worlds.homeByWorld(event.from.world)
        val toHome = event.to.world?.let(worlds::homeByWorld)
        if (fromHome != null && toHome == null) event.player.allowFlight = false
        if (toHome != null) homes.byId(toHome)?.let { event.player.allowFlight = enabled(it, HomeFlag.FLIGHT) }
    }
}
