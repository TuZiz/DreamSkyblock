package ym.dreamSkyblock.config

import org.bukkit.Material
import org.bukkit.World
import org.bukkit.configuration.file.YamlConfiguration
import ym.dreamSkyblock.DreamSkyblock
import ym.dreamSkyblock.model.HomeFlag
import ym.dreamSkyblock.model.HomeTemplate
import ym.dreamSkyblock.gui.HomeMenu
import java.io.File

class ConfigService(private val plugin: DreamSkyblock) {
    private data class Snapshot(
        val templates: Map<String, HomeTemplate>,
        val unloadDelaySeconds: Long,
        val inviteExpirySeconds: Long,
        val maxMembers: Int,
        val dataFolderName: String,
        val messages: YamlConfiguration,
        val menus: Map<HomeMenu, YamlConfiguration>,
        val levels: YamlConfiguration
    )

    lateinit var templates: Map<String, HomeTemplate>
        private set
    var unloadDelaySeconds: Long = 60
        private set
    var inviteExpirySeconds: Long = 300
        private set
    var maxMembers: Int = 4
        private set
    var dataFolderName: String = "playerdata"
        private set

    lateinit var messages: YamlConfiguration
        private set
    lateinit var menus: Map<HomeMenu, YamlConfiguration>
        private set
    val creationTemplates: Map<String, HomeTemplate> get() = templates.filterValues { it.generator == HomeTemplate.GeneratorType.SKYBLOCK }
    lateinit var levels: YamlConfiguration
        private set

    fun load() {
        plugin.saveDefaultConfig()
        listOf("templates.yml", "retired_templates.yml", "lang/zh_cn.yml", "levels.yml").forEach {
            if (!File(plugin.dataFolder, it).exists()) plugin.saveResource(it, false)
        }
        apply(readSnapshot())
    }

    /** 在异步线程读取文件，读取完成后回到 Paper 主线程应用配置。 */
    fun reloadAsync(onSuccess: () -> Unit, onFailure: (Throwable) -> Unit) {
        plugin.server.asyncScheduler.runNow(plugin) {
            runCatching { readSnapshot() }
                .onSuccess { snapshot ->
                    if (!plugin.isEnabled) return@onSuccess
                    plugin.server.globalRegionScheduler.execute(plugin) {
                        if (!plugin.isEnabled) return@execute
                        apply(snapshot)
                        onSuccess()
                    }
                }
                .onFailure { error ->
                    if (!plugin.isEnabled) return@onFailure
                    plugin.server.globalRegionScheduler.execute(plugin) { onFailure(error) }
                }
        }
    }

    private fun readSnapshot(): Snapshot {
        val config = VersionedYaml.load(File(plugin.dataFolder, "config.yml").toPath())
        val unloadDelaySeconds = config.getLong("world.unload-delay-seconds", 60).coerceAtLeast(5)
        val inviteExpirySeconds = config.getLong("invite-expiry-seconds", 300).coerceAtLeast(10)
        val maxMembers = config.getInt("home.max-members", 4).coerceIn(2, 32)
        val dataFolderName = config.getString("storage.data-folder", "playerdata")!!
        require(dataFolderName.matches(Regex("[A-Za-z0-9_-]+"))) { "config.yml: storage.data-folder 无效: $dataFolderName" }
        val root = VersionedYaml.load(File(plugin.dataFolder, "templates.yml").toPath())
        val configured = root.getConfigurationSection("templates")?.getKeys(false)?.associateWith { id -> parseTemplate(id, root) }
            ?.filterValues { it != null }?.mapValues { it.value!! } ?: emptyMap()
        require(configured.values.any { it.generator == HomeTemplate.GeneratorType.SKYBLOCK }) { "templates.yml: templates 必须包含空岛模板" }
        val retired = VersionedYaml.load(File(plugin.dataFolder, "retired_templates.yml").toPath())
        val legacy = retired.getConfigurationSection("templates")?.getKeys(false)?.associateWith { id -> parseTemplate(id, retired)!! } ?: emptyMap()
        val templates = legacy + configured
        return Snapshot(
            templates,
            unloadDelaySeconds,
            inviteExpirySeconds,
            maxMembers,
            dataFolderName,
            loadLanguage(),
            HomeGuiConfig(plugin).load(),
            VersionedYaml.load(File(plugin.dataFolder, "levels.yml").toPath())
        )
    }

    private fun loadLanguage(): YamlConfiguration {
        val path = File(plugin.dataFolder, "lang/zh_cn.yml").toPath()
        val yaml = VersionedYaml.load(path)
        val defaults = plugin.getResource("lang/zh_cn.yml")!!.bufferedReader(Charsets.UTF_8).use { it.readLines() }
        val required = defaults.filter { it.isNotBlank() && !it.startsWith("#") && !it.startsWith(" ") }.map { it.substringBefore(':') }.toSet()
        val missing = required.filterNot(yaml::contains).toSet()
        if (missing.isNotEmpty()) {
            val extra = defaults.filter { it.substringBefore(':') in missing }.joinToString("\n")
            ym.dreamSkyblock.world.WorldArchiveIO().writeDurable(path, java.nio.file.Files.readString(path).trimEnd() + "\n\n# 空岛数据维护提示\n$extra\n")
            return VersionedYaml.load(path)
        }
        return yaml
    }

    private fun apply(snapshot: Snapshot) {
        templates = snapshot.templates
        unloadDelaySeconds = snapshot.unloadDelaySeconds
        inviteExpirySeconds = snapshot.inviteExpirySeconds
        maxMembers = snapshot.maxMembers
        dataFolderName = snapshot.dataFolderName
        messages = snapshot.messages
        menus = snapshot.menus
        levels = snapshot.levels
    }

    private fun parseTemplate(id: String, root: YamlConfiguration): HomeTemplate? {
        val path = "templates.$id"
        val generator = runCatching { HomeTemplate.GeneratorType.valueOf(root.getString("$path.generator", "VANILLA")!!.uppercase()) }
            .getOrElse { throw IllegalArgumentException("templates.yml: $path.generator 无效: ${root.get("$path.generator")}", it) }
        val env = runCatching { World.Environment.valueOf(root.getString("$path.environment", "NORMAL")!!.uppercase()) }
            .getOrElse { throw IllegalArgumentException("templates.yml: $path.environment 无效: ${root.get("$path.environment")}", it) }
        val icon = Material.matchMaterial(root.getString("$path.icon", "GRASS_BLOCK")!!) ?: error("templates.yml: $path.icon 无效: ${root.get("$path.icon")}")
        val center = point(root, "$path.center", 0.0, 100.0, 0.0)
        val spawn = point(root, "$path.spawn", center.x + .5, center.y + 1.0, center.z + .5)
        val values = HomeFlag.entries.associateWith { root.getBoolean("$path.flags.${it.name.lowercase()}", it.defaultValue) }
        return HomeTemplate(
            id, root.getString("$path.display-name", id)!!, root.getStringList("$path.description"), icon, env, generator,
            center, spawn, root.getDouble("$path.world-border-size", 100.0).coerceIn(16.0, 384.0),
            runCatching { org.bukkit.Difficulty.valueOf(root.getString("$path.difficulty", "NORMAL")!!.uppercase()) }.getOrDefault(org.bukkit.Difficulty.NORMAL),
            root.getBoolean("$path.animals", true), root.getBoolean("$path.monsters", true), root.getBoolean("$path.pvp", false),
            root.getBoolean("$path.keep-inventory", true), root.getBoolean("$path.flight", false), root.getBoolean("$path.weather", false),
            root.getBoolean("$path.natural-regeneration", true), root.getBoolean("$path.allow-nether-portal", false), root.getBoolean("$path.allow-end-portal", false),
            values.filterKeys { it.category == HomeFlag.Category.WORLD }, values.filterKeys { it.category == HomeFlag.Category.STRANGER },
            root.getInt("$path.platform-size", 7).coerceIn(3, 31), root.getInt("$path.platform-y", 100)
        )
    }

    private fun point(root: YamlConfiguration, path: String, x: Double, y: Double, z: Double): HomeTemplate.Point =
        HomeTemplate.Point(root.getDouble("$path.x", x), root.getDouble("$path.y", y), root.getDouble("$path.z", z), root.getDouble("$path.yaw", 0.0).toFloat(), root.getDouble("$path.pitch", 0.0).toFloat())
}
