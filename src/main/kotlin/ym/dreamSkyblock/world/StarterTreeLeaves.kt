package ym.dreamSkyblock.world

import org.bukkit.Chunk
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.World
import org.bukkit.block.data.type.Leaves
import org.bukkit.persistence.PersistentDataType
import ym.dreamSkyblock.model.HomeTemplate
import kotlin.math.abs

/** Paper main thread only. Inspect the original canopy footprint, never scan/load other chunks. */
object StarterTreeLeaves {
    private val repaired = NamespacedKey("dreamskyblock", "starter_leaves_v1")

    fun canopy(template: HomeTemplate): List<Triple<Int, Int, Int>> = buildList {
        for (dy in 2..4) {
            val radius = if (dy == 4) 1 else 2
            for (dx in -radius..radius) for (dz in -radius..radius) {
                if (dy == 2 && abs(dx) == 2 && abs(dz) == 2) continue
                if (dx == 0 && dz == 0 && dy < 4) continue
                add(Triple(template.center.x.toInt() + 2 + dx, template.platformY + 1 + dy, template.center.z.toInt() + 2 + dz))
            }
        }
    }

    fun repairLoaded(world: World, template: HomeTemplate) {
        if (template.generator != HomeTemplate.GeneratorType.SKYBLOCK) return
        canopy(template).map { (x, _, z) -> (x shr 4) to (z shr 4) }.distinct().forEach { (x, z) ->
            if (world.isChunkLoaded(x, z)) repair(world.getChunkAt(x, z), template)
        }
    }

    fun repair(chunk: Chunk, template: HomeTemplate) {
        if (template.generator != HomeTemplate.GeneratorType.SKYBLOCK || chunk.persistentDataContainer.has(repaired)) return
        val positions = canopy(template).filter { (x, _, z) -> x shr 4 == chunk.x && z shr 4 == chunk.z }
        if (positions.isEmpty()) return
        positions.forEach { (x, y, z) ->
            val block = chunk.getBlock(x and 15, y, z and 15)
            val data = block.blockData as? Leaves ?: return@forEach
            // Only the original oak canopy footprint. Decorations outside it and other leaf types stay intact.
            if (block.type == Material.OAK_LEAVES && data.isPersistent) {
                data.isPersistent = false
                data.distance = data.maximumDistance
                block.setBlockData(data, true) // Let vanilla recompute connected-log distance before random decay.
            }
        }
        chunk.persistentDataContainer.set(repaired, PersistentDataType.BYTE, 1.toByte())
    }
}
