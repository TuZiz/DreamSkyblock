package ym.dreamSkyblock.world

import org.bukkit.Material
import org.bukkit.World
import org.bukkit.block.data.type.Leaves
import ym.dreamSkyblock.model.HomeTemplate

/** 只用于首次创建，在 Paper 主线程写入初始平台。恢复、加载、升级均不调用。 */
object IslandGenerator {
    fun generate(world: World, template: HomeTemplate) {
        val x = template.center.x.toInt()
        val y = template.platformY
        val z = template.center.z.toInt()
        val half = template.platformSize / 2
        for (dx in -half..half) for (dz in -half..half) {
            world.getBlockAt(x + dx, y - 1, z + dz).type = Material.DIRT
            world.getBlockAt(x + dx, y, z + dz).type = Material.GRASS_BLOCK
        }
        world.getBlockAt(x, y - 2, z).type = Material.BEDROCK
        val tx = x + 2
        val ty = y + 1
        val tz = z + 2
        for (dy in 0..3) world.getBlockAt(tx, ty + dy, tz).setType(Material.OAK_LOG, false)
        val leaves = Material.OAK_LEAVES.createBlockData() as Leaves
        leaves.isPersistent = false
        for ((lx, ly, lz) in StarterTreeLeaves.canopy(template)) {
            val data = leaves.clone() as Leaves
            data.distance = kotlin.math.abs(lx - tx) + kotlin.math.abs(lz - tz) + (ly - (ty + 3)).coerceAtLeast(0)
            world.getBlockAt(lx, ly, lz).setBlockData(data, true)
        }
    }
}
