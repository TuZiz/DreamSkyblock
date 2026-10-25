package ym.dreamSkyblock.listener

import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.event.entity.EntityExhaustionEvent
import org.bukkit.event.entity.FoodLevelChangeEvent
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy

class HomeHungerListenerTest {
    private val world = Proxy.newProxyInstance(World::class.java.classLoader, arrayOf(World::class.java)) { _, method, _ ->
        error("Unexpected world access: ${method.name}")
    } as World
    private val player = Proxy.newProxyInstance(Player::class.java.classLoader, arrayOf(Player::class.java)) { _, method, _ ->
        when (method.name) {
            "getWorld" -> world
            "getFoodLevel" -> 16
            else -> error("Unexpected player access: ${method.name}")
        }
    } as Player

    @Test fun `home worlds block depletion and exhaustion but allow eating`() {
        val listener = HomeHungerListener { it === world }
        val depletion = FoodLevelChangeEvent(player, 15)
        listener.food(depletion)
        assertTrue(depletion.isCancelled)
        val eating = FoodLevelChangeEvent(player, 20)
        listener.food(eating)
        assertFalse(eating.isCancelled)
        val exhaustion = EntityExhaustionEvent(player, EntityExhaustionEvent.ExhaustionReason.values().first(), 1f)
        listener.exhaust(exhaustion)
        assertTrue(exhaustion.isCancelled)
    }

    @Test fun `other worlds retain normal hunger and exhaustion`() {
        val listener = HomeHungerListener { false }
        val depletion = FoodLevelChangeEvent(player, 15)
        listener.food(depletion)
        assertFalse(depletion.isCancelled)
        val exhaustion = EntityExhaustionEvent(player, EntityExhaustionEvent.ExhaustionReason.values().first(), 1f)
        listener.exhaust(exhaustion)
        assertFalse(exhaustion.isCancelled)
    }
}
