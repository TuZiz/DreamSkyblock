package ym.dreamSkyblock.world

import org.bukkit.GameRule
import org.bukkit.World
import org.bukkit.plugin.Plugin
import ym.dreamSkyblock.model.Home
import ym.dreamSkyblock.model.HomeFlag
import ym.dreamSkyblock.model.HomeTemplate

/** 只在 Paper 主线程应用设置；升级只改变边界，不生成或替换方块。 */
object HomeWorldSettings {
    fun apply(plugin: Plugin, world: World, home: Home, template: HomeTemplate, fresh: Boolean) {
        world.difficulty = template.difficulty
        flags(plugin, world, home)
        if (fresh) world.setGameRule(GameRule.KEEP_INVENTORY, template.keepInventory)
        world.setGameRule(GameRule.NATURAL_REGENERATION, template.naturalRegeneration)
        world.worldBorder.setCenter(home.center.x, home.center.z)
        world.worldBorder.size = home.borderSize.coerceIn(16.0, 384.0)
        val spawn = home.spawn ?: template.spawn
        world.setSpawnLocation(spawn.x.toInt(), spawn.y.toInt(), spawn.z.toInt(), spawn.yaw)
    }

    fun flags(plugin: Plugin, world: World, home: Home) {
        fun value(flag: HomeFlag) = home.flags[flag] ?: flag.defaultValue
        world.setPVP(value(HomeFlag.PVP))
        world.setSpawnFlags(value(HomeFlag.ANIMAL_SPAWN), value(HomeFlag.MONSTER_SPAWN))
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, value(HomeFlag.WEATHER))
        // Internal vanilla rate: leaves decay and crops grow normally; not exposed as a player flag.
        world.setGameRule(GameRule.RANDOM_TICK_SPEED, 3)
        world.players.forEach { player ->
            player.scheduler.run(plugin, { if (player.world == world) HomeFlightSettings.apply(player, value(HomeFlag.FLIGHT)) }, null)
        }
        world.worldBorder.size = home.borderSize.coerceIn(16.0, 384.0)
    }
}
