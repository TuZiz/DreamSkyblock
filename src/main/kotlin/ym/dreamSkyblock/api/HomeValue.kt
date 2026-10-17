package ym.dreamSkyblock.api

import java.util.UUID

/** Stable identity within one home. The source is the integrating plugin's ID. */
data class HomeValueKey(val source: String, val id: String) {
    init {
        require(source.matches(Regex("[a-z0-9][a-z0-9_.-]{0,63}"))) { "Invalid value source: $source" }
        require(id.isNotBlank() && id.length <= 256 && id.none(Char::isISOControl)) { "Invalid facility ID" }
    }

    companion object {
        @JvmStatic fun block(source: String, x: Int, y: Int, z: Int) = HomeValueKey(source, "block:$x:$y:$z")
        val LEGACY = HomeValueKey("dreamskyblock-legacy", "imported-experience")
    }
}

/** Zero-value records are retained as tombstones to reject delayed placement retries. */
data class HomeValueRecord(val value: Long, val revision: Long) {
    init { require(value >= 0 && revision >= 0) { "Value and revision must be nonnegative" } }
}

enum class HomeValueStatus { APPLIED, UNCHANGED, STALE }

data class HomeValueResult(val homeId: UUID, val status: HomeValueStatus, val totalValue: Long,
                           val level: Int, val borderSize: Double, val record: HomeValueRecord)
