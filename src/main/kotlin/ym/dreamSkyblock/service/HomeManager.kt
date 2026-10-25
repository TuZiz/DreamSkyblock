package ym.dreamSkyblock.service

import org.bukkit.entity.Player
import ym.dreamSkyblock.DreamSkyblock
import ym.dreamSkyblock.config.ConfigService
import ym.dreamSkyblock.model.Home
import ym.dreamSkyblock.model.HomeFlag
import ym.dreamSkyblock.storage.HomeMaintenanceService
import ym.dreamSkyblock.storage.HomeStorage
import ym.dreamSkyblock.storage.LegacyHomeMigration
import ym.dreamSkyblock.text.MessageService
import ym.dreamSkyblock.world.HomeWorldManager
import ym.dreamSkyblock.world.PaperWorldTasks
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import java.util.logging.Level
import ym.dreamSkyblock.api.HomeValueKey
import ym.dreamSkyblock.api.HomeValueResult
import ym.dreamSkyblock.api.HomeValueStatus
import ym.dreamSkyblock.api.HomeValueRecord
import ym.dreamSkyblock.value.HomeValueLogic
import ym.dreamSkyblock.value.HomeValueStartup

class HomeManager(
    private val plugin: DreamSkyblock,
    private val config: ConfigService,
    private val storage: HomeStorage,
    private val worlds: HomeWorldManager,
    private val levels: HomeLevelService,
    private val messages: MessageService,
    private val ioExecutor: Executor,
    private val migration: LegacyHomeMigration,
    private val maintenance: HomeMaintenanceService
) {
    private val homes = ConcurrentHashMap<UUID, Home>()
    private val playerHome = ConcurrentHashMap<UUID, UUID>()
    private val playerLocks = ConcurrentHashMap.newKeySet<UUID>()
    private val mutations = ConcurrentHashMap.newKeySet<UUID>()
    private val initializing = AtomicBoolean(false)
    private val restoring = AtomicBoolean(false)
    private val tasks = PaperWorldTasks(plugin)
    @Volatile var ready = false
        private set
    val invites = InviteManager(plugin, config, this, messages)

    fun load(done: () -> Unit): CompletableFuture<Void> {
        if (ready) return CompletableFuture.completedFuture(null)
        if (!initializing.compareAndSet(false, true)) return CompletableFuture.failedFuture(IllegalStateException("家园数据正在初始化"))
        return maintenance.recover().thenCompose { migration.run() }.thenCompose { storage.loadIncludingDeleted(ioExecutor) }
            .thenCompose { HomeValueStartup.reconcile(it, levels, storage, ioExecutor) }
            .thenCompose { list -> tasks.global {
                homes.clear(); playerHome.clear()
                list.forEach { home ->
                    home.borderSize = home.borderSize.coerceIn(16.0, 384.0)
                    homes[home.id] = home
                    if (home.status != "DELETED") home.owners.forEach { owner ->
                        check(playerHome.putIfAbsent(owner, home.id) == null) { "SQLite成员重复归属: $owner" }
                    }
                }
                worlds.adopt(all())
                all().forEach(worlds::applyFlags)
                ready = true
                done()
            } }.thenAccept { }.whenComplete { _, error ->
                initializing.set(false)
                if (error != null) {
                    ready = false
                    plugin.logger.log(Level.SEVERE, "家园初始化/迁移失败；保留原数据，暂停玩家操作。修复后 /home migrate 重试", error)
                }
            }
    }

    fun byId(id: UUID): Home? = homes[id]?.takeIf { it.status != "DELETED" }
    fun historical(id: UUID): Home? = homes[id]
    fun byPlayer(player: UUID): Home? = playerHome[player]?.let(::byId)
    fun byWorld(worldName: String): Home? = homes.values.firstOrNull { it.loadedWorldName == worldName && it.status != "DELETED" }
    fun all(): Collection<Home> = homes.values.filter { it.status != "DELETED" }

    private fun allow(player: Player): Boolean {
        val message = when {
            !ready -> "home.loading"
            !player.hasPermission("dreamskyblock.use") -> "no-permission"
            restoring.get() || player.uniqueId in playerLocks -> "home.busy"
            else -> return true
        }
        messages.send(player, message)
        return false
    }

    private fun available(home: Home): Boolean = home.status == "ACTIVE" && !worlds.isBusy(home) && home.id !in mutations

    /** Main-thread transaction boundary used by the serialized value API. */
    internal fun changeValue(expected: Home, key: HomeValueKey, value: Long, revision: Long): CompletableFuture<HomeValueResult> {
        val id = expected.id
        if (!ready || restoring.get()) return CompletableFuture.failedFuture(IllegalStateException("空岛服务未就绪或正在恢复，请重试"))
        val home = byId(id) ?: return CompletableFuture.failedFuture(IllegalArgumentException("空岛不存在: $id"))
        if (home !== expected) return CompletableFuture.failedFuture(IllegalStateException("空岛已被恢复，请重新读取当前设施后再提交: $id"))
        if (!available(home)) return CompletableFuture.failedFuture(IllegalStateException("空岛正在保存或维护，请重试: $id"))
        if (key.source == HomeValueKey.LEGACY.source) return CompletableFuture.failedFuture(IllegalArgumentException("旧经验记录命名空间为系统保留"))
        val old = home.valueRecords[key]
        val classification = HomeValueLogic.status(old, HomeValueRecord(value, revision))
        if (classification != HomeValueStatus.APPLIED) return CompletableFuture.completedFuture(
            HomeValueResult(id, classification, home.experience, home.level, home.borderSize, old!!))
        var status = HomeValueStatus.UNCHANGED
        return mutate(home) { status = HomeValueLogic.update(it, key, value, revision, levels) }.thenApply {
            worlds.applyFlags(home)
            HomeValueResult(id, status, home.experience, home.level, home.borderSize, home.valueRecords.getValue(key))
        }
    }

    fun create(player: Player, templateId: String) {
        if (!allow(player)) return
        if (byPlayer(player.uniqueId) != null) return messages.send(player, "home.already-member")
        val template = config.creationTemplates[templateId] ?: return messages.send(player, "template.not-found")
        if (!playerLocks.add(player.uniqueId)) return messages.send(player, "home.busy")
        val home = Home(UUID.randomUUID(), player.name, template.id, template.center, levels.borderSizeFor(levels.initialLevel),
            level = levels.initialLevel, memberLimit = config.maxMembers, spawn = template.spawn)
        home.status = "CREATING"
        home.owners.add(player.uniqueId); home.ownerNames[player.uniqueId] = player.name
        home.flags.putAll(template.flags); home.flags.putAll(template.strangerFlags)
        homes[home.id] = home
        playerHome[player.uniqueId] = home.id
        storage.save(home, ioExecutor).whenComplete { _, error ->
            if (error != null) {
                plugin.logger.log(Level.SEVERE, "创建家园元数据失败 home_id=${home.id}；禁止创建世界", error)
                tasks.global {
                    homes.remove(home.id); playerHome.remove(player.uniqueId, home.id); playerLocks.remove(player.uniqueId)
                    messages.send(player, "home.create-failed")
                }
            } else worlds.create(home, template, player) { ok ->
                storage.updateStatus(home, if (ok) "ACTIVE" else "RECOVERY", ioExecutor).whenComplete { _, statusError ->
                    tasks.global {
                        playerLocks.remove(player.uniqueId)
                        if (statusError != null) {
                            home.status = "RECOVERY"
                            plugin.logger.log(Level.SEVERE, "家园创建提交失败 home_id=${home.id}；保留存档", statusError)
                        }
                        if (ok && statusError == null) plugin.server.pluginManager.callEvent(ym.dreamSkyblock.api.HomeMembershipCommittedEvent(home.id, player.uniqueId))
                        if (player.isOnline) messages.send(player, if (ok && statusError == null) "home.created" else "home.create-failed", mapOf("template" to template.displayName))
                    }
                }
            }
        }
    }

    fun teleport(player: Player) {
        if (!allow(player)) return
        val home = byPlayer(player.uniqueId) ?: return messages.send(player, "home.none")
        if (!available(home)) return messages.send(player, if (home.status == "RECOVERY") "home.recovery-required" else "home.busy")
        val template = config.templates[home.templateId] ?: return messages.send(player, "template.not-found")
        worlds.load(home, template, player) { ok -> if (!ok && player.isOnline) messages.send(player, "home.load-failed") }
    }

    /** Paper 主线程。忙时由登录协调器稍后重试；发起后再次检查会话与归属。 */
    fun returnOnLogin(player: Player, homeId: UUID, current: () -> Boolean, done: (Boolean) -> Unit): Boolean {
        val home = byPlayer(player.uniqueId) ?: return false
        if (!ready || restoring.get() || player.uniqueId in playerLocks || home.id != homeId || !available(home)) return false
        val template = config.templates[home.templateId] ?: return false
        worlds.loadForLogin(home, template, player, {
            current() && ready && player.isOnline && player.hasPermission("dreamskyblock.use") &&
                byPlayer(player.uniqueId) === home && home.isOwner(player.uniqueId) &&
                !restoring.get() && player.uniqueId !in playerLocks && available(home)
        }) { ok ->
            if (!ok && current() && player.isOnline) messages.send(player, "home.load-failed")
            done(ok)
        }
        return true
    }

    fun invite(owner: Player, target: Player) {
        if (!allow(owner)) return
        val home = byPlayer(owner.uniqueId) ?: return messages.send(owner, "home.none")
        if (!available(home)) return messages.send(owner, "home.busy")
        invites.create(owner, target, home)
    }
    fun accept(player: Player, token: String) { if (allow(player)) invites.accept(player, token) }
    fun deny(player: Player, token: String) { if (allow(player)) invites.deny(player, token) }
    fun inviteTokens(player: UUID): List<String> = invites.tokensFor(player)

    fun addOwner(home: Home, player: Player, done: (Boolean) -> Unit) {
        if (!available(home) || restoring.get() || byPlayer(player.uniqueId) != null || !home.hasRoom() || !playerLocks.add(player.uniqueId)) { done(false); return }
        mutate(home, {
            it.owners.add(player.uniqueId); it.ownerNames[player.uniqueId] = player.name
        }).whenComplete { _, error -> tasks.global {
            playerLocks.remove(player.uniqueId)
            if (error == null) plugin.server.pluginManager.callEvent(ym.dreamSkyblock.api.HomeMembershipCommittedEvent(home.id, player.uniqueId))
            done(error == null)
        } }
    }

    fun toggleFlag(player: Player, flag: HomeFlag, done: () -> Unit = {}): Boolean {
        if (!allow(player)) return false
        val home = byPlayer(player.uniqueId) ?: return false
        if (!available(home) || !home.isOwner(player.uniqueId)) return false
        mutate(home, { it.flags[flag] = !(it.flags[flag] ?: flag.defaultValue) }).whenComplete { _, error -> tasks.global {
            if (error == null) { worlds.applyFlags(home); if (player.isOnline) done() }
            else if (player.isOnline) messages.send(player, "home.save-failed")
        } }
        return true
    }

    fun leave(player: Player) {
        if (!allow(player)) return
        val home = byPlayer(player.uniqueId) ?: return messages.send(player, "home.none")
        if (!available(home)) return messages.send(player, "home.busy")
        if (home.owners.size <= 1) return messages.send(player, "home.last-owner")
        mutate(home, { it.owners.remove(player.uniqueId); it.ownerNames.remove(player.uniqueId) }).whenComplete { _, error -> tasks.global {
            if (player.isOnline) messages.send(player, if (error == null) "home.left" else "home.save-failed")
        } }
    }

    fun removeMember(player: Player, member: UUID, done: () -> Unit) {
        if (!allow(player)) return
        val home = byPlayer(player.uniqueId) ?: return messages.send(player, "home.none")
        if (!available(home)) return messages.send(player, "home.busy")
        if (!home.isOwner(player.uniqueId) || !home.isOwner(member) || member == player.uniqueId) return
        val name = home.ownerNames[member] ?: member.toString()
        mutate(home, { it.owners.remove(member); it.ownerNames.remove(member) }).whenComplete { _, error -> tasks.global {
            if (error == null) invites.invalidate(home.id)
            if (player.isOnline) {
                messages.send(player, if (error == null) "member.removed" else "home.save-failed", mapOf("member" to name))
                if (error == null) done()
            }
        } }
    }

    fun delete(player: Player) {
        if (!allow(player)) return
        val home = byPlayer(player.uniqueId) ?: return messages.send(player, "home.none")
        if (home.owners.size > 1 || !home.isOwner(player.uniqueId)) return messages.send(player, "home.delete-not-allowed")
        if (worlds.isBusy(home) || home.id in mutations) return messages.send(player, "home.busy")
        maintenance.delete(home).whenComplete { _, error -> tasks.global {
            if (error == null) { home.owners.forEach { playerHome.remove(it, home.id) }; invites.invalidate(home.id) }
            else maintenanceFailed(home)
            if (player.isOnline) messages.send(player, if (error == null) "home.deleted" else "home.delete-failed")
        } }
    }

    fun backup(id: UUID): CompletableFuture<String> {
        val home = byId(id) ?: return CompletableFuture.failedFuture(IllegalArgumentException("家园不存在: $id"))
        if (!ready || home.id in mutations) return CompletableFuture.failedFuture(IllegalStateException("家园数据忙碌"))
        return maintenance.backup(home)
    }

    fun restore(id: UUID, backupId: String): CompletableFuture<Home> {
        val home = historical(id) ?: return CompletableFuture.failedFuture(IllegalArgumentException("家园不存在: $id"))
        if (!ready || mutations.isNotEmpty() || playerLocks.isNotEmpty() || !restoring.compareAndSet(false, true)) return CompletableFuture.failedFuture(IllegalStateException("家园数据忙碌"))
        return maintenance.restore(home, backupId).thenCompose { restored -> tasks.global {
            homes[id] = restored
            home.owners.forEach { playerHome.remove(it, id) }
            restored.owners.forEach { playerHome[it] = id }
            invites.invalidate(id)
            restored
        } }.whenComplete { _, error ->
            if (error != null) maintenanceFailed(home)
            restoring.set(false)
        }
    }

    private fun maintenanceFailed(home: Home) {
        maintenance.requiresRecovery(home).whenComplete { pending, error ->
            if (pending == true || error != null) {
                ready = false
                plugin.logger.severe("家园维护存在待恢复事务 home_id=${home.id}；暂停玩家操作，请 /home migrate 重试")
            }
        }
    }

    private fun mutate(home: Home, transform: (Home) -> Unit): CompletableFuture<Void> {
        if (!mutations.add(home.id)) return CompletableFuture.failedFuture(IllegalStateException("家园正在保存"))
        val candidate = storage.snapshot(home).toHome()
        try { transform(candidate) } catch (error: Throwable) { mutations.remove(home.id); return CompletableFuture.failedFuture(error) }
        return storage.save(candidate, ioExecutor).thenCompose { tasks.global {
            synchronized(home) {
                home.owners.forEach { playerHome.remove(it, home.id) }
                home.owners.clear(); home.owners.addAll(candidate.owners)
                home.ownerNames.clear(); home.ownerNames.putAll(candidate.ownerNames)
                home.flags.clear(); home.flags.putAll(candidate.flags)
                home.valueRecords.clear(); home.valueRecords.putAll(candidate.valueRecords)
                home.borderSize = candidate.borderSize; home.level = candidate.level
                home.experience = candidate.experience; home.borderLevel = candidate.borderLevel
                home.memberLimit = candidate.memberLimit; home.spawn = candidate.spawn
                home.owners.forEach { playerHome[it] = home.id }
            }
        } }.thenAccept { }.whenComplete { _, error ->
            mutations.remove(home.id)
            if (error != null) plugin.logger.log(Level.SEVERE, "家园事务失败 home_id=${home.id}, database=${storage.databasePath}", error)
        }
    }

    fun refreshRules() {
        invites.clear()
        all().filter(::available).forEach { home ->
            worlds.cancelUnload(home)
            mutate(home, { levels.sanitize(it) }).whenComplete { _, error ->
                if (error == null) {
                    worlds.applyFlags(home)
                    worlds.scheduleUnload(home, config.unloadDelaySeconds)
                }
            }
        }
    }

    fun shutdown() {
        ready = false
        invites.clear()
        migration.close()
        tasks.close()
        playerLocks.clear(); mutations.clear(); homes.clear(); playerHome.clear()
    }
}
