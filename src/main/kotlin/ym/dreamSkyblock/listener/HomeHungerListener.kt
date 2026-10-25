package ym.dreamSkyblock.listener

import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityExhaustionEvent
import org.bukkit.event.entity.FoodLevelChangeEvent

/** Paper main-thread events; protects all players in managed home worlds without changing food elsewhere. */
class HomeHungerListener(private val isHomeWorld: (World) -> Boolean) : Listener {
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun exhaust(event: EntityExhaustionEvent) {
        val player = event.entity as? Player ?: return
        if (isHomeWorld(player.world)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun food(event: FoodLevelChangeEvent) {
        val player = event.entity as? Player ?: return
        if (event.foodLevel < player.foodLevel && isHomeWorld(player.world)) event.isCancelled = true
    }
}
