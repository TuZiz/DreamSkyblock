package ym.dreamSkyblock.api

import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import java.util.UUID

/** Paper main thread, after a successful durable creation or membership commit. */
class HomeMembershipCommittedEvent(val homeId: UUID, val playerId: UUID) : Event() {
    override fun getHandlers(): HandlerList = HANDLERS
    companion object {
        private val HANDLERS = HandlerList()
        @JvmStatic fun getHandlerList(): HandlerList = HANDLERS
    }
}
