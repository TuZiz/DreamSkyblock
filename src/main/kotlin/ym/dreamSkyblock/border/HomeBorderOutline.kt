package ym.dreamSkyblock.border

import kotlin.math.abs

/** Pure geometry: a short outline at the player's height, never a whole world-sized particle wall. */
object HomeBorderOutline {
    data class Point(val x: Double, val y: Double, val z: Double)

    fun points(centerX: Double, centerZ: Double, size: Double, x: Double, y: Double, z: Double): List<Point> {
        val half = size.coerceIn(16.0, 384.0) / 2
        val minX = centerX - half
        val maxX = centerX + half
        val minZ = centerZ - half
        val maxZ = centerZ + half
        val result = mutableListOf<Point>()
        for (edge in listOf(minX, maxX)) {
            if (abs(x - edge) > 3.0 || z < minZ - 3 || z > maxZ + 3) continue
            for (offset in -4..4) {
                val along = z + offset
                if (along in minZ..maxZ) for (height in 0..2) result += Point(edge, y + height + .25, along)
            }
        }
        for (edge in listOf(minZ, maxZ)) {
            if (abs(z - edge) > 3.0 || x < minX - 3 || x > maxX + 3) continue
            for (offset in -4..4) {
                val along = x + offset
                if (along in minX..maxX) for (height in 0..2) result += Point(along, y + height + .25, edge)
            }
        }
        return result.distinct()
    }
}
