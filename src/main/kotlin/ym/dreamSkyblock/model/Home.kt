package ym.dreamSkyblock.model

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class Home(
    val id: UUID,
    val ownerName: String,
    val templateId: String,
    val center: HomeTemplate.Point,
    var borderSize: Double,
    var level: Int = 1,
    var experience: Long = 0L,
    var borderLevel: Int = 1,
    var memberLimit: Int = 4
) {
    val owners: MutableSet<UUID> = ConcurrentHashMap.newKeySet()
    val ownerNames: MutableMap<UUID, String> = ConcurrentHashMap()
    val flags: MutableMap<HomeFlag, Boolean> = ConcurrentHashMap()
    @Volatile var loadedWorldName: String? = null

    fun isOwner(playerId: UUID): Boolean = owners.contains(playerId)
    fun hasRoom(): Boolean = owners.size < memberLimit
}
