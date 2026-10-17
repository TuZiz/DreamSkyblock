package ym.dreamSkyblock.value

import ym.dreamSkyblock.api.HomeValueKey
import ym.dreamSkyblock.api.HomeValueRecord
import ym.dreamSkyblock.api.HomeValueStatus
import ym.dreamSkyblock.model.Home
import ym.dreamSkyblock.service.HomeLevelService

/** Pure candidate mutation. Only a committed candidate may replace the live home. */
object HomeValueLogic {
    fun status(old: HomeValueRecord?, next: HomeValueRecord): HomeValueStatus {
        if (old != null && next.revision < old.revision) return HomeValueStatus.STALE
        if (old != null && next.revision == old.revision) {
            require(next == old) { "Facility revision conflict: revision=${next.revision}" }
            return HomeValueStatus.UNCHANGED
        }
        return HomeValueStatus.APPLIED
    }

    fun update(home: Home, key: HomeValueKey, value: Long, revision: Long, levels: HomeLevelService): HomeValueStatus {
        val next = HomeValueRecord(value, revision)
        val old = home.valueRecords[key]
        val status = status(old, next)
        if (status != HomeValueStatus.APPLIED) return status
        val total = Math.addExact(Math.subtractExact(home.experience, old?.value ?: 0), value)
        home.valueRecords[key] = next
        home.experience = total
        levels.sanitize(home)
        return HomeValueStatus.APPLIED
    }

    fun normalized(records: Map<HomeValueKey, HomeValueRecord>?, total: Long): Map<HomeValueKey, HomeValueRecord> {
        if (records == null) return if (total > 0) mapOf(HomeValueKey.LEGACY to HomeValueRecord(total, 0)) else emptyMap()
        require(records.values.fold(0L) { sum, entry -> Math.addExact(sum, entry.value) } == total) {
            "设施价值明细与空岛总价值不一致"
        }
        return records
    }
}
