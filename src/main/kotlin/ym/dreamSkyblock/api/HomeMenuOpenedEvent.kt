package ym.dreamSkyblock.api

import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import java.util.UUID

/** Paper owner thread; sent only after the requested island information screen is actually visible. */
class HomeMenuOpenedEvent(val homeId: UUID, val playerId: UUID, val menu: String) : Event() {
    override fun getHandlers() = HANDLERS
    companion object {
        private val HANDLERS = HandlerList()
        @JvmStatic fun getHandlerList() = HANDLERS
    }
}
