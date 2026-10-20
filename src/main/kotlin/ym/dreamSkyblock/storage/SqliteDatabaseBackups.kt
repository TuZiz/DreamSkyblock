package ym.dreamSkyblock.storage

import ym.dreamSkyblock.world.WorldArchiveIO
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.sql.Connection
import java.sql.DriverManager
import java.time.Instant
import java.util.UUID

/** Called exclusively on the ordered database worker, outside a SQLite transaction. */
internal class SqliteDatabaseBackups(private val root: Path) {
    fun create(database: Connection, reason: String): Path {
        require(reason in setOf("startup", "periodic", "recovery"))
        val io = WorldArchiveIO()
        io.checked(root)
        Files.createDirectories(root)
        val token = UUID.randomUUID().toString()
        val stage = io.contained(root.resolve(".stage-$token.db"), root)
        val target = root.resolve("homes-${Instant.now().toEpochMilli()}-$reason-$token.db")
        database.createStatement().use { it.execute("VACUUM INTO '${stage.toString().replace("'", "''")}'") }
        DriverManager.getConnection("jdbc:sqlite:$stage").use { copy ->
            copy.createStatement().use { statement ->
                statement.executeQuery("PRAGMA quick_check").use { rows ->
                    check(rows.next() && rows.getString(1) == "ok" && !rows.next()) { "SQLite备份完整性检查失败: $stage" }
                }
                statement.executeQuery("PRAGMA foreign_key_check").use { check(!it.next()) { "SQLite备份外键检查失败: $stage" } }
            }
        }
        FileChannel.open(stage, StandardOpenOption.WRITE).use { it.force(true) }
        io.moveAbsent(stage, target)
        // Only rotate this feature's completed snapshots, after a new independent copy is verified.
        Files.list(root).use { files ->
            files.filter { it.fileName.toString().matches(Regex("homes-\\d+-(startup|periodic|recovery)-[0-9a-f-]{36}\\.db")) }
                .sorted(compareByDescending { it.fileName.toString() }).skip(8).toList().forEach { Files.delete(io.contained(it, root)) }
        }
        return target
    }
}
