package ym.dreamSkyblock.api

import ym.dreamSkyblock.model.Home
import ym.dreamSkyblock.model.HomeFlag
import ym.dreamSkyblock.service.HomeManager
import ym.dreamSkyblock.storage.HomeSnapshot
import java.util.Collections
import java.util.Locale
import java.util.UUID

class DefaultDreamSkyblockApi(private val homes: HomeManager) : DreamSkyblockApi {
    override fun isReady() = homes.ready
    override fun findHome(homeId: UUID): HomeView? = if (isReady()) homes.byId(homeId)?.let(::view) else null
    override fun findHomeByMember(playerId: UUID): HomeView? = if (isReady()) homes.byPlayer(playerId)?.let(::view) else null
    override fun allHomes(): List<HomeView> = if (isReady()) Collections.unmodifiableList(homes.all().map(::view)) else emptyList()
    override fun isMember(homeId: UUID, playerId: UUID) = findHome(homeId)?.members?.contains(playerId) == true
    override fun permission(homeId: UUID, playerId: UUID, flag: String): Boolean {
        val home = findHome(homeId) ?: return false
        val type = HomeFlag.entries.firstOrNull { it.name.equals(flag, true) } ?: return false
        if (home.status != "ACTIVE") return false
        return if (type.category == HomeFlag.Category.STRANGER && playerId in home.members) true
        else home.flags[type.name.lowercase(Locale.ROOT)] ?: type.defaultValue
    }

    private fun view(home: Home): HomeView {
        val snapshot = HomeSnapshot.fromHome(home)
        return HomeView(snapshot.id, snapshot.worldKey, snapshot.templateId, snapshot.level, snapshot.experience,
            snapshot.borderSize, snapshot.memberLimit, snapshot.owners, snapshot.ownerNames,
            Collections.unmodifiableMap(HomeFlag.entries.associate { it.name.lowercase(Locale.ROOT) to (snapshot.flags[it] ?: it.defaultValue) }),
            snapshot.spawn?.let { HomePoint(it.x, it.y, it.z, it.yaw, it.pitch) }, snapshot.status)
    }
}
