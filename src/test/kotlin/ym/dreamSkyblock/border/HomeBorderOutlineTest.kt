package ym.dreamSkyblock.border

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class HomeBorderOutlineTest {
    @Test fun `only nearby edges show particles and all four sides are supported`() {
        assertTrue(HomeBorderOutline.points(0.0, 0.0, 48.0, 0.0, 100.0, 0.0).isEmpty())
        for (sign in listOf(-1, 1)) {
            val eastWest = HomeBorderOutline.points(0.0, 0.0, 48.0, sign * 22.0, 100.0, 0.0)
            assertEquals(27, eastWest.size)
            assertTrue(eastWest.all { it.x == sign * 24.0 && it.z in -4.0..4.0 })
            val northSouth = HomeBorderOutline.points(0.0, 0.0, 48.0, 0.0, 100.0, sign * 22.0)
            assertEquals(27, northSouth.size)
            assertTrue(northSouth.all { it.z == sign * 24.0 && it.x in -4.0..4.0 })
        }
    }

    @Test fun `corners are clipped to the island and particle work stays bounded`() {
        val points = HomeBorderOutline.points(0.0, 0.0, 48.0, 23.5, 200.0, 23.5)
        assertTrue(points.isNotEmpty() && points.size <= 54)
        assertEquals(points.size, points.distinct().size)
        assertTrue(points.all { it.x in -24.0..24.0 && it.z in -24.0..24.0 && it.y in 200.25..202.25 })
        assertTrue(points.any { it.x == 24.0 })
        assertTrue(points.any { it.z == 24.0 })
    }

    @Test fun `upgrading moves the outline without retaining the old boundary`() {
        assertFalse(HomeBorderOutline.points(0.0, 0.0, 48.0, 23.0, 100.0, 0.0).isEmpty())
        assertTrue(HomeBorderOutline.points(0.0, 0.0, 58.0, 23.0, 100.0, 0.0).isEmpty())
        assertTrue(HomeBorderOutline.points(0.0, 0.0, 384.0, 190.0, 100.0, 0.0).all { it.x == 192.0 })
    }

    @Test fun `custom centers and approach distance are respected`() {
        val nearby = HomeBorderOutline.points(100.0, -80.0, 48.0, 121.0, -20.0, -80.0)
        assertEquals(27, nearby.size)
        assertTrue(nearby.all { it.x == 124.0 && it.y in -19.75..-17.75 })
        assertTrue(HomeBorderOutline.points(100.0, -80.0, 48.0, 120.9, -20.0, -80.0).isEmpty())
        assertTrue(HomeBorderOutline.points(100.0, -80.0, 48.0, 124.0, -20.0, 900.0).isEmpty())
    }
}
