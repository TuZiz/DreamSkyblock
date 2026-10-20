package ym.dreamSkyblock.storage

import ym.dreamSkyblock.model.HomeFlag
import ym.dreamSkyblock.model.HomeTemplate
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Types
import java.util.UUID

/** JDBC-only repository. All multi-table mutations are atomic and preserve tombstones. */
class SqliteHomeRepository(val databasePath: Path) : AutoCloseable {
    private var connection: Connection? = null
    private var closed = false
    internal val recoveryJournal = HomeRecoveryJournal(databasePath.toAbsolutePath().parent.resolve("recovery/homes"))
    private val transactionEvidence = linkedMapOf<UUID, HomeSnapshot>()
    private var recoveryWriteFailed = false
    internal var transactionCheckpoint: (String) -> Unit = {}

    @Synchronized
    fun backupDatabase(reason: String): Path = SqliteDatabaseBackups(
        databasePath.toAbsolutePath().parent.resolve("backups/recovery")
    ).create(database(), reason)

    @Synchronized
    fun loadAll(): List<HomeSnapshot> = readHomes(false)

    @Synchronized
    fun loadIncludingDeleted(): List<HomeSnapshot> = readHomes(true)

    @Synchronized
    fun save(snapshot: HomeSnapshot) = transaction { database ->
        val existingStatus = status(database, snapshot.id)
        require(existingStatus != "DELETED") { "home ${snapshot.id}: 已删除；普通保存不能覆盖删除墓碑" }
        require(snapshot.status != "DELETED") { "home ${snapshot.id}: 删除必须使用 delete" }
        // A queued snapshot may precede a completed lifecycle change. Only explicit
        // lifecycle methods may alter the status of an already-persisted home.
        write(database, if (existingStatus == null) snapshot else snapshot.withStatus(existingStatus))
    }

    /** Import is a single transaction; an existing UUID, including a tombstone, is untouched. */
    @Synchronized
    fun importLegacy(snapshots: List<HomeSnapshot>) = transaction { database ->
        require(snapshots.map { it.id }.distinct().size == snapshots.size) { "旧数据包含重复 home_id" }
        snapshots.forEach { snapshot ->
            if (status(database, snapshot.id) == null) write(database, snapshot)
        }
    }

    @Synchronized
    fun delete(id: UUID) = updateStatus(id, "DELETED")

    @Synchronized
    fun updateStatus(id: UUID, newStatus: String) = transaction { database ->
        require(newStatus in HomeSnapshot.STATUSES) { "home $id: status 无效: $newStatus" }
        val existing = status(database, id) ?: error("home $id: 数据库中不存在，不能变更生命周期")
        require(existing != "DELETED" || newStatus == "DELETED") { "home $id: 已删除；必须显式 restoreSnapshot 才能恢复" }
        prepareEvidence(readHomes(true, id).single().withStatus(newStatus))
        database.prepareStatement("UPDATE homes SET status=? WHERE home_id=?").use {
            it.setString(1, newStatus); it.setString(2, id.toString()); check(it.executeUpdate() == 1)
        }
        database.prepareStatement("UPDATE home_members SET active=? WHERE home_id=?").use {
            it.setInt(1, if (newStatus == "DELETED") 0 else 1); it.setString(2, id.toString()); it.executeUpdate()
        }
    }

    /** Explicit recovery may replace a tombstone, after member uniqueness has been checked. */
    @Synchronized
    fun restoreSnapshot(snapshot: HomeSnapshot): HomeSnapshot = transaction { database ->
        val restored = snapshot.withStatus("ACTIVE")
        write(database, restored)
        restored
    }

    private fun readHomes(includingDeleted: Boolean, id: UUID? = null): List<HomeSnapshot> {
        val database = database()
        val condition = if (id != null) " WHERE home_id=?" else if (includingDeleted) "" else " WHERE status <> 'DELETED'"
        return database.prepareStatement("SELECT * FROM homes$condition ORDER BY home_id").use { statement ->
            if (id != null) statement.setString(1, id.toString())
            statement.executeQuery().use { result ->
                buildList {
                    while (result.next()) {
                        val id = parseUuid(result.getString("home_id"), "home_id")
                        val owners = database.prepareStatement("SELECT player_id, active FROM home_members WHERE home_id=? ORDER BY player_id").use { members ->
                            members.setString(1, id.toString())
                            members.executeQuery().use { rows ->
                                buildSet {
                                    while (rows.next()) {
                                        check(rows.getInt("active") == if (result.getString("status") == "DELETED") 0 else 1) {
                                            "home $id: status 与成员 active 状态不一致"
                                        }
                                        add(parseUuid(rows.getString("player_id"), "home_members.player_id"))
                                    }
                                }
                            }
                        }
                        val names = database.prepareStatement("SELECT player_id, player_name FROM home_owner_names WHERE home_id=?").use { names ->
                            names.setString(1, id.toString())
                            names.executeQuery().use { rows ->
                                buildMap { while (rows.next()) put(parseUuid(rows.getString(1), "home_owner_names.player_id"), rows.getString(2)) }
                            }
                        }
                        val flags = database.prepareStatement("SELECT flag, enabled FROM home_flags WHERE home_id=?").use { flags ->
                            flags.setString(1, id.toString())
                            flags.executeQuery().use { rows ->
                                buildMap {
                                    while (rows.next()) {
                                        val flag = runCatching { HomeFlag.valueOf(rows.getString(1)) }.getOrElse {
                                            throw IllegalStateException("home $id: 数据库包含未知权限 ${rows.getString(1)}", it)
                                        }
                                        put(flag, rows.getInt(2) == 1)
                                    }
                                }
                            }
                        }
                        val snapshot = HomeSnapshot(id, result.getString("owner_name"), result.getString("template_id"), point(result, "center"),
                            result.getDouble("border_size"), result.getInt("level"), result.getLong("experience"), result.getInt("border_level"),
                            result.getInt("member_limit"), owners, names, flags,
                            if (result.getObject("spawn_x") == null) null else point(result, "spawn"), result.getString("status"), SqliteValueRecords.read(database, id))
                        check(result.getString("world_key") == snapshot.worldKey) { "home $id: world_key 与 home_id 不匹配" }
                        add(snapshot)
                    }
                }
            }
        }
    }

    private fun write(database: Connection, snapshot: HomeSnapshot) {
        if (snapshot.status != "DELETED") {
            snapshot.owners.forEach { owner ->
                database.prepareStatement("SELECT home_id FROM home_members WHERE player_id=? AND active=1 AND home_id<>?").use {
                    it.setString(1, owner.toString()); it.setString(2, snapshot.id.toString())
                    it.executeQuery().use { result ->
                        require(!result.next()) { "home ${snapshot.id}: 成员 $owner 已归属空岛 ${result.getString(1)}，拒绝覆盖" }
                    }
                }
            }
        }
        prepareEvidence(snapshot)
        database.prepareStatement("""
            INSERT INTO homes (home_id, world_key, owner_name, template_id, center_x, center_y, center_z, center_yaw, center_pitch,
                spawn_x, spawn_y, spawn_z, spawn_yaw, spawn_pitch, border_size, level, experience, border_level, member_limit, status)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            ON CONFLICT(home_id) DO UPDATE SET world_key=excluded.world_key, owner_name=excluded.owner_name, template_id=excluded.template_id,
                center_x=excluded.center_x, center_y=excluded.center_y, center_z=excluded.center_z, center_yaw=excluded.center_yaw, center_pitch=excluded.center_pitch,
                spawn_x=excluded.spawn_x, spawn_y=excluded.spawn_y, spawn_z=excluded.spawn_z, spawn_yaw=excluded.spawn_yaw, spawn_pitch=excluded.spawn_pitch,
                border_size=excluded.border_size, level=excluded.level, experience=excluded.experience,
                border_level=excluded.border_level, member_limit=excluded.member_limit, status=excluded.status
        """.trimIndent()).use { statement ->
            statement.setString(1, snapshot.id.toString()); statement.setString(2, snapshot.worldKey)
            statement.setString(3, snapshot.ownerName); statement.setString(4, snapshot.templateId)
            bindPoint(statement, 5, snapshot.center)
            bindPoint(statement, 10, snapshot.spawn)
            statement.setDouble(15, snapshot.borderSize); statement.setInt(16, snapshot.level)
            statement.setLong(17, snapshot.experience); statement.setInt(18, snapshot.borderLevel)
            statement.setInt(19, snapshot.memberLimit); statement.setString(20, snapshot.status)
            check(statement.executeUpdate() == 1)
        }
        listOf("home_members", "home_owner_names", "home_flags").forEach { table ->
            database.prepareStatement("DELETE FROM $table WHERE home_id=?").use {
                it.setString(1, snapshot.id.toString()); it.executeUpdate()
            }
        }
        database.prepareStatement("INSERT INTO home_members(home_id,player_id,active) VALUES (?,?,?)").use { statement ->
            snapshot.owners.forEach { owner ->
                statement.setString(1, snapshot.id.toString()); statement.setString(2, owner.toString())
                statement.setInt(3, if (snapshot.status == "DELETED") 0 else 1); statement.addBatch()
            }
            statement.executeBatch()
        }
        database.prepareStatement("INSERT INTO home_owner_names(home_id,player_id,player_name) VALUES (?,?,?)").use { statement ->
            snapshot.ownerNames.forEach { (owner, name) ->
                statement.setString(1, snapshot.id.toString()); statement.setString(2, owner.toString()); statement.setString(3, name); statement.addBatch()
            }
            statement.executeBatch()
        }
        database.prepareStatement("INSERT INTO home_flags(home_id,flag,enabled) VALUES (?,?,?)").use { statement ->
            snapshot.flags.forEach { (flag, enabled) ->
                statement.setString(1, snapshot.id.toString()); statement.setString(2, flag.name); statement.setInt(3, if (enabled) 1 else 0); statement.addBatch()
            }
            statement.executeBatch()
        }
        SqliteValueRecords.write(database, snapshot)
    }

    private fun status(database: Connection, id: UUID): String? =
        database.prepareStatement("SELECT status FROM homes WHERE home_id=?").use { statement ->
            statement.setString(1, id.toString())
            statement.executeQuery().use { result -> if (result.next()) result.getString(1) else null }
        }

    private fun <T> transaction(block: (Connection) -> T): T {
        check(!recoveryWriteFailed) { "homes.db: 上次提交后的恢复凭据写入失败，已暂停后续写入；请修复磁盘并重启核对" }
        val database = database()
        transactionEvidence.clear()
        database.autoCommit = false
        try {
            val result = block(database)
            transactionCheckpoint("before-commit")
            database.commit()
            try {
                transactionCheckpoint("after-commit")
                transactionEvidence.values.forEach { recoveryJournal.write(it, true) }
            }
            catch (error: Exception) {
                recoveryWriteFailed = true
                throw IllegalStateException("homes.db: SQLite已提交但恢复凭据落盘失败；已暂停后续写入，重启后以SQLite核对", error)
            }
            return result
        } catch (error: Throwable) {
            runCatching { database.rollback() }.exceptionOrNull()?.let(error::addSuppressed)
            throw error
        } finally {
            database.autoCommit = true
            transactionEvidence.clear()
        }
    }

    private fun prepareEvidence(snapshot: HomeSnapshot) {
        recoveryJournal.write(snapshot, false)
        transactionEvidence[snapshot.id] = snapshot
    }

    private fun database(): Connection {
        check(!closed) { "homes.db: 数据库已关闭" }
        return connection ?: run {
            Files.createDirectories(databasePath.toAbsolutePath().parent)
            Class.forName("org.sqlite.JDBC")
            DriverManager.getConnection("jdbc:sqlite:${databasePath.toAbsolutePath()}").also { database ->
                try {
                    SqliteSchema.initialize(database, databasePath)
                    connection = database
                } catch (error: Throwable) {
                    runCatching { database.close() }.exceptionOrNull()?.let(error::addSuppressed)
                    throw IllegalStateException("无法初始化 $databasePath: ${error.message}", error)
                }
            }
        }
    }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        connection?.use { database ->
            database.createStatement().use { it.execute("PRAGMA wal_checkpoint(TRUNCATE)") }
        }
        connection = null
    }

    private fun parseUuid(value: String, field: String): UUID {
        val parsed = UUID.fromString(value)
        require(parsed.toString().equals(value, true)) { "homes.db: $field UUID 格式无效: $value" }
        return parsed
    }

    private fun point(result: ResultSet, prefix: String): HomeTemplate.Point = HomeTemplate.Point(
        result.getDouble("${prefix}_x"), result.getDouble("${prefix}_y"), result.getDouble("${prefix}_z"),
        result.getFloat("${prefix}_yaw"), result.getFloat("${prefix}_pitch"))

    private fun bindPoint(statement: PreparedStatement, first: Int, point: HomeTemplate.Point?) {
        if (point == null) (first until first + 5).forEach { statement.setNull(it, Types.REAL) }
        else {
            statement.setDouble(first, point.x); statement.setDouble(first + 1, point.y); statement.setDouble(first + 2, point.z)
            statement.setFloat(first + 3, point.yaw); statement.setFloat(first + 4, point.pitch)
        }
    }
}
