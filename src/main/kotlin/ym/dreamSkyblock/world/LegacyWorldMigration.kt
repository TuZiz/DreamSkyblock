package ym.dreamSkyblock.world

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.util.UUID

/** One-time migration; preserved legacy copies stop participating once the native directory is published. */
internal class LegacyWorldMigration(
    private val pluginRoot: Path,
    private val baseWorld: Path,
    private val legacyRoot: Path,
    private val io: WorldArchiveIO,
    private val worldName: (UUID) -> String,
    private val validateWorld: (Path) -> Boolean,
    private val isNativeWorld: (Path) -> Boolean,
    private val backupSource: (UUID, Path, String) -> String,
    private val diagnostic: (String) -> Unit
) {
    fun migrate(id: UUID, legacyHomeDir: Path?, metadataOverride: String? = null): Boolean {
        val name = worldName(id)
        val target = io.contained(baseWorld.resolve("dimensions/dreamskyblock/$name"), baseWorld)
        val marker = io.contained(pluginRoot.resolve("data/world-migrations/$id.properties"), pluginRoot)
        if (Files.exists(marker, NOFOLLOW_LINKS)) {
            val data = io.readProperties(marker)
            require(data["schema-version"] == "1" && data["home-id"] == id.toString() && data["world-name"] == name) {
                "世界迁移标记无效: $marker；拒绝猜测存档归属"
            }
            require(isNativeWorld(target)) { "空岛 $id 已迁移的原生世界缺失或损坏: $target；保留旧副本并禁止自动重建" }
            return true
        }
        val importMarker = pluginRoot.resolve("data/legacy-imports/$id.properties")
        if (Files.exists(io.checked(importMarker), NOFOLLOW_LINKS)) {
            val imported = io.readProperties(importMarker)
            require(imported["schema-version"] == "1" && imported["home-id"] == id.toString()) {
                "旧世界导入标记无效: $importMarker"
            }
            require(isNativeWorld(target)) { "空岛 $id 旧世界仍等待 Paper 官方导入，禁止直接复制 level.dat 到原生目录" }
            val stage = baseWorld.parent.resolve("dreamskyblock_$name")
            require(!Files.exists(io.checked(stage), NOFOLLOW_LINKS)) {
                "空岛 $id 原生世界和旧导入暂存同时存在，无法确认官方转换完成: $stage"
            }
            io.writeProperties(marker, mapOf(
                "schema-version" to "1", "home-id" to id.toString(), "world-name" to name,
                "initial-world-sha256" to io.fingerprint(target), "backups" to imported.getValue("backups"),
                "sources" to imported.getValue("sources"), "conversion" to "paper-native-26.2"
            ))
            diagnostic("空岛 $id Paper 官方旧世界转换完成，已确认原生目录 $target；原源及备份全部保留")
            return true
        }
        val present = sources(id, legacyHomeDir)
        if (present.isEmpty()) return false
        val invalid = present.filterNot(validateWorld)
        require(invalid.isEmpty()) {
            "空岛 $id 存在不完整的旧世界目录: ${invalid.joinToString()}；禁止覆盖或重新生成，修复后可重试"
        }
        val fingerprints = present.associateWith(io::fingerprint)
        val metadata = metadataOverride ?: metadata(id, legacyHomeDir)
        // Back up each valid competing source before deciding which directory may become authoritative.
        val backups = present.associateWith { backupSource(id, it, metadata) }
        require(fingerprints.values.distinct().size == 1) {
            "空岛 $id 多个有效世界副本内容冲突，拒绝合并/覆盖。各源完整备份: " +
                backups.entries.joinToString { "${it.key} -> ${it.value}" }
        }
        require(present.all(isNativeWorld)) {
            "空岛 $id 含旧版 level.dat 世界，需要 prepareLegacyImport 后由 Paper 官方 API 转换；原源及备份保留"
        }
        if (!Files.exists(target, NOFOLLOW_LINKS)) {
            val source = present.first()
            val stage = io.contained(target.resolveSibling(".migration-$id-${UUID.randomUUID()}"), baseWorld)
            io.copyVerified(source, stage)
            require(validateWorld(stage) && io.fingerprint(stage) == fingerprints.getValue(source)) {
                "空岛 $id 世界迁移暂存校验失败: $stage；原存档和备份均保留"
            }
            io.moveAbsent(stage, target)
        }
        io.writeProperties(marker, mapOf(
            "schema-version" to "1", "home-id" to id.toString(), "world-name" to name,
            "initial-world-sha256" to fingerprints.values.first(),
            "sources" to present.joinToString("\n"), "backups" to backups.values.joinToString("\n")
        ))
        diagnostic("空岛 $id 世界迁移完成: $target；旧源未删除，迁移备份 ${backups.values.joinToString()}")
        return true
    }

    fun sources(id: UUID, legacyHomeDir: Path?): List<Path> {
        val name = worldName(id)
        val candidates = buildList {
            add(io.contained(baseWorld.resolve("dimensions/dreamskyblock/$name"), baseWorld))
            add(io.contained(baseWorld.resolve("dimensions/minecraft/$name"), baseWorld))
            baseWorld.parent?.let { add(io.contained(it.resolve(name), it)) }
            legacyDirectories(id, legacyHomeDir).forEach { add(io.contained(it.resolve("world"), pluginRoot)) }
        }.distinct()
        return candidates.filter { Files.exists(io.checked(it), NOFOLLOW_LINKS) }
    }

    fun metadata(id: UUID, legacyHomeDir: Path?): String = legacyDirectories(id, legacyHomeDir)
        .map { it.resolve("home.yml") }.firstOrNull { Files.isRegularFile(io.checked(it), NOFOLLOW_LINKS) }
        ?.let { Files.readString(it, StandardCharsets.UTF_8) }.orEmpty()

    private fun legacyDirectories(id: UUID, supplied: Path?): List<Path> {
        if (supplied != null) return listOf(io.contained(supplied, pluginRoot))
        return setOf(legacyRoot, pluginRoot.resolve("playerdata")).flatMap { root ->
            io.checked(root)
            if (!Files.exists(root, NOFOLLOW_LINKS)) return@flatMap emptyList()
            Files.list(root).use { stream ->
            stream.filter { path ->
                val name = path.fileName.toString()
                name == id.toString() || name.endsWith("_$id", ignoreCase = true)
            }.sorted().toList().onEach { io.contained(it, pluginRoot) }
        } }.distinct()
    }
}
