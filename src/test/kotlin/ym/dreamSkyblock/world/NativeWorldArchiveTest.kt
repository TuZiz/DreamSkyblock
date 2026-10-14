package ym.dreamSkyblock.world

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

class NativeWorldArchiveTest {
    @TempDir lateinit var root: Path

    @Test fun `failed creation or missing world is deleted only after raw metadata archive`() {
        val fixture = WorldFileFixtures(root)
        val missing = UUID.randomUUID()
        val missingMetadata = "id: $missing\n"
        val absentArchive = fixture.files.archiveDeleted(missing, missingMetadata)
        assertEquals(absentArchive, fixture.files.archiveDeleted(missing, missingMetadata))
        assertTrue(Files.exists(fixture.pluginRoot.resolve("backups/$missing/$absentArchive/home.yml")))
        val partial = UUID.randomUUID()
        fixture.write(fixture.files.worldDir(partial).resolve("region/r.0.0.mca"), "partial-creation")
        val rawArchive = fixture.files.archiveDeleted(partial, "id: $partial\n")
        assertFalse(Files.exists(fixture.files.worldDir(partial)))
        assertEquals("partial-creation", Files.readString(fixture.pluginRoot.resolve("trash/$partial/$rawArchive/world/region/r.0.0.mca")))
    }

    @Test fun `archive manifests detect corrupted world and metadata`() {
        val fixture = WorldFileFixtures(root)
        val id = UUID.randomUUID()
        fixture.native(fixture.files.worldDir(id))
        val metadata = "id: $id\nowner-name: 中文玩家\n"
        val backup = fixture.files.backup(id, metadata)
        assertEquals(metadata, fixture.files.readBackup(id, backup))
        val archived = fixture.pluginRoot.resolve("backups/$id/$backup")
        fixture.write(archived.resolve("world/region/r.0.0.mca"), "corruption")
        assertThrows(IllegalArgumentException::class.java) { fixture.files.readBackup(id, backup) }
        assertEquals("original", fixture.region(id))
        val second = fixture.files.backup(id, metadata)
        fixture.write(fixture.pluginRoot.resolve("backups/$id/$second/home.yml"), "tampered")
        assertThrows(IllegalArgumentException::class.java) { fixture.files.readBackup(id, second) }
    }

    @Test fun `deletion archives source and is idempotent across a restart`() {
        val fixture = WorldFileFixtures(root)
        val id = UUID.randomUUID()
        fixture.native(fixture.files.worldDir(id))
        val metadata = "id: $id\n"
        val backup = fixture.files.archiveDeleted(id, metadata)
        assertFalse(Files.exists(fixture.files.worldDir(id)))
        assertEquals(metadata, fixture.files.readBackup(id, backup))
        assertTrue(Files.exists(fixture.pluginRoot.resolve("trash/$id/$backup/world/region/r.0.0.mca")))
        val restarted = NativeWorldFiles(fixture.pluginRoot, fixture.baseWorld, diagnostic = {})
        assertEquals(backup, restarted.archiveDeleted(id, metadata))
        assertEquals(1L, fixture.backupCount(id))
    }

    @Test fun `audit reports orphan worlds without changing their contents`() {
        val fixture = WorldFileFixtures(root)
        val known = UUID.randomUUID()
        val orphan = UUID.randomUUID()
        fixture.native(fixture.files.worldDir(known))
        fixture.native(fixture.files.worldDir(orphan), "orphan-data")
        assertEquals(listOf(fixture.files.worldDir(orphan)), fixture.files.audit(setOf(known)))
        assertEquals("orphan-data", fixture.region(orphan))
    }

    @Test fun `links inside worlds are rejected instead of followed`() {
        val fixture = WorldFileFixtures(root)
        val id = UUID.randomUUID()
        fixture.native(fixture.files.worldDir(id))
        val outside = root.resolve("outside-secret")
        fixture.write(outside, "outside")
        val link = fixture.files.worldDir(id).resolve("linked-data")
        val created = try {
            Files.createSymbolicLink(link, outside)
            true
        } catch (_: java.nio.file.FileSystemException) {
            false
        } catch (_: UnsupportedOperationException) {
            false
        }
        assumeTrue(created, "Host does not permit creating symbolic links")
        assertThrows(IllegalArgumentException::class.java) { fixture.files.backup(id, "id: $id\n") }
        assertEquals("outside", Files.readString(outside))
    }
}
