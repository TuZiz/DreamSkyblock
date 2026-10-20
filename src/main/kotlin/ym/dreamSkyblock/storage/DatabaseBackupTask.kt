package ym.dreamSkyblock.storage

import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import org.bukkit.plugin.java.JavaPlugin
import java.util.concurrent.atomic.AtomicBoolean
import java.util.logging.Level

/** Owns one timer; SQLite snapshots run on HomeStorage's serialized IO worker. */
class DatabaseBackupTask(private val plugin: JavaPlugin, private val storage: HomeStorage) {
    private var task: ScheduledTask? = null
    private val busy = AtomicBoolean(false)
    @Volatile private var stopped = false

    fun start() {
        if (task != null || stopped) return
        task = plugin.server.globalRegionScheduler.runAtFixedRate(plugin, {
            if (!busy.compareAndSet(false, true)) return@runAtFixedRate
            storage.backupDatabase("periodic").whenComplete { _, error ->
                busy.set(false)
                if (error != null && !stopped) plugin.logger.log(Level.SEVERE, "homes.db 定期一致性备份失败；保留之前有效备份", error)
            }
        }, 18_000L, 18_000L)
    }

    fun stop() {
        stopped = true
        task?.cancel()
        task = null
    }
}
