package ym.dreamSkyblock.storage

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ym.dreamSkyblock.model.Home
import ym.dreamSkyblock.model.HomeTemplate
import ym.dreamSkyblock.world.WorldFileFixtures
import java.nio.file.Files
import java.nio.file.Path
import java.sql.DriverManager
import java.util.UUID

class HomeOwnershipRecoveryTest {
    @TempDir lateinit var root: Path
    private val fixture by lazy { WorldFileFixtures(root) }
    private val db by lazy { fixture.pluginRoot.resolve("data/homes.db") }
    private val pending by lazy { fixture.pluginRoot.resolve("data/recovery/pending-worlds.properties") }
    private fun snapshot(owner: UUID = UUID.randomUUID(), status: String = "ACTIVE") = HomeSnapshot.fromHome(
        Home(UUID.randomUUID(), "主人", "skyblock", HomeTemplate.Point(0.0, 100.0, 0.0), 48.0).apply {
            owners.add(owner); ownerNames[owner] = "主人"; this.status = status
        }
    )
    private fun world(home: HomeSnapshot) = fixture.native(fixture.files.worldDir(home.id), "player-buildings")
    private fun recover(repository: SqliteHomeRepository) = HomeOwnershipRecovery(pending).recover(repository, fixture.files)
    private fun loseRow(id: UUID) {
        // Simulate loss/restoration of old DB metadata independently from the surviving world/journal.
        DriverManager.getConnection("jdbc:sqlite:$db").use { c ->
            listOf("home_members", "home_owner_names", "home_flags", "home_value_records", "homes").forEach { table ->
                c.prepareStatement("DELETE FROM $table WHERE home_id=?").use { s -> s.setString(1,id.toString());s.executeUpdate() }
            }
        }
    }

    @Test fun `committed ownership recovers missing row without changing world and is idempotent`() {
        val home = snapshot(); world(home)
        SqliteHomeRepository(db).use { it.save(home) }
        loseRow(home.id)
        SqliteHomeRepository(db).use { repo ->
            assertTrue(recover(repo).any { it.contains("已从持久归属凭据恢复") })
            assertEquals(home.owners, repo.loadAll().single().owners)
            assertEquals("player-buildings", fixture.region(home.id))
            assertTrue(recover(repo).isEmpty())
            assertEquals(1, repo.loadAll().size)
        }
    }

    @Test fun `committed creating requires saved creation receipt`() {
        val completed = snapshot(status="CREATING"); val interrupted = snapshot(status="CREATING")
        world(completed); world(interrupted)
        SqliteHomeRepository(db).use { it.save(completed); it.save(interrupted) }
        fixture.files.recordCreation(completed.id, UUID.randomUUID())
        loseRow(completed.id); loseRow(interrupted.id)
        SqliteHomeRepository(db).use { repo ->
            recover(repo)
            assertEquals(listOf(completed.id), repo.loadAll().map { it.id })
            assertEquals("ACTIVE", repo.loadAll().single().status)
            assertTrue(Files.readString(pending).isNotEmpty())
            assertEquals("player-buildings", fixture.region(interrupted.id))
        }
    }

    @Test fun `prepared evidence and unknown worlds remain untouched`() {
        val home = snapshot(); val unknown = snapshot(); world(home); world(unknown)
        SqliteHomeRepository(db).use { repo ->
            repo.recoveryJournal.write(home, false)
            val result = recover(repo)
            assertEquals(2, result.size)
            assertTrue(repo.loadAll().isEmpty())
            assertEquals("player-buildings", fixture.region(home.id))
            assertEquals("player-buildings", fixture.region(unknown.id))
            val io=ym.dreamSkyblock.world.WorldArchiveIO()
            val before=io.readProperties(pending)
            recover(repo)
            assertEquals(before["first-seen.${fixture.files.worldName(home.id)}"],io.readProperties(pending)["first-seen.${fixture.files.worldName(home.id)}"])
        }
    }

    @Test fun `existing sqlite wins over prepared or stale evidence including tombstones`() {
        val home = snapshot(); world(home)
        SqliteHomeRepository(db).use { repo ->
            repo.save(home)
            repo.recoveryJournal.write(home.withStatus("DELETED"), false)
            recover(repo)
            assertEquals("ACTIVE", repo.recoveryJournal.read(home.id)!!.snapshot.status)
            repo.delete(home.id)
            recover(repo)
            assertTrue(repo.loadAll().isEmpty())
            assertEquals("DELETED", repo.recoveryJournal.read(home.id)!!.snapshot.status)
            assertTrue(Files.exists(fixture.files.worldDir(home.id)))
        }
    }

    @Test fun `missing tombstone row must never resurrect deleted island`() {
        val home=snapshot();world(home)
        SqliteHomeRepository(db).use { it.save(home);it.delete(home.id) }
        loseRow(home.id)
        SqliteHomeRepository(db).use { repo ->
            assertTrue(recover(repo).single().contains("删除墓碑"))
            assertTrue(repo.loadAll().isEmpty())
        }
    }

    @Test fun `active membership conflict preserves both worlds without reassignment`() {
        val owner=UUID.randomUUID();val old=snapshot(owner);val current=snapshot(owner)
        world(old);world(current)
        SqliteHomeRepository(db).use { it.save(old) };loseRow(old.id)
        SqliteHomeRepository(db).use { repo ->
            repo.save(current)
            assertTrue(recover(repo).single().contains("成员已归属其他空岛"))
            assertEquals(listOf(current.id),repo.loadAll().map{it.id})
            assertEquals("player-buildings",fixture.region(old.id))
        }
    }

    @Test fun `mutually conflicting missing candidates are all rejected`() {
        val owner=UUID.randomUUID();val first=snapshot(owner);val second=snapshot(owner)
        world(first);world(second)
        SqliteHomeRepository(db).use { repo ->
            repo.recoveryJournal.write(first,true);repo.recoveryJournal.write(second,true)
            assertEquals(2,recover(repo).count{it.contains("多份恢复凭据成员冲突")})
            assertTrue(repo.loadAll().isEmpty())
        }
    }

    @Test fun `rollback leaves ambiguous evidence that cannot revive partial multi-home import`() {
        val existing=snapshot(); val first=snapshot();val conflict=snapshot(existing.owners.single())
        world(first)
        SqliteHomeRepository(db).use { repo ->
            repo.save(existing)
            assertThrows(IllegalArgumentException::class.java){repo.importLegacy(listOf(first,conflict))}
            assertFalse(repo.recoveryJournal.read(first.id)!!.committed)
            recover(repo)
            assertEquals(listOf(existing.id),repo.loadAll().map{it.id})
        }
    }

    @Test fun `live database backup includes committed WAL and rotates only verified own snapshots`() {
        val home=snapshot()
        SqliteHomeRepository(db).use { repo ->
            repo.save(home)
            val copy=repo.backupDatabase("periodic")
            DriverManager.getConnection("jdbc:sqlite:$copy").use { c ->
                c.createStatement().use { s -> s.executeQuery("SELECT home_id FROM homes").use { r -> assertTrue(r.next());assertEquals(home.id.toString(),r.getString(1)) } }
            }
            val unrelated=copy.parent.resolve("manual-owner-backup.db");Files.writeString(unrelated,"keep")
            repeat(9){repo.backupDatabase("periodic")}
            assertTrue(Files.exists(unrelated))
            Files.list(copy.parent).use { files -> assertEquals(8,files.filter{it.fileName.toString().startsWith("homes-")}.count().toInt()) }
        }
    }

    @Test fun `corrupt ownership evidence and incomplete world remain pending without affecting healthy homes`() {
        val healthy=snapshot();val corrupt=snapshot();val partial=snapshot()
        world(healthy);world(corrupt)
        Files.createDirectories(fixture.files.worldDir(partial.id))
        SqliteHomeRepository(db).use { repo ->
            repo.save(healthy);repo.recoveryJournal.write(partial,true)
            val path=db.parent.resolve("recovery/homes/${corrupt.id}.properties")
            Files.writeString(path,"schema-version=999\n")
            assertEquals(2,recover(repo).size)
            assertEquals(listOf(healthy.id),repo.loadAll().map{it.id})
            assertTrue(Files.exists(path))
            assertTrue(Files.exists(fixture.files.worldDir(partial.id)))
        }
    }

    @Test fun `forced JVM halt preserves committed creating row WAL and recoverable ownership`() {
        val id=UUID.randomUUID();val owner=UUID.randomUUID()
        val log=root.resolve("crash-writer.log")
        val child=ProcessBuilder(
            Path.of(System.getProperty("java.home"),"bin","java.exe").toString(),"-cp",
            System.getProperty("surefire.test.class.path",System.getProperty("java.class.path")),
            OwnershipCrashWriter::class.java.name,db.toString(),id.toString(),owner.toString()
        ).redirectErrorStream(true).redirectOutput(log.toFile()).start()
        try {
            assertTrue(child.waitFor(25,java.util.concurrent.TimeUnit.SECONDS),"child timeout: $log")
            assertEquals(0,child.exitValue(),Files.readString(log))
            assertTrue(Files.exists(db.resolveSibling("homes.db-wal")))
            SqliteHomeRepository(db).use { repo ->
                val home=repo.loadAll().single()
                assertEquals(id,home.id);assertEquals(setOf(owner),home.owners);assertEquals("CREATING",home.status)
                assertTrue(repo.recoveryJournal.read(id)!!.committed)
                // Reconciliation must not invent a world or discard a committed ownership row.
                recover(repo)
                assertEquals(id,repo.loadAll().single().id)
                assertFalse(Files.exists(fixture.files.worldDir(id)))
            }
        } finally { if(child.isAlive)child.destroyForcibly() }
    }

    @Test fun `unwritable backup location refuses ownership changes and leaves valid evidence intact`() {
        val home=snapshot();world(home)
        SqliteHomeRepository(db).use { it.save(home) };loseRow(home.id)
        val block=db.parent.resolve("backups/recovery")
        Files.createDirectories(block.parent);Files.writeString(block,"blocked")
        SqliteHomeRepository(db).use { repo ->
            assertThrows(Exception::class.java){recover(repo)}
            assertTrue(repo.loadAll().isEmpty())
            assertTrue(repo.recoveryJournal.read(home.id)!!.committed)
            assertEquals("player-buildings",fixture.region(home.id))
        }
    }

    @Test fun `commit boundaries distinguish rollback from committed data and pause uncertain subsequent writes`() {
        for(phase in listOf("before-commit","after-commit")) {
            val path=root.resolve(phase).resolve("homes.db"); val home=snapshot()
            SqliteHomeRepository(path).use { repo ->
                repo.transactionCheckpoint={if(it==phase)throw java.io.IOException("simulated interruption $phase")}
                assertThrows(Exception::class.java){repo.save(home)}
                assertFalse(repo.recoveryJournal.read(home.id)!!.committed)
                assertEquals(if(phase=="after-commit")1 else 0,repo.loadAll().size)
                if(phase=="after-commit") {
                    repo.transactionCheckpoint={}
                    assertThrows(IllegalStateException::class.java){repo.save(snapshot())}
                }
            }
            SqliteHomeRepository(path).use { restarted ->
                if(phase=="after-commit") {
                    assertEquals(home.id,restarted.loadAll().single().id)
                    restarted.save(home)
                    assertTrue(restarted.recoveryJournal.read(home.id)!!.committed)
                } else assertTrue(restarted.loadAll().isEmpty())
            }
        }
    }

    @Test fun `valid yaml payload with changed ownership is rejected when digest does not match`() {
        val original=snapshot();val changed=snapshot()
        SqliteHomeRepository(db).use { repo ->
            repo.recoveryJournal.write(original,true)
            val file=db.parent.resolve("recovery/homes/${original.id}.properties")
            val io=ym.dreamSkyblock.world.WorldArchiveIO()
            val fields=io.readProperties(file).toMutableMap()
            fields["snapshot"]=java.util.Base64.getEncoder().encodeToString(changed.toYaml().toByteArray(Charsets.UTF_8))
            io.writeProperties(file,fields)
            assertThrows(IllegalArgumentException::class.java){repo.recoveryJournal.read(original.id)}
        }
    }
}
