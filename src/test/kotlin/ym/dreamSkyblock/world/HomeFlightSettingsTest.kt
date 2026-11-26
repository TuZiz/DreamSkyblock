package ym.dreamSkyblock.world

import org.bukkit.GameMode
import org.bukkit.entity.Player
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy

class HomeFlightSettingsTest {
    private class FlightPlayer(val mode: GameMode, var allowed: Boolean, var flying: Boolean) {
        val player = Proxy.newProxyInstance(Player::class.java.classLoader, arrayOf(Player::class.java)) { _, method, args ->
            when (method.name) {
                "getGameMode" -> mode
                "getAllowFlight" -> allowed
                "isFlying" -> flying
                "setAllowFlight" -> { allowed = args[0] as Boolean; null }
                "setFlying" -> { flying = args[0] as Boolean; null }
                else -> error(method.name)
            }
        } as Player
    }

    @Test fun `creative and spectator regain flight after leaving island`() {
        for (mode in listOf(GameMode.CREATIVE, GameMode.SPECTATOR)) {
            val state = FlightPlayer(mode, false, false)
            HomeFlightSettings.apply(state.player, false)
            assertTrue(state.allowed, mode.name)
            assertFalse(state.flying, "Do not force a grounded player airborne")
        }
    }

    @Test fun `island settings cannot stop native flying modes`() {
        for (mode in listOf(GameMode.CREATIVE, GameMode.SPECTATOR)) {
            val state = FlightPlayer(mode, true, true)
            HomeFlightSettings.apply(state.player, false)
            assertTrue(state.allowed)
            assertTrue(state.flying)
        }
    }

    @Test fun `survival and adventure lose island flight on exit`() {
        for (mode in listOf(GameMode.SURVIVAL, GameMode.ADVENTURE)) {
            val state = FlightPlayer(mode, false, false)
            HomeFlightSettings.apply(state.player, true)
            assertTrue(state.allowed)
            state.flying = true
            HomeFlightSettings.apply(state.player, false)
            assertFalse(state.allowed)
            assertFalse(state.flying)
        }
    }
}
