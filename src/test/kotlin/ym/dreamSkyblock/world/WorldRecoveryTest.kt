package ym.dreamSkyblock.world

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

class WorldRecoveryTest {
    @TempDir lateinit var root: Path

    @Test fun `restore repairs incomplete world while retaining all damaged files`() {
        val fixture = WorldFileFixtures(root)
        val id = UUID.randomUUID()
        fixture.native(fixture.files.worldDir(id), "valid-backup")
        val backup = fixture.files.backup(id, "id: $id\n")
        Files.delete(fixture.files.worldDir(id).resolve("data/minecraft/world_gen_settings.dat"))
        fixture.write(fixture.files.worldDir(id).resolve("region/r.0.0.mca"), "damaged-state")
        fixture.files.restore(id, backup)
        fixture.files.acknowledgeRestore(id)
        assertEquals("valid-backup", fixture.region(id))
        val retained = Files.list(fixture.files.worldDir(id).parent).use { it.filter { path -> path.fileName.toString().startsWith(".rollback-$id-") }.findFirst().orElseThrow() }
        assertEquals("damaged-state", Files.readString(retained.resolve("region/r.0.0.mca")))
    }

    @Test fun `restore keeps old world and requires matching database metadata confirmation`() {
        val fixture = WorldFileFixtures(root)
        val id = UUID.randomUUID()
        fixture.native(fixture.files.worldDir(id), "backup-state")
        val metadata = "id: $id\nlevel: 2\n"
        val backup = fixture.files.backup(id, metadata)
        fixture.write(fixture.files.worldDir(id).resolve("region/r.0.0.mca"), "current-state")
        assertEquals(metadata, fixture.files.restore(id, backup))
        assertEquals("backup-state", fixture.region(id))
        assertEquals(mapOf(id to metadata), fixture.files.pendingRestores())
        assertThrows(IllegalArgumentException::class.java) { fixture.files.hasWorldData(id) }
        fixture.files.acknowledgeRestore(id)
        assertTrue(fixture.files.hasWorldData(id))
        assertTrue(fixture.files.pendingRestores().isEmpty())
        val namespace = fixture.files.worldDir(id).parent
        val rollback = Files.list(namespace).use { it.filter { path -> path.fileName.toString().startsWith(".rollback-$id-") }.findFirst().orElseThrow() }
        assertEquals("current-state", Files.readString(rollback.resolve("region/r.0.0.mca")))
    }

    @Test fun `every interrupted precommit rename safely rolls back on restart`() {
        for (phase in listOf("prepared", "old-moved", "new-moved")) {
            val fixture = WorldFileFixtures(root.resolve(phase))
            val id = UUID.randomUUID()
            fixture.native(fixture.files.worldDir(id), "backup-state")
            val backup = fixture.files.backup(id, "id: $id\nlevel: 1\n")
            fixture.write(fixture.files.worldDir(id).resolve("region/r.0.0.mca"), "latest-state")
            fixture.files.transactionCheckpoint = { if (it == phase) error("simulated crash after $phase") }
            assertThrows(IllegalStateException::class.java) { fixture.files.restore(id, backup) }
            val restarted = NativeWorldFiles(fixture.pluginRoot, fixture.baseWorld, diagnostic = {})
            restarted.recover()
            assertEquals("latest-state", fixture.region(id), phase)
            assertTrue(restarted.pendingRestores().isEmpty(), phase)
            assertTrue(restarted.hasWorldData(id), phase)
            assertEquals("id: $id\nlevel: 1\n", restarted.readBackup(id, backup))
            restarted.recover() // Repeated recovery is idempotent.
        }
    }

    @Test fun `committed restore survives restart pending sqlite metadata application`() {
        val fixture = WorldFileFixtures(root)
        val id = UUID.randomUUID()
        fixture.native(fixture.files.worldDir(id), "backup-state")
        val metadata = "id: $id\nlevel: 3\n"
        val backup = fixture.files.backup(id, metadata)
        fixture.write(fixture.files.worldDir(id).resolve("region/r.0.0.mca"), "latest-state")
        fixture.files.transactionCheckpoint = { if (it == "committed") error("crash before SQLite commit") }
        assertThrows(IllegalStateException::class.java) { fixture.files.restore(id, backup) }
        val restarted = NativeWorldFiles(fixture.pluginRoot, fixture.baseWorld, diagnostic = {})
        restarted.recover()
        assertEquals("backup-state", fixture.region(id))
        assertEquals(mapOf(id to metadata), restarted.pendingRestores())
        restarted.acknowledgeRestore(id)
        val nextRestart = NativeWorldFiles(fixture.pluginRoot, fixture.baseWorld, diagnostic = {})
        nextRestart.recover()
        assertTrue(nextRestart.pendingRestores().isEmpty())
        assertTrue(nextRestart.hasWorldData(id))
    }

    @Test fun `corrupted committed replacement returns to preserved old world`() {
        val fixture = WorldFileFixtures(root)
        val id = UUID.randomUUID()
        fixture.native(fixture.files.worldDir(id), "backup-state")
        val backup = fixture.files.backup(id, "id: $id\n")
        fixture.write(fixture.files.worldDir(id).resolve("region/r.0.0.mca"), "latest-valid-state")
        fixture.files.restore(id, backup)
        fixture.write(fixture.files.worldDir(id).resolve("region/r.0.0.mca"), "corrupted-after-commit")
        val restarted = NativeWorldFiles(fixture.pluginRoot, fixture.baseWorld, diagnostic = {})
        restarted.recover()
        assertEquals("latest-valid-state", fixture.region(id))
        assertTrue(restarted.pendingRestores().isEmpty())
    }
}
