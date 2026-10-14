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
        leaves.isPersistent = true
        leaves.distance = 1
        for (dy in 2..4) {
            val radius = if (dy == 4) 1 else 2
            for (dx in -radius..radius) for (dz in -radius..radius) {
                if (dy == 2 && kotlin.math.abs(dx) == 2 && kotlin.math.abs(dz) == 2) continue
                if (dx == 0 && dz == 0 && dy < 4) continue
                world.getBlockAt(tx + dx, ty + dy, tz + dz).setBlockData(leaves.clone(), false)
            }
        }
    }
}
