package ym.dreamSkyblock.world

import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.util.UUID

/** Prepares only plugin-owned disposable legacy roots; Paper itself converts and consumes them. */
internal class LegacyWorldImport(
    private val pluginRoot: Path,
    private val baseWorld: Path,
    private val io: WorldArchiveIO,
    private val worldName: (UUID) -> String,
    private val isNativeWorld: (Path) -> Boolean,
    private val readBackup: (UUID, String) -> String,
    private val sources: (UUID, Path?) -> List<Path>,
    private val metadata: (UUID, Path?) -> String,
    private val backupSource: (UUID, Path, String) -> String,
    private val diagnostic: (String) -> Unit
) {
    fun prepare(id: UUID, legacyHomeDir: Path?, metadataOverride: String? = null): Boolean {
        val name = worldName(id)
        val target = io.contained(baseWorld.resolve("dimensions/dreamskyblock/$name"), baseWorld)
        val serverRoot = requireNotNull(baseWorld.parent) { "基础世界缺少服务器父目录: $baseWorld" }
        val stage = io.contained(serverRoot.resolve("dreamskyblock_$name"), serverRoot)
        val marker = io.contained(pluginRoot.resolve("data/legacy-imports/$id.properties"), pluginRoot)
        if (Files.exists(marker, NOFOLLOW_LINKS)) {
            val values = io.readProperties(marker)
            require(values["schema-version"] == "1" && values["home-id"] == id.toString() &&
                values["stage-name"] == stage.fileName.toString()) { "旧世界导入所有权标记无效: $marker" }
            val backupId = values.getValue("primary-backup")
            readBackup(id, backupId)
            val source = io.contained(pluginRoot.resolve("backups/$id/$backupId/world"), pluginRoot)
            require(io.fingerprint(source) == values.getValue("world-sha256")) { "旧世界导入备份摘要不一致: $marker" }
            if (isNativeWorld(target) && !Files.exists(stage, NOFOLLOW_LINKS)) return false
            // Paper可能在迁移中途移动了部分文件。只隔离具有本插件持久所有权标记的副本，永不删除。
            if (Files.exists(target, NOFOLLOW_LINKS)) retainInterrupted(target, id)
            if (Files.exists(stage, NOFOLLOW_LINKS) && io.fingerprint(stage) != values.getValue("world-sha256")) retainInterrupted(stage, id)
            publishStage(stage, source, values.getValue("world-sha256"))
            diagnostic("空岛 $id 重试已备份的 Paper 旧世界导入，暂存 $stage；原源未修改")
            return true
        }
        if (isNativeWorld(target)) return false
        require(!Files.exists(target, NOFOLLOW_LINKS)) { "空岛 $id 原生世界存在但不完整且无导入所有权标记，拒绝覆盖: $target" }
        val candidates = sources(id, legacyHomeDir)
        if (candidates.isEmpty() || candidates.any(isNativeWorld)) return false
        require(!Files.exists(stage, NOFOLLOW_LINKS)) {
            "Paper 旧世界导入目录已存在且无本插件所有权标记，拒绝让 Paper 删除该目录: $stage"
        }
        val metadata = metadataOverride ?: metadata(id, legacyHomeDir)
        // backupSource validates each source before producing a sealed archive.
        val backups = candidates.associateWith { backupSource(id, it, metadata) }
        val fingerprints = candidates.associateWith(io::fingerprint)
        require(fingerprints.values.distinct().size == 1) {
            "空岛 $id 多个旧世界存档冲突，拒绝选择；原源和各自备份保留: " +
                backups.entries.joinToString { "${it.key} -> ${it.value}" }
        }
        val source = candidates.first()
        val sourceHash = fingerprints.getValue(source)
        io.writeProperties(marker, mapOf(
            "schema-version" to "1", "home-id" to id.toString(), "stage-name" to stage.fileName.toString(),
            "primary-backup" to backups.getValue(source), "world-sha256" to sourceHash,
            "sources" to candidates.joinToString("\n"), "backups" to backups.values.joinToString("\n")
        ))
        // Use the verified immutable backup instead of reading a potentially changing original again.
        val verifiedSource = pluginRoot.resolve("backups/$id/${backups.getValue(source)}/world")
        publishStage(stage, verifiedSource, sourceHash)
        diagnostic("空岛 $id 已完整备份，准备 Paper 官方旧世界导入: $stage；原源及备份保留")
        return true
    }

    private fun retainInterrupted(path: Path, id: UUID) {
        val quarantine = path.resolveSibling(".failed-import-$id-${UUID.randomUUID()}")
        io.moveAbsent(path, quarantine)
        diagnostic("空岛 $id 已隔离中断的官方导入副本 $quarantine；从经过摘要验证的迁移备份重试，所有副本均保留")
    }

    private fun publishStage(stage: Path, source: Path, expectedHash: String) {
        if (Files.exists(stage, NOFOLLOW_LINKS)) {
            require(io.fingerprint(stage) == expectedHash) {
                "旧世界导入暂存内容与所有权摘要不符，拒绝覆盖或交给 Paper: $stage"
            }
            return
        }
        val temporary = stage.resolveSibling(".legacy-import-${UUID.randomUUID()}")
        io.copyVerified(source, temporary)
        require(io.fingerprint(temporary) == expectedHash) { "旧世界导入暂存摘要失败: $temporary" }
        io.moveAbsent(temporary, stage)
    }
}
