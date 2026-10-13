package ym.dreamSkyblock.world

import io.papermc.paper.ServerBuildInfo
import net.kyori.adventure.key.Key
import org.bukkit.World
import org.bukkit.plugin.java.JavaPlugin
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer
import java.util.logging.Level

/** 世界卸载平台边界。调用方负责 global 调度；停止阶段只能在生命周期线程调用。 */
class WorldUnloadAdapter(private val plugin: JavaPlugin) {
    private val canvasMethod = plugin.server.javaClass.methods.firstOrNull { method ->
        method.name == "unloadWorldAsync" && method.parameterTypes.contentEquals(
            arrayOf(World::class.java, Boolean::class.javaPrimitiveType, Consumer::class.java)
        )
    }
    private val futureMethod = plugin.server.javaClass.methods.firstOrNull { method ->
        method.name == "unloadWorld" && method.parameterTypes.contentEquals(
            arrayOf(World::class.java, Boolean::class.javaPrimitiveType, CompletableFuture::class.java)
        )
    }
    private val regionThreaded = ServerBuildInfo.buildInfo().isBrandCompatible(Key.key("papermc", "folia")) ||
        hasRegionizedServer()

    val mode: String = when {
        canvasMethod != null -> "Canvas async"
        futureMethod != null -> "Future async"
        regionThreaded -> "unsupported region-threaded unload"
        else -> "Paper/Purpur main-thread"
    }

    /** global 所有权线程。标准 Purpur API 必须在主线程执行，不能放入 IO 线程池。 */
    fun unload(world: World, save: Boolean, done: (Boolean) -> Unit) {
        val result = { success: Boolean ->
            if (plugin.isEnabled) {
                plugin.server.globalRegionScheduler.execute(plugin) {
                    if (plugin.isEnabled) done(success)
                }
            }
        }
        invoke(world, save, result)
    }

    /** onDisable 所有权线程；不注册已被禁用的插件任务。回调只允许清理本地状态。 */
    fun unloadDuringShutdown(world: World, done: (Boolean) -> Unit) = invoke(world, true, done)

    private fun invoke(world: World, save: Boolean, done: (Boolean) -> Unit) {
        if (world.players.isNotEmpty()) {
            done(false)
            return
        }
        try {
            val canvas = canvasMethod
            val futureApi = futureMethod
            when {
                canvas != null -> canvas.invoke(plugin.server, world, save, Consumer<Any?> { result ->
                    try {
                        done(result?.javaClass?.getMethod("isSuccess")?.invoke(result) == true)
                    } catch (error: ReflectiveOperationException) {
                        fail(world, error, done)
                    }
                })
                futureApi != null -> {
                    val future = CompletableFuture<Boolean>()
                    future.whenComplete { success, error ->
                        if (error != null) fail(world, error, done) else done(success == true)
                    }
                    futureApi.invoke(plugin.server, world, save, future)
                }
                regionThreaded -> {
                    plugin.logger.severe("世界卸载失败 ${world.name}: $mode；该平台未提供受支持的异步卸载 API")
                    done(false)
                }
                else -> {
                    check(plugin.server.isPrimaryThread) { "Paper/Purpur 世界卸载必须在主线程执行" }
                    done(plugin.server.unloadWorld(world, save))
                }
            }
        } catch (error: Exception) {
            fail(world, error, done)
        }
    }

    private fun fail(world: World, error: Throwable, done: (Boolean) -> Unit) {
        plugin.logger.log(Level.WARNING, "世界卸载失败 ${world.name}, mode=$mode", error)
        done(false)
    }

    private fun hasRegionizedServer(): Boolean = try {
        Class.forName("io.papermc.paper.threadedregions.RegionizedServer", false, plugin.server.javaClass.classLoader)
        true
    } catch (_: ClassNotFoundException) {
        false
    }
}
