package ym.dreamSkyblock.world

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.time.Instant
import java.util.UUID
import java.util.logging.Logger

/** Offline disk operations only. The world manager owns unloading and serializes all world/DB changes. */
class NativeWorldFiles(
    pluginRoot: Path,
    baseWorld: Path,
    legacyRoot: Path = pluginRoot.resolve("playerdata"),
    private val diagnostic: (String) -> Unit = { Logger.getLogger("DreamSkyblock").warning(it) }
) {
    private val io = WorldArchiveIO()
    private val pluginRoot = io.checked(pluginRoot)
    private val baseWorld = io.checked(baseWorld)
    private val legacyRoot = io.contained(legacyRoot, this.pluginRoot)
    private val namespace = this.baseWorld.resolve("dimensions/dreamskyblock")
    private val backups = this.pluginRoot.resolve("backups")
    private val trash = this.pluginRoot.resolve("trash")
    private val creation = HomeCreationReceipt(this.pluginRoot.resolve("data/created-worlds"))
    internal var transactionCheckpoint: (String) -> Unit = {}
    private val transactions = WorldFileTransactions(
        this.pluginRoot.resolve("data/world-transactions"), namespace, io, ::worldName, ::validateWorld,
        diagnostic, { transactionCheckpoint(it) }
    )
    private val migration = LegacyWorldMigration(
        this.pluginRoot, this.baseWorld, this.legacyRoot, io, ::worldName, ::validateWorld, ::isNativeWorld,
        ::backupSource, diagnostic
    )
    private val legacyImport = LegacyWorldImport(
        this.pluginRoot, this.baseWorld, io, ::worldName, ::isNativeWorld, ::readBackup,
        migration::sources, migration::metadata, ::backupSource, diagnostic
    )

    fun worldName(id: UUID): String = "dreamskyblock_${id.toString().replace("-", "")}"

    fun worldDir(id: UUID): Path = io.contained(namespace.resolve(worldName(id)), baseWorld)

    fun recordCreation(id: UUID, worldUuid: UUID) = creation.record(id, "dreamskyblock:${worldName(id)}", worldUuid)
    fun creationCompleted(id: UUID): Boolean = creation.completed(id, "dreamskyblock:${worldName(id)}") && hasWorldData(id)

    @Synchronized
    fun hasWorldData(id: UUID): Boolean {
        transactions.assertReady(id)
        val path = worldDir(id)
        if (!Files.exists(path, NOFOLLOW_LINKS)) return false
        require(isNativeWorld(path)) { "空岛 $id 原生世界目录不完整，拒绝重新生成或覆盖: $path" }
        return true
    }

    /** 26.2 splits level data; metadata alone is not sufficient to identify a saved world. */
    fun validateWorld(path: Path): Boolean {
        io.checked(path)
        if (!Files.exists(path, NOFOLLOW_LINKS)) return false
        require(Files.isDirectory(path, NOFOLLOW_LINKS)) { "世界路径不是目录: $path" }
        io.validateTree(path) // Reject links without hashing every region file during normal world loading.
        return nonEmptyFile(path.resolve("level.dat")) || nonEmptyFile(path.resolve("level.dat_old")) || (
            nonEmptyFile(path.resolve("data/minecraft/world_gen_settings.dat")) &&
                nonEmptyFile(path.resolve("data/paper/metadata.dat"))
            )
    }

    @Synchronized
    fun migrate(id: UUID, legacyHomeDir: Path? = null, metadata: String? = null): Boolean {
        transactions.assertReady(id)
        return migration.migrate(id, legacyHomeDir, metadata)
    }

    /** Prepares an owned throwaway legacy root for Paper's official 26.2 conversion, preserving every original. */
    @Synchronized
    fun prepareLegacyImport(id: UUID, legacyHomeDir: Path? = null, metadata: String? = null): Boolean {
        transactions.assertReady(id)
        return legacyImport.prepare(id, legacyHomeDir, metadata)
    }

    /** Must be called only after Paper successfully saved and unloaded this world. */
    @Synchronized
    fun backup(id: UUID, metadata: String): String {
        transactions.assertReady(id)
        val source = worldDir(id)
        require(validateWorld(source)) { "空岛 $id 没有可备份的有效原生世界: $source" }
        return backupSource(id, source, metadata)
    }

    @Synchronized
    fun readBackup(id: UUID, backupId: String): String {
        val directory = backupDirectory(id, backupId)
        io.verifyManifest(directory)
        require(validateWorld(directory.resolve("world"))) { "空岛备份不包含有效世界: $directory" }
        return Files.readString(directory.resolve("home.yml"), StandardCharsets.UTF_8)
    }

    /** Leaves a durable COMMITTED journal until the caller commits matching SQLite metadata. */
    @Synchronized
    fun restore(id: UUID, backupId: String): String {
        val metadata = readBackup(id, backupId)
        val source = backupDirectory(id, backupId).resolve("world")
        require(isNativeWorld(source)) { "备份 $backupId 为旧版 level.dat 格式，必须通过 Paper 官方导入，禁止直接替换原生目录" }
        transactions.replace(id, source, backupId, metadata)
        return metadata
    }

    @Synchronized
    fun recover() = transactions.recover()

    @Synchronized
    fun pendingRestores(): Map<UUID, String> = transactions.pendingRestores()

    @Synchronized
    fun hasPendingTransaction(id: UUID): Boolean = transactions.hasPending(id)

    @Synchronized
    fun acknowledgeRestore(id: UUID) = transactions.acknowledgeRestore(id)

    /** Returns the backup ID; the caller's durable delete intent owns the corresponding SQLite deletion. */
    @Synchronized
    fun archiveDeleted(id: UUID, metadata: String): String {
        transactions.assertReady(id)
        val source = worldDir(id)
        if (!Files.exists(source, NOFOLLOW_LINKS)) {
            val existing = archivedBackup(id, metadata)
            if (existing != null) return existing
        }
        // 损坏或中断创建的世界也完整归档；缺失世界保留元数据凭据，归属墓碑永不丢弃。
        val backupId = backupSource(id, source, metadata, allowInvalid = true)
        val archive = io.contained(trash.resolve(id.toString()).resolve(backupId), trash)
        Files.createDirectories(archive)
        if (Files.exists(source, NOFOLLOW_LINKS)) io.moveAbsent(source, archive.resolve("world"))
        io.writeProperties(archive.resolve("deleted.properties"), mapOf(
            "schema-version" to "1", "home-id" to id.toString(), "backup-id" to backupId,
            "deleted-at" to Instant.now().toString()
        ))
        diagnostic("空岛 $id 世界已移入安全删除归档 $archive，完整备份 $backupId 保留")
        return backupId
    }

    /** Reports unmanaged world directories; it never removes or imports them without ownership evidence. */
    @Synchronized
    fun audit(knownIds: Set<UUID>): List<Path> {
        io.checked(namespace)
        if (!Files.exists(namespace, NOFOLLOW_LINKS)) return emptyList()
        val knownNames = knownIds.map(::worldName).toSet()
        return Files.list(namespace).use { stream ->
            stream.filter { it.fileName.toString().startsWith("dreamskyblock_") && it.fileName.toString() !in knownNames }
                .sorted().toList().onEach { io.contained(it, namespace) }
        }
    }

    private fun backupSource(id: UUID, source: Path, metadata: String): String = backupSource(id, source, metadata, false)

    private fun backupSource(id: UUID, source: Path, metadata: String, allowInvalid: Boolean): String {
        require(allowInvalid || validateWorld(source)) { "迁移/备份源没有有效世界数据: $source" }
        val token = UUID.randomUUID().toString()
        val directory = backupDirectory(id, token)
        val temporary = io.contained(directory.resolveSibling(".stage-$token"), backups)
        Files.createDirectories(temporary)
        if (Files.exists(io.checked(source), NOFOLLOW_LINKS)) io.copyVerified(source, temporary.resolve("world"))
        else io.writeDurable(temporary.resolve("world-missing.txt"), "home-id=$id\n世界缺失；保留完整归属元数据及删除记录。\n")
        io.writeDurable(temporary.resolve("home.yml"), metadata)
        io.writeDurable(temporary.resolve("manifest.sha256"), io.manifest(io.entries(temporary)))
        io.verifyManifest(temporary)
        io.moveAbsent(temporary, directory)
        return token
    }

    private fun backupDirectory(id: UUID, backupId: String): Path {
        require(UUID.fromString(backupId).toString() == backupId) { "非法备份标识: $backupId" }
        return io.contained(backups.resolve(id.toString()).resolve(backupId), backups)
    }

    private fun archivedBackup(id: UUID, metadata: String): String? {
        val directory = io.contained(trash.resolve(id.toString()), trash)
        if (!Files.exists(directory, NOFOLLOW_LINKS)) return null
        val archives = Files.list(directory).use { it.sorted().toList() }
        for (archive in archives) {
            io.contained(archive, directory)
            val token = archive.fileName.toString()
            // A crash after the rename but before the receipt is safe: the verified backup identifies this archive.
            require(UUID.fromString(token).toString() == token) { "非法删除归档目录: $archive" }
            val backup = backupDirectory(id, token)
            io.verifyManifest(backup)
            if (Files.readString(backup.resolve("home.yml")) == metadata) return token
        }
        return null
    }

    private fun nonEmptyFile(path: Path): Boolean =
        Files.isRegularFile(io.checked(path), NOFOLLOW_LINKS) && Files.size(path) > 0

    private fun isNativeWorld(path: Path): Boolean = validateWorld(path) &&
        nonEmptyFile(path.resolve("data/minecraft/world_gen_settings.dat")) &&
        nonEmptyFile(path.resolve("data/paper/metadata.dat"))
}
