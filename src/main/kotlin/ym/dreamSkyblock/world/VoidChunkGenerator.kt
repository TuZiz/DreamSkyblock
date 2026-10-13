package ym.dreamSkyblock.world

import org.bukkit.World
import org.bukkit.generator.ChunkGenerator
import org.bukkit.generator.WorldInfo
import java.util.Random

class VoidChunkGenerator : ChunkGenerator() {
    override fun generateChunkData(world: World, random: Random, chunkX: Int, chunkZ: Int, biome: BiomeGrid): ChunkData = createChunkData(world)
    override fun getFixedSpawnLocation(world: World, random: Random) = org.bukkit.Location(world, 0.5, 100.0, 0.5)
    override fun shouldGenerateNoise(): Boolean = false
    override fun shouldGenerateSurface(): Boolean = false
    override fun shouldGenerateBedrock(): Boolean = false
    override fun shouldGenerateCaves(): Boolean = false
    override fun shouldGenerateDecorations(): Boolean = false
    override fun shouldGenerateMobs(): Boolean = true
    override fun shouldGenerateStructures(): Boolean = false
}
