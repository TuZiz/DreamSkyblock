package ym.dreamSkyblock.storage

import ym.dreamSkyblock.api.HomeValueKey
import ym.dreamSkyblock.api.HomeValueRecord
import java.sql.Connection
import java.util.UUID

internal object SqliteValueRecords {
    fun create(database: Connection) {
        database.createStatement().use { it.execute("""
            CREATE TABLE home_value_records (
                home_id TEXT NOT NULL REFERENCES homes(home_id) ON DELETE RESTRICT,
                source TEXT NOT NULL, entry_id TEXT NOT NULL,
                value INTEGER NOT NULL CHECK(value >= 0),
                revision INTEGER NOT NULL CHECK(revision >= 0),
                PRIMARY KEY(home_id, source, entry_id)
            )
        """.trimIndent()) }
    }

    fun read(database: Connection, id: UUID): Map<HomeValueKey, HomeValueRecord> =
        database.prepareStatement("SELECT source,entry_id,value,revision FROM home_value_records WHERE home_id=?").use { statement ->
            statement.setString(1, id.toString())
            statement.executeQuery().use { rows -> buildMap {
                while (rows.next()) put(HomeValueKey(rows.getString(1), rows.getString(2)), HomeValueRecord(rows.getLong(3), rows.getLong(4)))
            } }
        }

    fun write(database: Connection, snapshot: HomeSnapshot) {
        database.prepareStatement("DELETE FROM home_value_records WHERE home_id=?").use {
            it.setString(1, snapshot.id.toString()); it.executeUpdate()
        }
        database.prepareStatement("INSERT INTO home_value_records(home_id,source,entry_id,value,revision) VALUES (?,?,?,?,?)").use { statement ->
            snapshot.valueRecords.forEach { (key, record) ->
                statement.setString(1, snapshot.id.toString()); statement.setString(2, key.source); statement.setString(3, key.id)
                statement.setLong(4, record.value); statement.setLong(5, record.revision); statement.addBatch()
            }
            statement.executeBatch()
        }
    }
}
