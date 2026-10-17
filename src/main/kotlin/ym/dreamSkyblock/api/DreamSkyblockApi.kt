package ym.dreamSkyblock.api

import java.util.UUID
import java.util.concurrent.CompletableFuture

/** ServicesManager API: immutable read snapshots; value writes are asynchronous and may be called from any thread. */
interface DreamSkyblockApi {
    fun isReady(): Boolean
    fun findHome(homeId: UUID): HomeView?
    fun findHomeByMember(playerId: UUID): HomeView?
    fun allHomes(): List<HomeView>
    fun isMember(homeId: UUID, playerId: UUID): Boolean
    fun permission(homeId: UUID, playerId: UUID, flag: String): Boolean
    fun findHomeByWorldKey(worldKey: String): HomeView?
    fun valueRecords(homeId: UUID): Map<HomeValueKey, HomeValueRecord>
    fun setValue(homeId: UUID, key: HomeValueKey, value: Long, revision: Long): CompletableFuture<HomeValueResult>
    fun removeValue(homeId: UUID, key: HomeValueKey, revision: Long): CompletableFuture<HomeValueResult>
}

data class HomeView(
    val homeId: UUID,
    val worldKey: String,
    val templateId: String,
    val level: Int,
    val experience: Long,
    val borderSize: Double,
    val memberLimit: Int,
    val members: Set<UUID>,
    val memberNames: Map<UUID, String>,
    val flags: Map<String, Boolean>,
    val spawn: HomePoint?,
    val status: String
)

data class HomePoint(val x: Double, val y: Double, val z: Double, val yaw: Float, val pitch: Float)
