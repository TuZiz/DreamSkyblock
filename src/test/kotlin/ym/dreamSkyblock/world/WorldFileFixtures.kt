package ym.dreamSkyblock.world

import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

internal class WorldFileFixtures(val root: Path) {
    val pluginRoot: Path = root.resolve("plugins/DreamSkyblock")
    val baseWorld: Path = root.resolve("world")
    val files = NativeWorldFiles(pluginRoot, baseWorld, diagnostic = {})

    fun native(path: Path, region: String = "original") {
        write(path.resolve("data/minecraft/world_gen_settings.dat"), "generation")
        write(path.resolve("data/paper/metadata.dat"), "metadata")
        write(path.resolve("region/r.0.0.mca"), region)
    }

    fun legacy(path: Path, region: String = "original") {
        write(path.resolve("level.dat"), "legacy-level")
        write(path.resolve("region/r.0.0.mca"), region)
    }

    fun oldHome(id: UUID): Path = pluginRoot.resolve("playerdata/Player_$id")

    fun write(path: Path, text: String) {
        Files.createDirectories(path.parent)
        Files.writeString(path, text)
    }

    fun region(id: UUID): String = Files.readString(files.worldDir(id).resolve("region/r.0.0.mca"))

    fun backupCount(id: UUID): Long = pluginRoot.resolve("backups/$id").let { path ->
        if (!Files.exists(path)) 0L else Files.list(path).use { it.filter { entry -> !entry.fileName.toString().startsWith(".") }.count() }
    }

    fun consumeLegacyStage(stage: Path) {
        require(stage.toAbsolutePath().normalize().startsWith(root.toAbsolutePath().normalize()))
        Files.walk(stage).use { stream -> stream.sorted(Comparator.reverseOrder()).forEach(Files::delete) }
    }
}
