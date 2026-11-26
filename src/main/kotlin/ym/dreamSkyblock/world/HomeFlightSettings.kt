package ym.dreamSkyblock.world

import org.bukkit.GameMode
import org.bukkit.entity.Player

/** 在玩家所属线程应用；岛屿开关不覆盖创造和旁观模式自带的飞行能力。 */
object HomeFlightSettings {
    fun apply(player: Player, islandFlight: Boolean) {
        val allowed = islandFlight || player.gameMode == GameMode.CREATIVE || player.gameMode == GameMode.SPECTATOR
        if (!allowed && player.isFlying) player.isFlying = false
        player.allowFlight = allowed
    }
}
