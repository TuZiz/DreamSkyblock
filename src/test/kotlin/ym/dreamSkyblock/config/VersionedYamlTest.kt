package ym.dreamSkyblock.config

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class VersionedYamlTest {
    @TempDir lateinit var root: Path

    @Test
    fun `version migration preserves every original comment and unknown value`() {
        val path = root.resolve("config.yml")
        val original = "# 中文注释\nunknown: 保留\nworld:\n  # 秒\n  unload-delay-seconds: 60\n"
        Files.writeString(path, original)
        assertEquals(1, VersionedYaml.load(path).getInt("schema-version"))
        assertEquals("schema-version: 1\n$original", Files.readString(path))
        VersionedYaml.load(path)
        assertEquals("schema-version: 1\n$original", Files.readString(path))
    }

    @Test
    fun `malformed or future configuration is not overwritten`() {
        val path = root.resolve("config.yml")
        listOf("broken: [", "schema-version: 2\nunknown: 保留\n").forEach { original ->
            Files.writeString(path, original)
            assertThrows(Exception::class.java) { VersionedYaml.load(path) }
            assertEquals(original, Files.readString(path))
        }
    }
}
