package ym.dreamSkyblock.login

import io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler
import io.papermc.paper.connection.PlayerConfigurationConnection
import io.papermc.paper.event.player.AsyncPlayerSpawnLocationEvent
import com.destroystokyo.paper.profile.PlayerProfile
import org.bukkit.Location
import org.bukkit.Server
import org.bukkit.World
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import sun.misc.Unsafe
import ym.dreamSkyblock.DreamSkyblock
import ym.dreamSkyblock.config.ConfigService
import ym.dreamSkyblock.model.Home
import ym.dreamSkyblock.model.HomeTemplate
import ym.dreamSkyblock.service.HomeManager
import ym.dreamSkyblock.world.HomeWorldManager
import java.lang.reflect.Proxy
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Exercise the real scheduled prepare entry, before any world reservation or loading. */
class HomeSpawnServiceTest {
    private val unsafe = Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null) as Unsafe
    private inline fun <reified T> blank(): T = unsafe.allocateInstance(T::class.java) as T
    private fun field(target: Any, name: String, value: Any) {
        val owner = generateSequence(target.javaClass) { it.superclass }.first { type -> type.declaredFields.any { it.name == name } }
        owner.getDeclaredField(name).apply { isAccessible = true }.set(target, value)
    }
    private inline fun <reified T> proxy(crossinline invoke: (String, Array<out Any?>) -> Any?): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, args ->
            invoke(method.name, args ?: emptyArray())
        } as T

    private val playerId = UUID.randomUUID()
    private val islandWorldId = UUID.randomUUID()
    private val home = Home(UUID.randomUUID(), "owner", "skyblock", HomeTemplate.Point(0.0, 100.0, 0.0), 48.0)
    private var templateLookups = 0
    private val worlds = blank<HomeWorldManager>().also {
        field(it, "loaded", ConcurrentHashMap<UUID, World>().apply {
            put(home.id, proxy { name, _ -> if (name == "getUID") islandWorldId else error(name) })
        })
        field(it, "maintenance", ConcurrentHashMap.newKeySet<UUID>())
    }
    private val homes = blank<HomeManager>().also {
        field(it, "ready", true)
        field(it, "homes", ConcurrentHashMap<UUID, Home>().apply { put(home.id, home) })
        field(it, "playerHome", ConcurrentHashMap<UUID, UUID>().apply { put(playerId, home.id) })
    }
    private val plugin = blank<DreamSkyblock>().also {
        field(it, "isEnabled", true)
        val scheduler = proxy<GlobalRegionScheduler> { name, args ->
            check(name == "execute"); (args[1] as Runnable).run(); null
        }
        field(it, "server", proxy<Server> { name, _ -> if (name == "getGlobalRegionScheduler") scheduler else error(name) })
        field(it, "configService", ConfigService(it).also { config ->
            // A missing template stops after eligibility. Counting access proves excluded
            // logins never enter template selection, reservation, or world preparation.
            field(config, "templates", object : AbstractMap<String, HomeTemplate>() {
                override val entries: Set<Map.Entry<String, HomeTemplate>> = emptySet()
                override fun get(key: String): HomeTemplate? { templateLookups++; return null }
            })
        })
    }
    private val service = HomeSpawnService(plugin, homes, worlds)

    @Test fun `main world login never prepares an island despite membership`() {
        assertNull(service.prepare(playerId, UUID.randomUUID()).join())
        assertEquals(0, templateLookups)
    }
    @Test fun `login inside own loaded island retains preloading`() {
        assertNull(service.prepare(playerId, islandWorldId).join())
        assertEquals(1, templateLookups)
    }
    @Test fun `unloaded island uses persisted join fallback rather than forcing the initial spawn`() {
        field(worlds, "loaded", ConcurrentHashMap<UUID, World>())
        assertNull(service.prepare(playerId, UUID.randomUUID()).join())
        assertEquals(0, templateLookups)
    }
    @Test fun `respawn preparation remains independent of login world`() {
        assertNull(service.prepare(playerId).join())
        assertEquals(1, templateLookups)
    }
    @Test fun `player without an island does not prepare a world`() {
        assertNull(service.prepare(UUID.randomUUID(), islandWorldId).join())
        assertEquals(0, templateLookups)
    }
    @Test fun `initial spawn event keeps the server selected non island location`() {
        val profile = proxy<PlayerProfile> { name, _ -> if (name == "getId") playerId else error(name) }
        val connection = proxy<PlayerConfigurationConnection> { name, _ -> if (name == "getProfile") profile else error(name) }
        val mainWorld = proxy<World> { name, _ -> if (name == "getUID") UUID(0, 1) else error(name) }
        val original = Location(mainWorld, 37.5, 85.0, -21.5, 120f, 15f)
        val event = AsyncPlayerSpawnLocationEvent(connection, original, false)
        val listener = blank<HomeInitialSpawn>().also { field(it, "spawns", service) }
        listener.spawn(event)
        assertEquals(original, event.spawnLocation)
        assertEquals(0, templateLookups)
    }
}
