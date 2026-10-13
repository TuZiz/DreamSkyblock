package ym.dreamSkyblock.service

import org.bukkit.entity.Player
import ym.dreamSkyblock.DreamSkyblock
import ym.dreamSkyblock.config.ConfigService
import ym.dreamSkyblock.model.Home
import ym.dreamSkyblock.model.HomeTemplate
import ym.dreamSkyblock.storage.HomeStorage
import ym.dreamSkyblock.text.MessageService
import ym.dreamSkyblock.world.HomeWorldManager
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor

class HomeManager(
    private val plugin: DreamSkyblock,
    private val config: ConfigService,
    private val storage: HomeStorage,
    private val worlds: HomeWorldManager,
    private val levels: HomeLevelService,
    private val messages: MessageService,
    private val ioExecutor: Executor
) {
    private val homes = ConcurrentHashMap<UUID, Home>()
    private val playerHome = ConcurrentHashMap<UUID, UUID>()
    private val creationLocks = ConcurrentHashMap.newKeySet<UUID>()
    val invites = InviteManager(plugin, config, this, messages)

    fun load(done: () -> Unit) {
        storage.loadAll(ioExecutor).thenAccept { list ->
            list.forEach { home ->
                levels.sanitize(home)
                if (home.owners.isEmpty()) return@forEach
                if (home.owners.any { playerHome.putIfAbsent(it, home.id) != null }) {
                    plugin.logger.warning("检测到重复家园归属，已跳过冲突成员: ${home.id}")
                    home.owners.removeIf { playerHome[it] != home.id }
                }
                home.owners.forEach { playerHome[it] = home.id }
                homes[home.id] = home
            }
            plugin.server.globalRegionScheduler.execute(plugin) { done() }
        }.exceptionally { plugin.logger.severe("家园数据加载失败: ${it.message}"); plugin.server.globalRegionScheduler.execute(plugin) { done() }; null }
    }

    fun byId(id: UUID): Home? = homes[id]
    fun byPlayer(player: UUID): Home? = playerHome[player]?.let(homes::get)
    fun byWorld(worldName: String): Home? = homes.values.firstOrNull { it.loadedWorldName == worldName }
    fun all(): Collection<Home> = homes.values

    fun create(player: Player, templateId: String) {
        if (!creationLocks.add(player.uniqueId)) return messages.send(player, "home.busy")
        val template = config.templates[templateId] ?: run {
            creationLocks.remove(player.uniqueId)
            messages.send(player, "template.not-found")
            return
        }
        val existing = byPlayer(player.uniqueId)
        if (existing != null) {
            ioExecutor.execute {
                val stale = !worlds.isManaged(existing) && worlds.getLoaded(existing) == null && !storage.hasWorldData(existing)
                plugin.server.globalRegionScheduler.execute(plugin) {
                    if (!stale) {
                        creationLocks.remove(player.uniqueId)
                        messages.send(player, "home.already-member")
                    } else {
                        removeHomeRecord(existing)
                        worlds.discardFailedCreation(existing)
                        beginCreate(player, template)
                    }
                }
            }
            return
        }
        beginCreate(player, template)
    }

    private fun beginCreate(player: Player, template: HomeTemplate) {
        val home = Home(UUID.randomUUID(), player.name, template.id, template.center, levels.borderSizeFor(levels.initialLevel), level = levels.initialLevel, memberLimit = config.maxMembers)
        home.owners.add(player.uniqueId); home.ownerNames[player.uniqueId] = player.name
        home.flags.putAll(template.flags); home.flags.putAll(template.strangerFlags)
        if (playerHome.putIfAbsent(player.uniqueId, home.id) != null) {
            creationLocks.remove(player.uniqueId)
            messages.send(player, "home.already-member")
            return
        }
        homes[home.id] = home
        storage.save(home, ioExecutor).whenComplete { _, error ->
            if (error != null) {
                removeHomeRecord(home); worlds.discardFailedCreation(home); creationLocks.remove(player.uniqueId); send(player, "home.create-failed")
            } else worlds.create(home, template, player) { ok ->
                if (ok) send(player, "home.created", mapOf("template" to template.displayName))
                else { removeHomeRecord(home); worlds.discardFailedCreation(home); send(player, "home.create-failed") }
                creationLocks.remove(player.uniqueId)
            }
        }
    }

    private fun removeHomeRecord(home: Home) {
        homes.remove(home.id, home)
        home.owners.forEach { playerHome.remove(it, home.id) }
    }

    fun teleport(player: Player) {
        val home = byPlayer(player.uniqueId) ?: run { messages.send(player, "home.none"); return }
        val template = config.templates[home.templateId] ?: run { messages.send(player, "template.not-found"); return }
        val world = worlds.getLoaded(home)
        if (world != null) { worlds.cancelUnload(home); player.getScheduler().execute(plugin, { if (player.isOnline) player.teleportAsync(world.spawnLocation) }, null, 0L) }
        else worlds.load(home, template, player) { ok -> if (!ok) send(player, "home.load-failed") }
    }

    fun invite(owner: Player, target: Player) {
        val home = byPlayer(owner.uniqueId) ?: run { messages.send(owner, "home.none"); return }
        if (!home.isOwner(owner.uniqueId)) { messages.send(owner, "home.not-owner"); return }
        invites.create(owner, target, home)
    }

    fun accept(player: Player, token: String) = invites.accept(player, token)
    fun deny(player: Player, token: String) = invites.deny(player, token)
    fun inviteTokens(player: UUID): List<String> = invites.tokensFor(player)

    fun addOwner(home: Home, player: Player): Boolean {
        synchronized(home) {
            if (byPlayer(player.uniqueId) != null || !home.hasRoom()) return false
            if (!home.owners.add(player.uniqueId)) return false
            home.ownerNames[player.uniqueId] = player.name
            playerHome[player.uniqueId] = home.id
        }
        persist(home)
        return true
    }

    fun toggleFlag(player: Player, flag: ym.dreamSkyblock.model.HomeFlag): Boolean {
        val home = byPlayer(player.uniqueId) ?: return false
        synchronized(home) {
            if (!home.isOwner(player.uniqueId)) return false
            home.flags[flag] = !(home.flags[flag] ?: flag.defaultValue)
        }
        persist(home)
        worlds.applyFlags(home)
        return true
    }

    fun leave(player: Player) {
        val home = byPlayer(player.uniqueId) ?: run { messages.send(player, "home.none"); return }
        synchronized(home) {
            if (!home.isOwner(player.uniqueId)) return
            if (home.owners.size <= 1) { messages.send(player, "home.last-owner"); return }
            home.owners.remove(player.uniqueId); home.ownerNames.remove(player.uniqueId); playerHome.remove(player.uniqueId, home.id)
        }
        persist(home)
        messages.send(player, "home.left")
        if (worlds.getLoaded(home)?.players?.isEmpty() == true) worlds.scheduleUnload(home, config.unloadDelaySeconds)
    }

    fun delete(player: Player) {
        val home = byPlayer(player.uniqueId) ?: run { messages.send(player, "home.none"); return }
        synchronized(home) { if (home.owners.size > 1 || !home.isOwner(player.uniqueId)) { messages.send(player, "home.delete-not-allowed"); return } }
        worlds.delete(home) { ok ->
            if (ok) {
                homes.remove(home.id); playerHome.remove(player.uniqueId, home.id); send(player, "home.deleted")
            } else send(player, "home.delete-failed")
        }
    }

    private fun send(player: Player, key: String, placeholders: Map<String, String> = emptyMap()) = player.getScheduler().execute(plugin, { if (player.isOnline) messages.send(player, key, placeholders) }, null, 0L)

    private fun persist(home: Home) {
        storage.save(home, ioExecutor).exceptionally {
            plugin.logger.severe("保存家园数据失败 ${home.id}: ${it.message}")
            null
        }
    }
}
