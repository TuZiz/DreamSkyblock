package ym.dreamSkyblock.storage

import org.bukkit.NamespacedKey
import org.bukkit.configuration.file.YamlConfiguration
import ym.dreamSkyblock.DreamSkyblock
import ym.dreamSkyblock.config.ConfigService
import ym.dreamSkyblock.model.Home
import ym.dreamSkyblock.model.HomeFlag
import ym.dreamSkyblock.model.HomeTemplate
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.AtomicMoveNotSupportedException
import java.util.Comparator
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor

class HomeStorage(private val plugin: DreamSkyblock, private val config: ConfigService) {
    private val root: Path get() = plugin.dataFolder.toPath().resolve(config.dataFolderName).normalize()

    fun loadAll(executor: Executor): CompletableFuture<List<Home>> = CompletableFuture.supplyAsync({
        Files.createDirectories(root)
        Files.list(root).use { stream ->
            stream.filter { Files.isDirectory(it) }.toList().mapNotNull { load(it) }
        }
    }, executor)

    fun save(home: Home, executor: Executor): CompletableFuture<Void> = CompletableFuture.runAsync({
        val dir = homeDir(home)
        Files.createDirectories(dir)
        val yaml = YamlConfiguration()
        yaml.set("id", home.id.toString())
        yaml.set("owner-name", home.ownerName)
        yaml.set("template", home.templateId)
        yaml.set("center.x", home.center.x)
        yaml.set("center.y", home.center.y)
        yaml.set("center.z", home.center.z)
        yaml.set("level", home.level)
        yaml.set("experience", home.experience)
        yaml.set("border-level", home.borderLevel)
        yaml.set("member-limit", home.memberLimit)
        yaml.set("border-size", home.borderSize)
        yaml.set("owners", home.owners.map { it.toString() })
        home.ownerNames.forEach { (id, name) -> yaml.set("owner-names.$id", name) }
        home.flags.forEach { (flag, value) -> yaml.set("flags.${flag.name.lowercase()}", value) }
        val target = dir.resolve("home.yml")
        val temporary = dir.resolve(".home.yml.${UUID.randomUUID()}.tmp")
        try {
            yaml.save(temporary.toFile())
            try {
                Files.move(temporary, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }, executor)

    fun homeDir(home: Home): Path = safeHomeDir(home.ownerName, home.id)
    fun worldDir(home: Home): Path = homeDir(home).resolve("world").normalize()

    fun hasWorldData(home: Home): Boolean {
        val dir = worldDir(home)
        // 26.2 的独立维度将生成设置和 Paper UUID 元数据保存在 data 下。
        return Files.isRegularFile(dir.resolve("data/minecraft/world_gen_settings.dat")) ||
            Files.isRegularFile(dir.resolve("data/paper/metadata.dat")) ||
            Files.isRegularFile(dir.resolve("level.dat")) || Files.isRegularFile(dir.resolve("level.dat_old"))
    }

    /** Paper 26.2 only creates worlds in the minecraft namespace. Real data remains under the plugin data folder. */
    fun stagingDir(home: Home, worldContainer: Path): Path =
        baseWorldDir(worldContainer).resolve("dimensions").resolve("minecraft").resolve(worldName(home)).normalize()

    /** Runtime mount point used by the first V1 implementation. */
    fun legacyStagingDir(home: Home, worldContainer: Path): Path =
        baseWorldDir(worldContainer).resolve("dimensions").resolve("dreamskyblock").resolve(worldName(home)).normalize()

    fun worldName(home: Home): String = "dreamskyblock_${home.id.toString().replace("-", "")}".lowercase()
    fun worldKey(home: Home): NamespacedKey = NamespacedKey.minecraft(worldName(home))

    fun copyDirectory(source: Path, target: Path) {
        if (!Files.exists(source)) return
        Files.createDirectories(target)
        Files.walk(source).use { paths ->
            paths.forEach { path ->
                val relative = source.relativize(path)
                val destination = target.resolve(relative).normalize()
                require(isWithin(destination, target)) { "非法世界路径" }
                if (Files.isDirectory(path)) Files.createDirectories(destination)
                else Files.copy(path, destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }

    fun deleteDirectory(path: Path) {
        if (!Files.exists(path)) return
        Files.walk(path).sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
    }

    /** Prepare a physical runtime copy. Paper 26.2 rejects symlink/junction leaf directories. */
    fun prepareWorldDirectory(home: Home, worldContainer: Path) {
        val staging = stagingDir(home, worldContainer)
        val legacy = legacyStagingDir(home, worldContainer)
        val target = worldDir(home)
        require(isWithin(staging, worldContainer) && isWithin(target, root)) { "非法世界路径" }
        Files.createDirectories(staging.parent)

        if (entryExists(staging)) {
            if (Files.exists(staging) && sameFile(staging, target)) {
                Files.deleteIfExists(staging)
            } else {
                migrateStagingEntry(staging, target, worldContainer)
            }
        }
        if (entryExists(legacy)) {
            if (Files.exists(legacy) && sameFile(legacy, target)) {
                Files.deleteIfExists(legacy)
            } else {
                migrateStagingEntry(legacy, target, worldContainer)
            }
        }
        Files.createDirectories(staging)
        if (Files.exists(target)) copyDirectory(target, staging)
    }

    /**
     * Prepare a brand-new world. Paper must see a missing leaf directory so it can
     * create its world configuration; a junction here would make createDirectories
     * fail with FileAlreadyExistsException on Windows.
     */
    fun prepareWorldForCreation(home: Home, worldContainer: Path) {
        val staging = stagingDir(home, worldContainer)
        val legacy = legacyStagingDir(home, worldContainer)
        val target = worldDir(home)
        require(isWithin(staging, worldContainer) && isWithin(target, root)) { "非法世界路径" }
        Files.createDirectories(target.parent)
        removeStagingEntry(staging, target, worldContainer)
        removeStagingEntry(legacy, target, worldContainer)
    }

    /** Copy a newly-created runtime world into plugin storage while it remains loaded. */
    fun snapshotWorldDirectory(home: Home, worldContainer: Path) {
        val staging = stagingDir(home, worldContainer)
        val target = worldDir(home)
        require(isWithin(staging, worldContainer) && isWithin(target, root)) { "非法世界路径" }
        require(Files.isDirectory(staging, LinkOption.NOFOLLOW_LINKS)) { "世界目录未创建: $staging" }
        Files.createDirectories(target.parent)
        copyDirectory(staging, target)
    }

    /** Persist and remove a physical runtime world after Paper has unloaded it. */
    fun syncWorldDirectory(home: Home, worldContainer: Path) {
        val staging = stagingDir(home, worldContainer)
        val target = worldDir(home)
        require(isWithin(staging, worldContainer) && isWithin(target, root)) { "非法世界路径" }
        if (Files.exists(staging)) {
            Files.createDirectories(target)
            copyDirectory(staging, target)
            deleteDirectory(staging)
        }
    }

    /** Remove only runtime copies/legacy directories; never delete plugin-owned world data here. */
    fun removeWorldLink(home: Home, worldContainer: Path) {
        val target = worldDir(home)
        removeStagingEntry(stagingDir(home, worldContainer), target, worldContainer)
        removeStagingEntry(legacyStagingDir(home, worldContainer), target, worldContainer)
    }

    private fun baseWorldDir(worldContainer: Path): Path =
        if (worldContainer.fileName?.toString()?.equals("world", ignoreCase = true) == true) {
            worldContainer
        } else {
            worldContainer.resolve("world")
        }

    private fun entryExists(path: Path): Boolean = Files.exists(path, LinkOption.NOFOLLOW_LINKS)

    private fun migrateStagingEntry(source: Path, target: Path, worldContainer: Path) {
        if (!Files.exists(source)) {
            Files.deleteIfExists(source)
            return
        }
        validateRealPath(source, worldContainer)
        if (Files.isSymbolicLink(source)) error("拒绝迁移非插件创建的世界符号链接: $source")
        copyDirectory(source, target)
        deleteDirectory(source)
    }

    private fun removeStagingEntry(staging: Path, target: Path, worldContainer: Path) {
        if (!entryExists(staging)) return
        if ((Files.exists(staging) && sameFile(staging, target)) || Files.isSymbolicLink(staging)) {
            Files.deleteIfExists(staging)
        } else {
            validateRealPath(staging, worldContainer)
            deleteDirectory(staging)
        }
    }

    private fun validateRealPath(path: Path, worldContainer: Path) {
        val real = runCatching { path.toRealPath() }.getOrElse { return }
        require(isWithin(real, worldContainer)) { "非法世界路径" }
    }

    private fun load(dir: Path): Home? = runCatching {
        val file = dir.resolve("home.yml").normalize()
        if (!isWithin(file, root) || !Files.exists(file)) return null
        val y = YamlConfiguration.loadConfiguration(file.toFile())
        val id = UUID.fromString(y.getString("id") ?: dir.fileName.toString().substringAfterLast('_'))
        val center = HomeTemplate.Point(y.getDouble("center.x"), y.getDouble("center.y"), y.getDouble("center.z"))
        Home(
            id,
            y.getString("owner-name", "unknown")!!,
            y.getString("template", "vanilla")!!,
            center,
            y.getDouble("border-size", 100.0),
            y.getInt("level", 1),
            y.getLong("experience", 0L).coerceAtLeast(0L),
            y.getInt("border-level", 1),
            y.getInt("member-limit", config.maxMembers)
        ).also { home ->
            y.getStringList("owners").mapNotNull { runCatching { UUID.fromString(it) }.getOrNull() }.forEach { home.owners.add(it) }
            y.getConfigurationSection("owner-names")?.getKeys(false)?.forEach { key ->
                runCatching { home.ownerNames[UUID.fromString(key)] = y.getString("owner-names.$key", "unknown")!! }
            }
            HomeFlag.entries.forEach { flag ->
                if (y.contains("flags.${flag.name.lowercase()}")) home.flags[flag] = y.getBoolean("flags.${flag.name.lowercase()}")
            }
        }
    }.getOrElse {
        plugin.logger.warning("无法读取家园目录 $dir: ${it.message}")
        null
    }

    private fun safeHomeDir(ownerName: String, id: UUID): Path {
        val safeName = ownerName.replace(Regex("[^A-Za-z0-9_-]"), "_").take(32).ifBlank { "player" }
        val dir = root.resolve("${safeName}_$id").normalize()
        require(isWithin(dir, root)) { "非法家园路径" }
        return dir
    }

    fun isWithin(child: Path, parent: Path): Boolean {
        val childText = child.toAbsolutePath().normalize().toString()
        val parentText = parent.toAbsolutePath().normalize().toString()
        val windows = System.getProperty("os.name").lowercase(Locale.ROOT).contains("win")
        return if (windows) {
            childText.equals(parentText, ignoreCase = true) || childText.startsWith("$parentText${File.separator}", ignoreCase = true)
        } else {
            childText == parentText || childText.startsWith("$parentText${File.separator}")
        }
    }

    private fun sameFile(first: Path, second: Path): Boolean = runCatching { Files.isSameFile(first, second) }.getOrDefault(false)

}
