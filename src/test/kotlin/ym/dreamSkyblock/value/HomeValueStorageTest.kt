package ym.dreamSkyblock.value

import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ym.dreamSkyblock.api.*
import ym.dreamSkyblock.model.Home
import ym.dreamSkyblock.model.HomeTemplate
import ym.dreamSkyblock.service.HomeLevelService
import ym.dreamSkyblock.storage.*
import java.nio.file.Files
import java.nio.file.Path
import java.sql.DriverManager
import java.util.UUID
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

class HomeValueStorageTest {
    @TempDir lateinit var root: Path
    private val levels = HomeLevelService(YamlConfiguration())
    private val direct = Executor(Runnable::run)
    private fun home() = Home(UUID.randomUUID(), "Owner", "skyblock", HomeTemplate.Point(0.0, 100.0, 0.0), 48.0).apply {
        val owner = UUID.randomUUID(); owners.add(owner); ownerNames[owner] = "Owner"
    }
    private val machine = HomeValueKey("machines", "machine-1")

    @Test fun `database and backup retain facility values and removal revisions`() {
        val path = root.resolve("homes.db")
        val home = home()
        HomeValueLogic.update(home, machine, 650, 1, levels)
        val key = HomeValueKey.block("machines", 1, 100, 2)
        HomeValueLogic.update(home, key, 100, 1, levels)
        HomeValueLogic.update(home, key, 0, 2, levels)
        val backup = HomeSnapshot.fromHome(home)
        SqliteHomeRepository(path).use { it.save(backup) }
        SqliteHomeRepository(path).use { repository ->
            val stored = repository.loadAll().single()
            assertEquals(backup.valueRecords, stored.valueRecords)
            assertEquals(backup.valueRecords, HomeSnapshot.fromYaml(backup.toYaml()).valueRecords)
            assertThrows(UnsupportedOperationException::class.java) { (stored.valueRecords as MutableMap).clear() }
            val removed = stored.toHome()
            HomeValueLogic.update(removed, machine, 0, 2, levels)
            repository.save(HomeSnapshot.fromHome(removed))
            assertEquals(0L, repository.loadAll().single().experience)
            repository.restoreSnapshot(HomeSnapshot.fromYaml(backup.toYaml()))
            assertEquals(650L, repository.loadAll().single().experience)
            assertEquals(HomeValueStatus.STALE, HomeValueLogic.update(repository.loadAll().single().toHome(), key, 100, 1, levels))
        }
    }

    @Test fun `v1 migration backs up the valid original and preserves old experience as a record`() {
        val path = root.resolve("homes.db")
        val old = home().apply { experience = 650; level = 6 }
        SqliteHomeRepository(path).use { it.save(HomeSnapshot.fromHome(old)) }
        DriverManager.getConnection("jdbc:sqlite:$path").use { db -> db.createStatement().use {
            it.execute("DROP TABLE home_value_records"); it.execute("PRAGMA user_version=1")
        } }
        SqliteHomeRepository(path).use { repository ->
            val migrated = repository.loadAll().single()
            assertEquals(old.id, migrated.id)
            assertEquals(650L, migrated.valueRecords.getValue(HomeValueKey.LEGACY).value)
            assertEquals(old.owners, migrated.owners)
        }
        val backups = Files.list(root.resolve("backups")).use { it.toList() }
        assertEquals(1, backups.size)
        DriverManager.getConnection("jdbc:sqlite:${backups.single()}").use { db -> db.createStatement().use { statement ->
            statement.executeQuery("PRAGMA user_version").use { it.next(); assertEquals(1, it.getInt(1)) }
            statement.executeQuery("SELECT experience FROM homes").use { it.next(); assertEquals(650L, it.getLong(1)) }
        } }
        SqliteHomeRepository(path).use { assertEquals(650L, it.loadAll().single().experience) }
        assertEquals(1L, Files.list(root.resolve("backups")).use { it.count() })
    }

    @Test fun `ledger corruption fails instead of reconstructing a made up total`() {
        val path = root.resolve("homes.db")
        val home = home()
        HomeValueLogic.update(home, machine, 100, 1, levels)
        SqliteHomeRepository(path).use { it.save(HomeSnapshot.fromHome(home)) }
        DriverManager.getConnection("jdbc:sqlite:$path").use { db -> db.createStatement().use { it.execute("UPDATE homes SET experience=101") } }
        SqliteHomeRepository(path).use { repository -> assertThrows(IllegalArgumentException::class.java) { repository.loadAll() } }
    }

    @Test fun `facility insert failure rolls back home total level and border in the same transaction`() {
        val path = root.resolve("homes.db")
        val home = home()
        SqliteHomeRepository(path).use { repository ->
            repository.save(HomeSnapshot.fromHome(home))
            DriverManager.getConnection("jdbc:sqlite:$path").use { db -> db.createStatement().use {
                it.execute("CREATE TRIGGER reject_values BEFORE INSERT ON home_value_records BEGIN SELECT RAISE(ABORT,'test failure'); END")
            } }
            HomeValueLogic.update(home, machine, 650, 1, levels)
            assertThrows(Exception::class.java) { repository.save(HomeSnapshot.fromHome(home)) }
            val actual = repository.loadAll().single()
            assertEquals(0L, actual.experience); assertEquals(1, actual.level); assertEquals(48.0, actual.borderSize)
            assertTrue(actual.valueRecords.isEmpty())
        }
    }

    @Test fun `startup derives and persists levels before homes become available`() {
        val storage = HomeStorage(root.resolve("homes.db"))
        try {
            val old = home().apply { experience = 650; level = 1 }
            storage.save(old, direct).get(10, TimeUnit.SECONDS)
            val loaded = storage.loadAll(direct).get(10, TimeUnit.SECONDS)
            val reconciled = HomeValueStartup.reconcile(loaded, levels, storage, direct).get(10, TimeUnit.SECONDS).single()
            assertEquals(4, reconciled.level); assertEquals(128.0, reconciled.borderSize)
            val stored = storage.loadAll(direct).get(10, TimeUnit.SECONDS).single()
            assertEquals(4, stored.level); assertEquals(650L, stored.experience)
            assertEquals(650L, stored.valueRecords.getValue(HomeValueKey.LEGACY).value)
        } finally { storage.closeAsync().get(10, TimeUnit.SECONDS) }
    }
}
