package ym.dreamSkyblock.world

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest
import java.util.Base64
import java.util.Properties
import java.util.UUID

/** Filesystem primitives shared by offline world migration and archives. Never follows links. */
internal class WorldArchiveIO {
    fun checked(path: Path): Path {
        val absolute = path.toAbsolutePath().normalize()
        var current: Path? = absolute
        while (current != null) {
            if (Files.exists(current, NOFOLLOW_LINKS)) {
                val attributes = Files.readAttributes(current, BasicFileAttributes::class.java, NOFOLLOW_LINKS)
                require(!attributes.isSymbolicLink && !attributes.isOther) { "拒绝符号链接或重解析目录: $current" }
                // Junctions are not reported as symbolic links on all Windows/JDK combinations.
                require(current.toRealPath() == current) { "拒绝路径重定向: $current -> ${current.toRealPath()}" }
            }
            current = current.parent
        }
        return absolute
    }

    fun contained(path: Path, root: Path): Path {
        val candidate = checked(path)
        require(candidate.startsWith(checked(root))) { "路径超出所属目录: $candidate (root=$root)" }
        return candidate
    }

    fun entries(root: Path): Map<String, String> {
        checked(root)
        require(Files.isDirectory(root, NOFOLLOW_LINKS)) { "世界目录不存在: $root" }
        return Files.walk(root).use { stream ->
            stream.filter { it != root }.sorted().toList().associate { path ->
                checked(path)
                val attributes = Files.readAttributes(path, BasicFileAttributes::class.java, NOFOLLOW_LINKS)
                val relative = root.relativize(path).joinToString("/")
                val value = when {
                    attributes.isDirectory -> "d"
                    attributes.isRegularFile -> "f:${attributes.size()}:${sha256(path)}"
                    else -> throw IOException("不支持的存档条目: $path")
                }
                relative to value
            }.toSortedMap()
        }
    }

    fun validateTree(root: Path) {
        checked(root)
        Files.walk(root).use { stream ->
            stream.forEach { path ->
                checked(path)
                val attributes = Files.readAttributes(path, BasicFileAttributes::class.java, NOFOLLOW_LINKS)
                require(attributes.isDirectory || attributes.isRegularFile) { "不支持的存档条目: $path" }
            }
        }
    }

    fun copyVerified(source: Path, target: Path): Map<String, String> {
        val original = entries(source)
        checked(target)
        require(!Files.exists(target, NOFOLLOW_LINKS)) { "复制目标已经存在: $target" }
        Files.createDirectories(target)
        for ((relative, kind) in original) {
            val from = contained(source.resolve(relative), source)
            val to = contained(target.resolve(relative), target)
            if (kind == "d") Files.createDirectories(to)
            else {
                Files.createDirectories(to.parent)
                Files.copy(from, to, StandardCopyOption.COPY_ATTRIBUTES)
                FileChannel.open(to, StandardOpenOption.WRITE).use { it.force(true) }
            }
        }
        require(entries(target) == original && entries(source) == original) {
            "复制期间世界内容改变或摘要校验失败: $source -> $target；原存档已保留"
        }
        return original
    }

    fun manifest(entries: Map<String, String>): String = buildString {
        append("dreamskyblock-sha256-v1\n")
        entries.toSortedMap().forEach { (path, value) ->
            append(value).append('\t').append(Base64.getUrlEncoder().withoutPadding()
                .encodeToString(path.toByteArray(StandardCharsets.UTF_8))).append('\n')
        }
    }

    fun verifyManifest(root: Path) {
        val manifest = contained(root.resolve("manifest.sha256"), root)
        require(Files.isRegularFile(manifest, NOFOLLOW_LINKS)) { "备份缺少摘要清单: $root" }
        val expected = Files.readString(manifest, StandardCharsets.UTF_8)
        val actual = entries(root).filterKeys { it != "manifest.sha256" }
        require(expected == manifest(actual)) { "备份摘要校验失败: $root；拒绝使用损坏的备份" }
    }

    fun writeDurable(path: Path, content: String) {
        checked(path)
        Files.createDirectories(path.parent)
        val temporary = path.resolveSibling(".${path.fileName}.${UUID.randomUUID()}.tmp")
        val bytes = content.toByteArray(StandardCharsets.UTF_8)
        FileChannel.open(temporary, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE).use { channel ->
            val buffer = ByteBuffer.wrap(bytes)
            while (buffer.hasRemaining()) channel.write(buffer)
            channel.force(true)
        }
        Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }

    fun writeProperties(path: Path, values: Map<String, String>) {
        val properties = Properties().also { properties -> values.forEach(properties::setProperty) }
        val text = java.io.StringWriter().also { properties.store(it, "DreamSkyblock world transaction v1") }.toString()
        writeDurable(path, text)
    }

    fun readProperties(path: Path): Map<String, String> {
        checked(path)
        val properties = Properties()
        Files.newBufferedReader(path, StandardCharsets.UTF_8).use(properties::load)
        return properties.stringPropertyNames().associateWith(properties::getProperty)
    }

    fun moveAbsent(source: Path, target: Path) {
        checked(source)
        checked(target)
        require(Files.exists(source, NOFOLLOW_LINKS)) { "原子移动源不存在: $source" }
        require(!Files.exists(target, NOFOLLOW_LINKS)) { "拒绝覆盖现有存档: $target" }
        Files.createDirectories(target.parent)
        // No copy/delete fallback: a failed atomic move must leave the only valid source intact.
        Files.move(source, target, StandardCopyOption.ATOMIC_MOVE)
    }

    fun fingerprint(path: Path): String = sha256(manifest(entries(path)).toByteArray(StandardCharsets.UTF_8))

    private fun sha256(path: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path).use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }
}
