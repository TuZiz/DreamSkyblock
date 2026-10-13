package ym.dreamSkyblock.model

import org.bukkit.Material
import org.bukkit.World

data class HomeTemplate(
    val id: String,
    val displayName: String,
    val description: List<String>,
    val icon: Material,
    val environment: World.Environment,
    val generator: GeneratorType,
    val center: Point,
    val spawn: Point,
    val borderSize: Double,
    val difficulty: org.bukkit.Difficulty,
    val animals: Boolean,
    val monsters: Boolean,
    val pvp: Boolean,
    val keepInventory: Boolean,
    val flight: Boolean,
    val weather: Boolean,
    val naturalRegeneration: Boolean,
    val netherPortal: Boolean,
    val endPortal: Boolean,
    val flags: Map<HomeFlag, Boolean>,
    val strangerFlags: Map<HomeFlag, Boolean>,
    val platformSize: Int,
    val platformY: Int
) {
    enum class GeneratorType { SKYBLOCK, VANILLA }
    data class Point(val x: Double, val y: Double, val z: Double, val yaw: Float = 0f, val pitch: Float = 0f)
}
