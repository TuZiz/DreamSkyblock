package ym.dreamSkyblock.storage

import java.sql.Connection
import java.nio.file.Path

/** Versioned, strictly checked schema. An unknown database is never overwritten. */
internal object SqliteSchema {
    private val columns = mapOf(
        "homes" to setOf("home_id", "world_key", "owner_name", "template_id", "center_x", "center_y", "center_z", "center_yaw", "center_pitch",
            "spawn_x", "spawn_y", "spawn_z", "spawn_yaw", "spawn_pitch", "border_size", "level", "experience", "border_level", "member_limit", "status"),
        "home_members" to setOf("home_id", "player_id", "active"),
        "home_owner_names" to setOf("home_id", "player_id", "player_name"),
        "home_flags" to setOf("home_id", "flag", "enabled"),
        "home_value_records" to setOf("home_id", "source", "entry_id", "value", "revision")
    )

    fun initialize(connection: Connection, path: Path) {
        connection.createStatement().use { statement ->
            statement.executeQuery("PRAGMA journal_mode=WAL").use { result ->
                check(result.next() && result.getString(1).equals("wal", true)) { "homes.db: 无法启用 WAL" }
            }
            statement.execute("PRAGMA synchronous=FULL")
            statement.execute("PRAGMA foreign_keys=ON")
            statement.execute("PRAGMA busy_timeout=10000")
            statement.executeQuery("PRAGMA foreign_keys").use { result ->
                check(result.next() && result.getInt(1) == 1) { "homes.db: 无法启用外键约束" }
            }
            val version = statement.executeQuery("PRAGMA user_version").use { result -> result.next(); result.getInt(1) }
            when (version) {
                0 -> {
                    val existing = statement.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'").use {
                        buildList { while (it.next()) add(it.getString(1)) }
                    }
                    check(existing.isEmpty()) { "homes.db: schema-version=0 却包含未知表 $existing；拒绝覆盖" }
                    create(connection)
                }
                1 -> { validate(connection, false); SqliteValueMigration.upgrade(connection, path) }
                2 -> Unit
                else -> error("homes.db: 不支持 schema-version=$version（支持 1-2）")
            }
        }
        validate(connection)
    }

    private fun create(connection: Connection) {
        connection.autoCommit = false
        try {
            connection.createStatement().use { statement ->
                statement.execute("""
                    CREATE TABLE homes (
                        home_id TEXT PRIMARY KEY NOT NULL,
                        world_key TEXT NOT NULL UNIQUE,
                        owner_name TEXT NOT NULL,
                        template_id TEXT NOT NULL,
                        center_x REAL NOT NULL, center_y REAL NOT NULL, center_z REAL NOT NULL,
                        center_yaw REAL NOT NULL, center_pitch REAL NOT NULL,
                        spawn_x REAL, spawn_y REAL, spawn_z REAL, spawn_yaw REAL, spawn_pitch REAL,
                        border_size REAL NOT NULL CHECK(border_size > 0),
                        level INTEGER NOT NULL CHECK(level > 0),
                        experience INTEGER NOT NULL CHECK(experience >= 0),
                        border_level INTEGER NOT NULL CHECK(border_level > 0),
                        member_limit INTEGER NOT NULL CHECK(member_limit > 0),
                        status TEXT NOT NULL CHECK(status IN ('CREATING','ACTIVE','DELETED','RECOVERY')),
                        CHECK ((spawn_x IS NULL AND spawn_y IS NULL AND spawn_z IS NULL AND spawn_yaw IS NULL AND spawn_pitch IS NULL)
                            OR (spawn_x IS NOT NULL AND spawn_y IS NOT NULL AND spawn_z IS NOT NULL AND spawn_yaw IS NOT NULL AND spawn_pitch IS NOT NULL))
                    )
                """.trimIndent())
                statement.execute("""
                    CREATE TABLE home_members (
                        home_id TEXT NOT NULL REFERENCES homes(home_id) ON DELETE RESTRICT,
                        player_id TEXT NOT NULL,
                        active INTEGER NOT NULL CHECK(active IN (0,1)),
                        PRIMARY KEY(home_id, player_id)
                    )
                """.trimIndent())
                statement.execute("CREATE UNIQUE INDEX unique_active_home_owner ON home_members(player_id) WHERE active=1")
                statement.execute("""
                    CREATE TABLE home_owner_names (
                        home_id TEXT NOT NULL REFERENCES homes(home_id) ON DELETE RESTRICT,
                        player_id TEXT NOT NULL,
                        player_name TEXT NOT NULL,
                        PRIMARY KEY(home_id, player_id)
                    )
                """.trimIndent())
                statement.execute("""
                    CREATE TABLE home_flags (
                        home_id TEXT NOT NULL REFERENCES homes(home_id) ON DELETE RESTRICT,
                        flag TEXT NOT NULL,
                        enabled INTEGER NOT NULL CHECK(enabled IN (0,1)),
                        PRIMARY KEY(home_id, flag)
                    )
                """.trimIndent())
                SqliteValueRecords.create(connection)
                statement.execute("PRAGMA user_version=2")
            }
            connection.commit()
        } catch (error: Throwable) {
            runCatching { connection.rollback() }.exceptionOrNull()?.let(error::addSuppressed)
            throw error
        } finally {
            connection.autoCommit = true
        }
    }

    private fun validate(connection: Connection, values: Boolean = true) {
        connection.createStatement().use { statement ->
            columns.filterKeys { values || it != "home_value_records" }.forEach { (table, expected) ->
                val actual = statement.executeQuery("PRAGMA table_info($table)").use { result ->
                    buildSet { while (result.next()) add(result.getString("name")) }
                }
                check(actual == expected) { "homes.db: 表 $table 字段不匹配，实际=$actual，预期=$expected" }
            }
            val uniqueIndex = statement.executeQuery("SELECT sql FROM sqlite_master WHERE type='index' AND name='unique_active_home_owner'").use { result ->
                if (result.next()) result.getString(1) else null
            }
            check(uniqueIndex == "CREATE UNIQUE INDEX unique_active_home_owner ON home_members(player_id) WHERE active=1") {
                "homes.db: 缺少或修改了成员唯一归属约束"
            }
            if (values) {
                val primary = statement.executeQuery("PRAGMA table_info(home_value_records)").use { rows ->
                    buildMap { while (rows.next()) if (rows.getInt("pk") > 0) put(rows.getInt("pk"), rows.getString("name")) }
                }
                check(primary == mapOf(1 to "home_id", 2 to "source", 3 to "entry_id")) { "homes.db: 设施价值明细缺少唯一设施主键" }
            }
            statement.executeQuery("PRAGMA quick_check").use { result ->
                while (result.next()) check(result.getString(1) == "ok") { "homes.db: 完整性校验失败: ${result.getString(1)}" }
            }
            statement.executeQuery("PRAGMA foreign_key_check").use { result ->
                check(!result.next()) { "homes.db: 外键一致性校验失败，table=${result.getString(1)} row=${result.getString(2)}" }
            }
        }
    }
}
