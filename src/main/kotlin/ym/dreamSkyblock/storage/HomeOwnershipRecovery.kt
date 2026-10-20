package ym.dreamSkyblock.storage

import ym.dreamSkyblock.world.NativeWorldFiles
import ym.dreamSkyblock.world.WorldArchiveIO
import java.nio.file.Path
import java.nio.file.Files
import java.time.Instant
import java.util.UUID

/** Offline startup reconciliation. Never changes world files or guesses ownership from player names. */
internal class HomeOwnershipRecovery(private val reportPath: Path) {
    fun recover(repository: SqliteHomeRepository, worlds: NativeWorldFiles): List<String> {
        val io = WorldArchiveIO()
        val previous = if (Files.exists(io.checked(reportPath))) io.readProperties(reportPath) else emptyMap()
        require(previous.isEmpty() || previous["schema-version"] == "1") { "待处理世界清单版本不支持: $reportPath" }
        val existing = repository.loadIncludingDeleted()
        repository.backupDatabase("startup")
        val known = existing.associateBy { it.id }
        val occupied = existing.filter { it.status != "DELETED" }.flatMap { it.owners }.toSet()
        // SQLite wins over interrupted/old evidence, including permanent deletion tombstones.
        existing.forEach { repository.recoveryJournal.write(it, true) }
        val pending = linkedMapOf<String, String>()
        val candidates = mutableListOf<HomeSnapshot>()
        for (directory in worlds.audit(known.keys)) {
            val name = directory.fileName.toString()
            try {
                val raw = name.removePrefix("dreamskyblock_")
                require(raw.matches(Regex("[0-9a-f]{32}"))) { "目录名称不是标准home_id" }
                val id = UUID.fromString(raw.replace(Regex("(.{8})(.{4})(.{4})(.{4})(.{12})"), "$1-$2-$3-$4-$5"))
                val evidence = repository.recoveryJournal.read(id) ?: error("缺少归属恢复凭据，禁止推测主人")
                check(evidence.committed) { "上次数据库提交结果不明确，需人工核对" }
                val snapshot = evidence.snapshot
                check(snapshot.status != "DELETED") { "存在删除墓碑，禁止自动复活" }
                check(snapshot.status != "RECOVERY") { "原记录已处于RECOVERY，需人工核对" }
                check(worlds.hasWorldData(id)) { "世界结构不完整，禁止重新生成代替" }
                if (snapshot.status == "CREATING") check(worlds.creationCompleted(id)) { "首次生成保存未完成，保留现场" }
                check(snapshot.owners.none(occupied::contains)) { "成员已归属其他空岛，禁止覆盖现有归属" }
                candidates += snapshot.withStatus("ACTIVE")
            } catch (error: Exception) {
                pending[name] = "${error.javaClass.simpleName}: ${error.message}"
            }
        }
        // Reject ALL mutually conflicting candidates rather than restoring whichever sorts first.
        val duplicateOwners = candidates.flatMap { it.owners }.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        val safe = candidates.filter { snapshot ->
            if (snapshot.owners.any(duplicateOwners::contains)) {
                pending[worlds.worldName(snapshot.id)] = "多份恢复凭据成员冲突，全部保留待人工核对"
                false
            } else true
        }
        if (safe.isNotEmpty()) {
            repository.backupDatabase("recovery")
            repository.importLegacy(safe) // One transaction, final member uniqueness enforced by SQLite.
        }
        val now = Instant.now().toString()
        val report = linkedMapOf("schema-version" to "1", "checked-at" to now, "pending-count" to pending.size.toString())
        pending.forEach { (name, reason) ->
            report["pending.$name"] = reason
            report["first-seen.$name"] = previous["first-seen.$name"] ?: now
        }
        io.writeProperties(reportPath, report)
        return safe.map { "已从持久归属凭据恢复空岛 home_id=${it.id}；未修改世界文件" } +
            pending.map { (name, reason) -> "待处理无归属世界 $name: $reason；原目录保留；清单=$reportPath" }
    }
}
