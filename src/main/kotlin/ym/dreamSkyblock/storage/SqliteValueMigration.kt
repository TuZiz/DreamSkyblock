package ym.dreamSkyblock.storage

import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.util.UUID

internal object SqliteValueMigration {
    /** VACUUM INTO includes committed WAL content. Verify the independent backup before touching schema v1. */
    fun upgrade(database: Connection, path: Path) {
        val backup = path.toAbsolutePath().parent.resolve("backups/homes-before-values-${UUID.randomUUID()}.db")
        Files.createDirectories(backup.parent)
        database.createStatement().use { it.execute("VACUUM INTO '${backup.toString().replace("'", "''")}'") }
        DriverManager.getConnection("jdbc:sqlite:$backup").use { copy ->
            copy.createStatement().use { statement ->
                statement.executeQuery("PRAGMA quick_check").use { rows -> while (rows.next()) check(rows.getString(1) == "ok") }
                statement.executeQuery("PRAGMA user_version").use { rows -> check(rows.next() && rows.getInt(1) == 1) }
            }
        }
        database.autoCommit = false
        try {
            SqliteValueRecords.create(database)
            database.createStatement().use { statement ->
                statement.execute("INSERT INTO home_value_records(home_id,source,entry_id,value,revision) SELECT home_id,'dreamskyblock-legacy','imported-experience',experience,0 FROM homes WHERE experience>0")
                statement.execute("PRAGMA user_version=2")
            }
            database.commit()
            java.util.logging.Logger.getLogger("DreamSkyblock").info("homes.db: 设施价值明细已迁移至 schema=2；原数据库备份=$backup")
        } catch (error: Throwable) {
            runCatching { database.rollback() }.exceptionOrNull()?.let(error::addSuppressed)
            throw IllegalStateException("homes.db: 价值明细迁移失败，原版备份=$backup", error)
        } finally { database.autoCommit = true }
    }
}
