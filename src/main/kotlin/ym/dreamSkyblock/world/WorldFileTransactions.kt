package ym.dreamSkyblock.world

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.util.Base64
import java.util.UUID

/** Crash-recoverable replacement. Callers must keep the world unloaded until metadata is acknowledged. */
internal class WorldFileTransactions(
    private val root: Path,
    private val namespace: Path,
    private val io: WorldArchiveIO,
    private val worldName: (UUID) -> String,
    private val validateWorld: (Path) -> Boolean,
    private val diagnostic: (String) -> Unit,
    private val checkpoint: (String) -> Unit
) {
    fun replace(id: UUID, source: Path, backupId: String, metadata: String) {
        require(journals().none { read(it)["home-id"] == id.toString() }) {
            "空岛 $id 存在未完成世界恢复事务，请先处理恢复元数据"
        }
        val token = UUID.randomUUID().toString()
        val target = namespace.resolve(worldName(id))
        val stage = namespace.resolve(".restore-$id-$token")
        val rollback = namespace.resolve(".rollback-$id-$token")
        io.checked(target)
        if (Files.exists(target, NOFOLLOW_LINKS)) require(Files.isDirectory(target, NOFOLLOW_LINKS)) {
            "当前世界路径不是目录，拒绝恢复: $target"
        }
        io.copyVerified(source, stage)
        require(validateWorld(stage)) { "恢复暂存世界无效: $stage" }
        val journal = root.resolve("$token.properties")
        val values = mutableMapOf(
            "schema-version" to "1", "home-id" to id.toString(), "state" to "PREPARED",
            "stage" to stage.fileName.toString(), "rollback" to rollback.fileName.toString(),
            "had-target" to Files.exists(target, NOFOLLOW_LINKS).toString(),
            "old-world-sha256" to if (Files.exists(target, NOFOLLOW_LINKS)) io.fingerprint(target) else "",
            "backup-id" to backupId, "world-sha256" to io.fingerprint(stage),
            "metadata" to Base64.getEncoder().encodeToString(metadata.toByteArray(StandardCharsets.UTF_8))
        )
        io.writeProperties(journal, values)
        checkpoint("prepared")
        if (values["had-target"] == "true") io.moveAbsent(target, rollback)
        checkpoint("old-moved")
        values["state"] = "OLD_MOVED"
        io.writeProperties(journal, values)
        io.moveAbsent(stage, target)
        checkpoint("new-moved")
        values["state"] = "COMMITTED"
        io.writeProperties(journal, values)
        checkpoint("committed")
    }

    fun recover() {
        for (journal in journals()) {
            val values = read(journal)
            val id = UUID.fromString(values.getValue("home-id"))
            val target = namespace.resolve(worldName(id))
            val stage = transactionPath(values.getValue("stage"), ".restore-$id-")
            val rollback = transactionPath(values.getValue("rollback"), ".rollback-$id-")
            val state = values.getValue("state")
            if (state == "COMMITTED" && validReplacement(target, values)) {
                diagnostic("恢复空岛 $id 已提交的世界事务，等待 SQLite 元数据确认: $journal")
                continue
            }
            require(state in setOf("PREPARED", "OLD_MOVED", "COMMITTED", "ROLLING_BACK", "ROLLED_BACK", "ACKNOWLEDGED")) {
                "未知世界事务状态 $state: $journal"
            }
            if (state == "ACKNOWLEDGED" || state == "ROLLED_BACK") {
                closeJournal(journal, state.lowercase())
                continue
            }
            io.writeProperties(journal, values + ("state" to "ROLLING_BACK"))
            // Even if the crash happened between the rename and journal update, path presence identifies the move.
            if (Files.exists(rollback, NOFOLLOW_LINKS)) {
                require(io.fingerprint(rollback) == values.getValue("old-world-sha256")) { "世界事务回滚副本与原摘要不符: $rollback；保留所有目录并停止恢复" }
                if (Files.exists(target, NOFOLLOW_LINKS)) {
                    require(!Files.exists(stage, NOFOLLOW_LINKS)) {
                        "恢复事务同时存在目标、暂存和回滚目录，无法安全判定: $journal"
                    }
                    io.moveAbsent(target, stage)
                }
                io.moveAbsent(rollback, target)
            } else if (values["had-target"] == "true") {
                require(io.fingerprint(target) == values.getValue("old-world-sha256") &&
                    state in setOf("PREPARED", "ROLLING_BACK")) {
                    "恢复事务缺少有效旧世界: $journal；不删除任何存档"
                }
            } else if (Files.exists(target, NOFOLLOW_LINKS)) {
                require(!Files.exists(stage, NOFOLLOW_LINKS)) { "无旧世界恢复事务存在冲突目录: $journal" }
                io.moveAbsent(target, stage)
            }
            io.writeProperties(journal, values + ("state" to "ROLLED_BACK"))
            closeJournal(journal, "rolled_back")
            diagnostic("空岛 $id 未完成世界恢复事务已安全回滚；暂存副本保留在 $stage")
        }
    }

    fun pendingRestores(): Map<UUID, String> = journals().mapNotNull { journal ->
        val values = read(journal)
        if (values["state"] != "COMMITTED") return@mapNotNull null
        val id = UUID.fromString(values.getValue("home-id"))
        require(validReplacement(namespace.resolve(worldName(id)), values)) { "已提交的恢复世界校验失败: $journal" }
        id to decodeMetadata(values, journal)
    }.toMap()

    fun assertReady(id: UUID) {
        require(journals().none { read(it)["home-id"] == id.toString() }) {
            "空岛 $id 有未完成的世界恢复事务；完成恢复及 SQLite 元数据确认前禁止加载或存档操作"
        }
    }

    fun hasPending(id: UUID): Boolean = journals().any { read(it)["home-id"] == id.toString() }

    fun acknowledgeRestore(id: UUID) {
        val matching = journals().filter { read(it)["home-id"] == id.toString() }
        require(matching.size == 1) { "空岛 $id 恢复确认需要一个唯一待提交事务，实际 ${matching.size}" }
        val journal = matching.single()
        val values = read(journal)
        require(values["state"] == "COMMITTED" && validReplacement(namespace.resolve(worldName(id)), values)) {
            "空岛 $id 世界恢复尚未安全提交: $journal"
        }
        io.writeProperties(journal, values + ("state" to "ACKNOWLEDGED"))
        closeJournal(journal, "acknowledged")
    }

    private fun decodeMetadata(values: Map<String, String>, journal: Path): String = try {
        String(Base64.getDecoder().decode(values.getValue("metadata")), StandardCharsets.UTF_8)
    } catch (error: IllegalArgumentException) {
        throw IllegalStateException("恢复事务元数据损坏: $journal", error)
    }

    private fun read(journal: Path): Map<String, String> = io.readProperties(journal).also { values ->
        require(values["schema-version"] == "1") { "不支持的世界事务版本: $journal" }
        UUID.fromString(values.getValue("home-id"))
    }

    private fun validReplacement(target: Path, values: Map<String, String>): Boolean =
        validateWorld(target) && io.fingerprint(target) == values.getValue("world-sha256")

    private fun transactionPath(name: String, prefix: String): Path {
        require(name.startsWith(prefix) && name.length == prefix.length + 36) { "非法事务目录名称: $name" }
        UUID.fromString(name.removePrefix(prefix))
        return io.contained(namespace.resolve(name), namespace)
    }

    private fun closeJournal(journal: Path, result: String) =
        io.moveAbsent(journal, journal.resolveSibling("${journal.fileName}.$result"))

    private fun journals(): List<Path> {
        io.checked(root)
        if (!Files.exists(root, NOFOLLOW_LINKS)) return emptyList()
        return Files.list(root).use { stream ->
            stream.filter { it.fileName.toString().endsWith(".properties") }.sorted().toList().onEach(io::checked)
        }
    }
}
