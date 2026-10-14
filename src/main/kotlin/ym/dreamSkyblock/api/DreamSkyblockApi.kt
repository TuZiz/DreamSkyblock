package ym.dreamSkyblock.api

import java.util.UUID

/** 通过 Bukkit ServicesManager 获取；只读、无IO，可跨线程查询不可变快照。 */
interface DreamSkyblockApi {
    fun isReady(): Boolean
    fun findHome(homeId: UUID): HomeView?
    fun findHomeByMember(playerId: UUID): HomeView?
    fun allHomes(): List<HomeView>
    fun isMember(homeId: UUID, playerId: UUID): Boolean
    fun permission(homeId: UUID, playerId: UUID, flag: String): Boolean
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
