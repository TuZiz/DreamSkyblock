package ym.dreamSkyblock.storage

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ym.dreamSkyblock.model.Home
import ym.dreamSkyblock.model.HomeFlag
import ym.dreamSkyblock.model.HomeTemplate
import java.nio.file.Path
import java.sql.DriverManager
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class SqliteHomeRepositoryTest {
    @TempDir
    lateinit var directory: Path
    private val directExecutor = Executor(Runnable::run)

    @Test
    fun `metadata survives close and restart with original ownership and spawn`() {
        val database = directory.resolve("data/homes.db")
        val home = home().apply {
            level = 5; borderLevel = 3; experience = Long.MAX_VALUE - 17; borderSize = 384.0
            spawn = HomeTemplate.Point(1.5, 67.0, -2.5, 123f, -32f)
            flags.putAll(HomeFlag.entries.associateWith { it.ordinal % 2 == 0 })
            val member = UUID.randomUUID(); owners.add(member); ownerNames[member] = "队友"
        }
        SqliteHomeRepository(database).use { it.save(HomeSnapshot.fromHome(home)) }
        SqliteHomeRepository(database).use { repository ->
            val restored = repository.loadAll().single()
            assertEquivalent(HomeSnapshot.fromHome(home), restored)
            assertEquals("dreamskyblock:dreamskyblock_${home.id.toString().replace("-", "")}", restored.worldKey)
        }
    }

    @Test
    fun `legacy import rolls entire transaction back when later member conflicts`() {
        SqliteHomeRepository(directory.resolve("homes.db")).use { repository ->
            val existing = home()
            repository.save(HomeSnapshot.fromHome(existing))
            val first = home()
            val collision = home(owner = existing.owners.single())
            assertThrows(IllegalArgumentException::class.java) {
                repository.importLegacy(listOf(HomeSnapshot.fromHome(first), HomeSnapshot.fromHome(collision)))
            }
            assertEquals(listOf(existing.id), repository.loadAll().map { it.id })
        }
    }

    @Test
    fun `deleted tombstone prevents legacy resurrection and ordinary saves`() {
        SqliteHomeRepository(directory.resolve("homes.db")).use { repository ->
            val original = HomeSnapshot.fromHome(home())
            repository.save(original)
            repository.delete(original.id)
            repository.importLegacy(listOf(original))
            assertTrue(repository.loadAll().isEmpty())
            val tombstone = repository.loadIncludingDeleted().single()
            assertEquals("DELETED", tombstone.status)
            assertEquals(original.owners, tombstone.owners)
            assertEquals(original.ownerNames, tombstone.ownerNames)
            assertThrows(IllegalArgumentException::class.java) { repository.save(original) }
            assertThrows(IllegalArgumentException::class.java) { repository.updateStatus(original.id, "ACTIVE") }
        }
    }

    @Test
    fun `restore rejects reclaimed members and keeps original tombstone`() {
        SqliteHomeRepository(directory.resolve("homes.db")).use { repository ->
            val original = HomeSnapshot.fromHome(home())
            repository.save(original)
            repository.delete(original.id)
            val replacement = HomeSnapshot.fromHome(home(owner = original.owners.single()))
            repository.save(replacement)
            assertThrows(IllegalArgumentException::class.java) { repository.restoreSnapshot(original) }
            assertEquals("DELETED", repository.loadIncludingDeleted().first { it.id == original.id }.status)
            repository.delete(replacement.id)
            assertEquivalent(original, repository.restoreSnapshot(original))
        }
    }

    @Test
    fun `snapshot YAML preserves every field and refuses malformed legacy data`() {
        val original = home().apply {
            spawn = HomeTemplate.Point(.5, 101.0, .5, 90f, -12f)
            status = "RECOVERY"
        }
        val snapshot = HomeSnapshot.fromHome(original)
        original.ownerNames.clear(); original.owners.clear(); original.flags.clear(); original.level = 99
        val restored = HomeSnapshot.fromYaml(snapshot.toYaml())
        assertEquivalent(snapshot, restored)
        assertThrows(UnsupportedOperationException::class.java) { (snapshot.owners as MutableSet).clear() }
        assertThrows(IllegalArgumentException::class.java) {
            HomeSnapshot.fromYaml(snapshot.toYaml().replace(snapshot.owners.single().toString(), "invalid-player-id"))
        }
        val missingMembers = snapshot.toYaml().replace(Regex("(?m)^owners:\\s*\\n(?:-.*\\n)*"), "owners: []\n")
        assertThrows(IllegalArgumentException::class.java) { HomeSnapshot.fromYaml(missingMembers) }
    }

    @Test
    fun `legacy YAML without new fields retains the established defaults`() {
        val id = UUID.randomUUID()
        val owner = UUID.randomUUID()
        val yaml = """
            id: $id
            owner-name: player
            template: vanilla
            center:
              x: 0.0
              y: 100.0
              z: 0.0
            border-size: 100.0
            owners:
              - $owner
            owner-names:
              $owner: player
            flags:
              pvp: true
        """.trimIndent()
        val result = HomeSnapshot.fromYaml(yaml)
        assertEquals(1, result.level)
        assertEquals(0, result.experience)
        assertEquals("ACTIVE", result.status)
        assertNull(result.spawn)
        assertEquals(setOf(owner), result.owners)
        assertEquals(true, result.flags[HomeFlag.PVP])
    }

    @Test
    fun `ordered worker persists captured states and drains accepted writes before close`() {
        val database = directory.resolve("homes.db")
        val storage = HomeStorage(database)
        val home = home()
        val saves = (1..60).map { level ->
            synchronized(home) {
                home.level = level
                home.ownerNames[home.owners.single()] = "player-$level"
                storage.save(home, directExecutor)
            }
        }
        home.level = 1000
        home.ownerNames.clear()
        val closed = storage.closeAsync()
        CompletableFuture.allOf(*saves.toTypedArray()).get(15, TimeUnit.SECONDS)
        closed.get(15, TimeUnit.SECONDS)
        assertThrows(java.util.concurrent.ExecutionException::class.java) {
            storage.loadAll(directExecutor).get(5, TimeUnit.SECONDS)
        }
        SqliteHomeRepository(database).use { repository ->
            val restored = repository.loadAll().single()
            assertEquals(60, restored.level)
            assertEquals("player-60", restored.ownerNames.values.single())
        }
    }

    @Test
    fun `concurrent independent saves cannot assign one member to two homes`() {
        val storage = HomeStorage(directory.resolve("homes.db"))
        val owner = UUID.randomUUID()
        val pool = Executors.newFixedThreadPool(4)
        try {
            val futures = (1..8).map {
                CompletableFuture.supplyAsync({ storage.save(home(owner = owner), directExecutor) }, pool)
            }.map { it.get(10, TimeUnit.SECONDS) }
            val outcomes = futures.map { runCatching { it.get(10, TimeUnit.SECONDS) }.isSuccess }
            assertEquals(1, outcomes.count { it })
            val homes = storage.loadAll(directExecutor).get(10, TimeUnit.SECONDS)
            assertEquals(1, homes.size)
            assertEquals(setOf(owner), homes.single().owners)
        } finally {
            pool.shutdownNow()
            storage.closeAsync().get(10, TimeUnit.SECONDS)
        }
    }

    @Test
    fun `unknown schema is rejected without changing its tables or version`() {
        val database = directory.resolve("homes.db")
        Class.forName("org.sqlite.JDBC")
        DriverManager.getConnection("jdbc:sqlite:$database").use { connection ->
            connection.createStatement().use {
                it.execute("CREATE TABLE old_data (value TEXT)")
                it.execute("INSERT INTO old_data VALUES ('preserve-me')")
                it.execute("PRAGMA user_version=7")
            }
        }
        SqliteHomeRepository(database).use { repository ->
            assertThrows(IllegalStateException::class.java) { repository.loadAll() }
        }
        DriverManager.getConnection("jdbc:sqlite:$database").use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT value FROM old_data").use { it.next(); assertEquals("preserve-me", it.getString(1)) }
                statement.executeQuery("PRAGMA user_version").use { it.next(); assertEquals(7, it.getInt(1)) }
            }
        }
    }

    @Test
    fun `late ordinary snapshot cannot regress completed lifecycle status`() {
        SqliteHomeRepository(directory.resolve("homes.db")).use { repository ->
            val creating = HomeSnapshot.fromHome(home().apply { status = "CREATING" })
            repository.save(creating)
            repository.updateStatus(creating.id, "ACTIVE")
            repository.save(creating)
            assertEquals("ACTIVE", repository.loadAll().single().status)
        }
    }

    @Test
    fun `corrupted missing ownership fails instead of dropping or recreating a home`() {
        val database = directory.resolve("homes.db")
        SqliteHomeRepository(database).use { it.save(HomeSnapshot.fromHome(home())) }
        DriverManager.getConnection("jdbc:sqlite:$database").use { connection ->
            connection.createStatement().use { it.execute("DELETE FROM home_members") }
        }
        SqliteHomeRepository(database).use { repository ->
            assertThrows(IllegalArgumentException::class.java) { repository.loadAll() }
        }
    }

    private fun home(owner: UUID = UUID.randomUUID()): Home = Home(UUID.randomUUID(), "player", "skyblock",
        HomeTemplate.Point(0.0, 100.0, 0.0, 12f, 34f), 100.0).apply {
        owners.add(owner); ownerNames[owner] = "player"; flags[HomeFlag.PVP] = true
    }

    private fun assertEquivalent(expected: HomeSnapshot, actual: HomeSnapshot) {
        assertAll(
            { assertEquals(expected.id, actual.id) },
            { assertEquals(expected.ownerName, actual.ownerName) },
            { assertEquals(expected.templateId, actual.templateId) },
            { assertEquals(expected.center, actual.center) },
            { assertEquals(expected.spawn, actual.spawn) },
            { assertEquals(expected.borderSize, actual.borderSize) },
            { assertEquals(expected.level, actual.level) },
            { assertEquals(expected.experience, actual.experience) },
            { assertEquals(expected.borderLevel, actual.borderLevel) },
            { assertEquals(expected.memberLimit, actual.memberLimit) },
            { assertEquals(expected.owners, actual.owners) },
            { assertEquals(expected.ownerNames, actual.ownerNames) },
            { assertEquals(expected.flags, actual.flags) },
            { assertEquals(expected.status, actual.status) }
        )
    }
}
