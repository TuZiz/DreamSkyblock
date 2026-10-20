package ym.dreamSkyblock.storage

import ym.dreamSkyblock.world.WorldArchiveIO
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.time.Instant
import java.util.Base64
import java.util.UUID
import java.security.MessageDigest

/** Offline recovery evidence only; SQLite remains authoritative whenever its row exists. */
internal class HomeRecoveryJournal(private val root: Path) {
    private val io = WorldArchiveIO()
    data class Entry(val snapshot: HomeSnapshot, val committed: Boolean)

    fun write(snapshot: HomeSnapshot, committed: Boolean) {
        val payload = snapshot.toYaml().toByteArray(Charsets.UTF_8)
        io.writeProperties(path(snapshot.id), mapOf(
            "schema-version" to "1", "home-id" to snapshot.id.toString(),
            "phase" to if (committed) "COMMITTED" else "PREPARED",
            "updated-at" to Instant.now().toString(),
            "snapshot" to Base64.getEncoder().encodeToString(payload), "sha256" to hash(payload)
        ))
    }

    fun read(id: UUID): Entry? {
        val file = io.checked(path(id))
        if (!Files.exists(file, NOFOLLOW_LINKS)) return null
        val fields = io.readProperties(file)
        require(fields["schema-version"] == "1" && fields["home-id"] == id.toString()) { "归属恢复凭据无效: $file" }
        val phase = fields.getValue("phase")
        require(phase in setOf("PREPARED", "COMMITTED")) { "归属恢复凭据阶段无效: $file" }
        val payload = Base64.getDecoder().decode(fields.getValue("snapshot"))
        require(fields["sha256"] == hash(payload)) { "归属恢复凭据摘要不匹配: $file" }
        val snapshot = HomeSnapshot.fromYaml(String(payload, Charsets.UTF_8))
        require(snapshot.id == id) { "归属恢复凭据 home_id 不匹配: $file" }
        return Entry(snapshot, phase == "COMMITTED")
    }

    private fun path(id: UUID) = root.resolve("$id.properties")
    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
