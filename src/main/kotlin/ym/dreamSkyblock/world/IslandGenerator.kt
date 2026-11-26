package ym.dreamSkyblock.world

import org.bukkit.Material
import org.bukkit.World
import org.bukkit.block.data.type.Leaves
import ym.dreamSkyblock.model.HomeTemplate

/** 首次创建使用。Canvas 由每个区块的区域线程写入初始平台。 */
object IslandGenerator {
    fun chunks(template: HomeTemplate): Set<Pair<Int, Int>> {
        val x = template.center.x.toInt()
        val z = template.center.z.toInt()
        val half = template.platformSize / 2
        return buildSet {
            for (cx in ((x - half) shr 4)..((x + half) shr 4))
                for (cz in ((z - half) shr 4)..((z + half) shr 4)) add(cx to cz)
            StarterTreeLeaves.canopy(template).forEach { (lx, _, lz) -> add((lx shr 4) to (lz shr 4)) }
            add(((x + 2) shr 4) to ((z + 2) shr 4))
        }
    }

    fun generate(world: World, template: HomeTemplate) = write(world, template, true) { _, _ -> true }

    fun generateChunk(world: World, template: HomeTemplate, cx: Int, cz: Int) =
        write(world, template, false) { x, z -> x shr 4 == cx && z shr 4 == cz }

    private fun write(world: World, template: HomeTemplate, physics: Boolean, owns: (Int, Int) -> Boolean) {
        val x = template.center.x.toInt()
        val y = template.platformY
        val z = template.center.z.toInt()
        val half = template.platformSize / 2
        for (dx in -half..half) for (dz in -half..half) {
            if (!owns(x + dx, z + dz)) continue
            world.getBlockAt(x + dx, y - 1, z + dz).setType(Material.DIRT, physics)
            world.getBlockAt(x + dx, y, z + dz).setType(Material.GRASS_BLOCK, physics)
        }
        if (owns(x, z)) world.getBlockAt(x, y - 2, z).setType(Material.BEDROCK, physics)
        val tx = x + 2
        val ty = y + 1
        val tz = z + 2
        if (owns(tx, tz)) for (dy in 0..3) world.getBlockAt(tx, ty + dy, tz).setType(Material.OAK_LOG, false)
        val leaves = Material.OAK_LEAVES.createBlockData() as Leaves
        leaves.isPersistent = false
        for ((lx, ly, lz) in StarterTreeLeaves.canopy(template)) {
            if (!owns(lx, lz)) continue
            val data = leaves.clone() as Leaves
            data.distance = kotlin.math.abs(lx - tx) + kotlin.math.abs(lz - tz) + (ly - (ty + 3)).coerceAtLeast(0)
            world.getBlockAt(lx, ly, lz).setBlockData(data, physics)
        }
    }
}
