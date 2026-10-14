package ym.dreamSkyblock.world

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

class NativeWorldMigrationTest {
    @TempDir lateinit var root: Path

    @Test fun `interrupted owned Paper import retains partial worlds before retrying backup`() {
        val fixture = WorldFileFixtures(root)
        val id = UUID.randomUUID()
        val home = fixture.oldHome(id)
        fixture.legacy(home.resolve("world"), "before-crash")
        assertTrue(fixture.files.prepareLegacyImport(id, home))
        val stage = root.resolve("dreamskyblock_${fixture.files.worldName(id)}")
        fixture.write(stage.resolve("region/r.0.0.mca"), "half-consumed")
        fixture.write(fixture.files.worldDir(id).resolve("region/r.0.0.mca"), "partial-import")
        assertTrue(fixture.files.prepareLegacyImport(id, home))
        assertFalse(Files.exists(fixture.files.worldDir(id)))
        assertEquals("before-crash", Files.readString(stage.resolve("region/r.0.0.mca")))
        assertEquals(1L, fixture.backupCount(id))
        assertTrue(Files.list(root).use { it.anyMatch { entry -> entry.fileName.toString().startsWith(".failed-import-$id-") } })
        assertTrue(Files.list(fixture.files.worldDir(id).parent).use { it.anyMatch { entry -> entry.fileName.toString().startsWith(".failed-import-$id-") } })
    }

    @Test fun `completed creation receipt distinguishes saved island from partial creation`() {
        val fixture = WorldFileFixtures(root)
        val id = UUID.randomUUID()
        fixture.native(fixture.files.worldDir(id))
        assertFalse(fixture.files.creationCompleted(id))
        fixture.files.recordCreation(id, UUID.randomUUID())
        assertTrue(fixture.files.creationCompleted(id))
    }

    @Test fun `native migration retains source and ignores preserved stale copies on retry`() {
        val fixture = WorldFileFixtures(root)
        val id = UUID.randomUUID()
        val source = fixture.oldHome(id).resolve("world")
        fixture.native(source)
        fixture.write(fixture.oldHome(id).resolve("home.yml"), "id: $id\nowner-name: Player\n")
        assertTrue(fixture.files.migrate(id, fixture.oldHome(id)))
        assertTrue(Files.exists(source.resolve("region/r.0.0.mca")))
        assertEquals("original", fixture.region(id))
        fixture.write(fixture.files.worldDir(id).resolve("region/r.0.0.mca"), "new-native-data")
        assertTrue(fixture.files.migrate(id, fixture.oldHome(id)))
        assertEquals("new-native-data", fixture.region(id))
        assertEquals(1L, fixture.backupCount(id))
    }

    @Test fun `conflicting sources are backed up retained and retryable`() {
        val fixture = WorldFileFixtures(root)
        val id = UUID.randomUUID()
        val source = fixture.oldHome(id).resolve("world")
        val runtime = fixture.baseWorld.resolve("dimensions/minecraft/${fixture.files.worldName(id)}")
        fixture.native(source, "stored-copy")
        fixture.native(runtime, "runtime-copy")
        assertThrows(IllegalArgumentException::class.java) { fixture.files.migrate(id, fixture.oldHome(id)) }
        assertFalse(Files.exists(fixture.files.worldDir(id)))
        assertEquals(2L, fixture.backupCount(id))
        assertEquals("stored-copy", Files.readString(source.resolve("region/r.0.0.mca")))
        assertEquals("runtime-copy", Files.readString(runtime.resolve("region/r.0.0.mca")))
        fixture.write(runtime.resolve("region/r.0.0.mca"), "stored-copy")
        assertTrue(fixture.files.migrate(id, fixture.oldHome(id)))
        assertTrue(Files.exists(source) && Files.exists(runtime))
    }

    @Test fun `metadata alone cannot become a valid saved world`() {
        val fixture = WorldFileFixtures(root)
        val id = UUID.randomUUID()
        val path = fixture.files.worldDir(id)
        fixture.write(path.resolve("data/paper/metadata.dat"), "metadata")
        assertFalse(fixture.files.validateWorld(path))
        assertThrows(IllegalArgumentException::class.java) { fixture.files.hasWorldData(id) }
        assertThrows(IllegalArgumentException::class.java) { fixture.files.migrate(id) }
    }

    @Test fun `old level dat uses owned Paper import stage and never native copy`() {
        val fixture = WorldFileFixtures(root)
        val id = UUID.randomUUID()
        val home = fixture.oldHome(id)
        fixture.legacy(home.resolve("world"), "legacy-island")
        assertTrue(fixture.files.prepareLegacyImport(id, home))
        val stage = root.resolve("dreamskyblock_${fixture.files.worldName(id)}")
        assertTrue(Files.exists(stage.resolve("level.dat")))
        assertFalse(Files.exists(fixture.files.worldDir(id)))
        assertTrue(fixture.files.prepareLegacyImport(id, home))
        assertEquals(1L, fixture.backupCount(id))
        assertThrows(IllegalArgumentException::class.java) { fixture.files.migrate(id, home) }
        fixture.native(fixture.files.worldDir(id), "legacy-island")
        fixture.consumeLegacyStage(stage) // Simulates Paper's documented conversion and removal of its disposable input.
        assertTrue(fixture.files.migrate(id, home))
        assertFalse(fixture.files.prepareLegacyImport(id, home))
        assertTrue(Files.exists(home.resolve("world/level.dat")))
        assertEquals("legacy-island", fixture.region(id))
    }

    @Test fun `unowned Paper import directory cannot be consumed`() {
        val fixture = WorldFileFixtures(root)
        val id = UUID.randomUUID()
        val home = fixture.oldHome(id)
        fixture.legacy(home.resolve("world"))
        val stage = root.resolve("dreamskyblock_${fixture.files.worldName(id)}")
        fixture.legacy(stage, "foreign-data")
        assertThrows(IllegalArgumentException::class.java) { fixture.files.prepareLegacyImport(id, home) }
        assertEquals("foreign-data", Files.readString(stage.resolve("region/r.0.0.mca")))
        assertTrue(Files.exists(home.resolve("world/level.dat")))
    }

    @Test fun `legacy import resumes from sealed backup when disposable stage disappeared`() {
        val fixture = WorldFileFixtures(root)
        val id = UUID.randomUUID()
        val home = fixture.oldHome(id)
        fixture.legacy(home.resolve("world"), "valid-before-crash")
        assertTrue(fixture.files.prepareLegacyImport(id, home))
        val stage = root.resolve("dreamskyblock_${fixture.files.worldName(id)}")
        fixture.consumeLegacyStage(stage)
        fixture.write(home.resolve("world/region/r.0.0.mca"), "later-untrusted-copy")
        assertTrue(fixture.files.prepareLegacyImport(id, home))
        assertEquals("valid-before-crash", Files.readString(stage.resolve("region/r.0.0.mca")))
        assertEquals(1L, fixture.backupCount(id))
    }
}
