package ym.dreamSkyblock.listener

import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.damage.DamageSource
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy
import java.util.UUID

class HomeVoidListenerTest {
    private val id = UUID.randomUUID()
    private val world = Proxy.newProxyInstance(World::class.java.classLoader, arrayOf(World::class.java)) { p, m, a ->
        when (m.name) {
            "getMinHeight" -> -64
            "getMaxHeight" -> 320
            "equals" -> p === a[0]
            "hashCode" -> 1
            else -> error(m.name)
        }
    } as World
    private var ground = false
    private var reset = false
    private var teleportOk = true
    private var destination: Location? = null
    private val player = Proxy.newProxyInstance(Player::class.java.classLoader, arrayOf(Player::class.java)) { _, m, args ->
        when (m.name) {
            "getUniqueId" -> id
            "getWorld" -> world
            "getLocation" -> Location(world, 12.5, -130.0, -7.25)
            "teleport" -> { destination = args[0] as Location; teleportOk }
            "setFallDistance", "setVelocity" -> { reset = true; null }
            "isOnGround" -> ground
            "isFlying", "isGliding", "isInWater" -> false
            else -> error(m.name)
        }
    } as Player
    private fun move(y: Double) = PlayerMoveEvent(player, Location(world, 12.5, y + 1, -7.25), Location(world, 12.5, y, -7.25, 80f, 15f))
    private fun sessions(listener: HomeVoidListener): Map<*, *> = HomeVoidListener::class.java.getDeclaredField("descents").apply { isAccessible = true }.get(listener) as Map<*, *>
    private fun damage(cause: EntityDamageEvent.DamageCause): EntityDamageEvent {
        val source = Proxy.newProxyInstance(DamageSource::class.java.classLoader, arrayOf(DamageSource::class.java)) { _, m, _ -> error(m.name) } as DamageSource
        return EntityDamageEvent(player, cause, source, 20.0)
    }

    @Test fun `rescue protects one fall but does not grant permanent damage immunity`() {
        val listener = HomeVoidListener { true }
        listener.move(move(-73.0))
        val fire = damage(EntityDamageEvent.DamageCause.FIRE); listener.damage(fire); assertFalse(fire.isCancelled)
        val fall = damage(EntityDamageEvent.DamageCause.FALL); listener.damage(fall); assertTrue(fall.isCancelled)
        val next = damage(EntityDamageEvent.DamageCause.FALL); listener.damage(next); assertFalse(next.isCancelled)
    }

    @Test fun `void damage fallback only cancels damage after successful teleport`() {
        val listener = HomeVoidListener { true }
        val first = damage(EntityDamageEvent.DamageCause.VOID); listener.damage(first)
        assertTrue(first.isCancelled); assertEquals(318.0, destination!!.y)
        assertEquals(1, sessions(listener).size)
        teleportOk = false
        val failed = damage(EntityDamageEvent.DamageCause.VOID); listener.damage(failed)
        assertFalse(failed.isCancelled); assertTrue(sessions(listener).isEmpty())
        val outside = HomeVoidListener { false }
        val normal = damage(EntityDamageEvent.DamageCause.VOID); outside.damage(normal); assertFalse(normal.isCancelled)
    }

    @Test fun `cancelled or redirected movement removes uncompleted rescue`() {
        val listener = HomeVoidListener { true }
        val cancelled = move(-73.0); listener.move(cancelled); cancelled.isCancelled = true
        listener.moved(cancelled); assertTrue(sessions(listener).isEmpty())
        val redirected = move(-73.0); listener.move(redirected); redirected.to = Location(world, 0.0, 80.0, 0.0)
        listener.moved(redirected); assertTrue(sessions(listener).isEmpty())
    }

    @Test fun `void returns to world top preserving column and view while ordinary movement stays unchanged`() {
        val listener = HomeVoidListener { true }
        val ordinary = move(90.0); listener.move(ordinary); assertEquals(90.0, ordinary.to.y); assertFalse(reset)
        val rescue = move(-73.0); listener.move(rescue)
        assertEquals(318.0, rescue.to.y); assertEquals(12.5, rescue.to.x); assertEquals(-7.25, rescue.to.z)
        assertEquals(80f, rescue.to.yaw); assertEquals(15f, rescue.to.pitch); assertTrue(reset)
        listener.teleport(PlayerTeleportEvent(player, rescue.from, rescue.to))
        assertEquals(1, sessions(listener).size)
        ground = true; listener.move(move(100.0)); assertTrue(sessions(listener).isEmpty())
    }
    @Test fun `other worlds and teleport events are untouched`() {
        val listener = HomeVoidListener { false }
        val event = move(-100.0); listener.move(event); assertEquals(-100.0, event.to.y); assertFalse(reset)
        val home = HomeVoidListener { true }
        val teleport = PlayerTeleportEvent(player, event.from, event.to); home.move(teleport)
        assertEquals(-100.0, teleport.to.y); assertTrue(sessions(home).isEmpty())
    }
    @Test fun `manual teleport cancelled rescue quit and stop release protection`() {
        val listener = HomeVoidListener { true }
        val rescue = move(-73.0); listener.move(rescue)
        val cancelled = PlayerTeleportEvent(player, rescue.from, rescue.to); cancelled.isCancelled = true
        listener.teleport(cancelled); assertTrue(sessions(listener).isEmpty())
        listener.move(move(-73.0))
        listener.teleport(PlayerTeleportEvent(player, rescue.to, Location(world, 0.0, 100.0, 0.0)))
        assertTrue(sessions(listener).isEmpty())
        listener.move(move(-73.0)); listener.quit(PlayerQuitEvent(player, net.kyori.adventure.text.Component.empty(), PlayerQuitEvent.QuitReason.DISCONNECTED))
        assertTrue(sessions(listener).isEmpty())
        listener.move(move(-73.0)); listener.stop(); assertTrue(sessions(listener).isEmpty())
    }
}
