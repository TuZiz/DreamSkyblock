package ym.dreamSkyblock.world

import org.bukkit.Server
import org.bukkit.World
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer

/** Call on the global scheduler. Canvas completes the callback after releasing world storage. */
internal class RuntimeWorldUnload(private val server: Server) {
    private val canvasUnload = try {
        Server::class.java.getMethod("unloadWorldAsync", World::class.java, Boolean::class.javaPrimitiveType, Consumer::class.java)
    } catch (_: NoSuchMethodException) {
        null
    }

    val asynchronous: Boolean get() = canvasUnload != null

    fun saveAndUnload(world: World): CompletableFuture<Boolean> {
        val unload = canvasUnload ?: return CompletableFuture.completedFuture(server.unloadWorld(world, true))
        val completion = CompletableFuture<Boolean>()
        unload.invoke(server, world, true, Consumer<Enum<*>> { result ->
            completion.complete(result.name == "SUCCESS")
        })
        return completion
    }
}
