import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import ym.dreamSkyblock.DreamSkyblock;
import ym.dreamSkyblock.api.DreamSkyblockApi;
import ym.dreamSkyblock.model.Home;
import ym.dreamSkyblock.model.HomeFlag;
import kotlin.Unit;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/** Runs only in an isolated test server; no real player/network or production data is used. */
public final class DreamStorageProbe extends JavaPlugin {
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000099");
    private static final UUID MIGRATED = UUID.fromString("2f489701-7012-4870-924e-292b87c636b4");
    private static final UUID MEMBER = UUID.fromString("00000000-0000-0000-0000-000000000066");
    private DreamSkyblock dream;
    private Player player, member;
    private Home home;
    private World world;
    private String backup;
    private UUID worldUid;
    private int phase, ticks;
    private final Path state = Path.of("probe-state.txt");

    @Override public void onEnable() {
        dream = (DreamSkyblock) getServer().getPluginManager().getPlugin("DreamSkyblock");
        var scheduler = (EntityScheduler) Proxy.newProxyInstance(EntityScheduler.class.getClassLoader(), new Class[]{EntityScheduler.class}, (p,m,a) -> defaultValue(m.getReturnType()));
        player = player(PLAYER, "NativeProbe", scheduler);
        member = player(MEMBER, "MemberProbe", scheduler);
        getServer().getGlobalRegionScheduler().runAtFixedRate(this, task -> {
            try { step(); } catch (Throwable error) { fail(error); task.cancel(); }
        }, 20L, 10L);
    }

    private void step() throws Exception {
        check(++ticks < 360, "probe timeout phase=" + phase);
        if (phase == 0) {
            if (!dream.getHomeManager().getReady()) return;
            DreamSkyblockApi api = getServer().getServicesManager().load(DreamSkyblockApi.class);
            check(api != null && api.isReady(), "stable API registered");
            Home migrated = dream.getHomeManager().byId(MIGRATED);
            if (Files.exists(state)) {
                check(migrated == null, "retained legacy YAML must not resurrect deleted home");
                String[] saved = Files.readString(state).split("\n");
                home = dream.getHomeManager().byId(UUID.fromString(saved[0]));
                check(home != null && home.isOwner(PLAYER), "SQLite ownership survives restart");
                worldUid = UUID.fromString(saved[1]);
                if (Boolean.getBoolean("dream.probe.crash") && !Files.exists(Path.of("crash-done.flag"))) {
                    complete(dream.getHomeManager().backup(home.getId()), token -> {
                        backup = token;
                        visit(home, () -> {
                            dream.getWorldManager().getLoaded(home).getBlockAt(4,105,4).setType(Material.EMERALD_BLOCK);
                            home.setLevel(101);
                            dream.getHomeManager().toggleFlag(player, HomeFlag.PVP, () -> Unit.INSTANCE);
                            phase = 30;
                        });
                    });
                    phase = 99;
                    return;
                }
                visit(home, () -> {
                    World reopened = dream.getWorldManager().getLoaded(home);
                    check(reopened.getUID().equals(worldUid), "world UUID survives restart");
                    check(reopened.getBlockAt(4,105,4).getType() == Material.DIAMOND_BLOCK, "restored blocks survive restart");
                    check(reopened.getWorldBorder().getSize() == 384.0, "384 border survives restart");
                    check(home.getLevel() == 100 && home.getFlags().get(HomeFlag.PVP), "SQLite metadata agrees with restored world after restart");
                    getLogger().info("DREAM PROBE RESTART ALL PASS SQLite + native world + restored metadata + no YAML resurrection");
                    getServer().shutdown();
                });
                phase = 99;
                return;
            }
            check(migrated != null, "legacy YAML imported");
            visit(migrated, () -> {
                World migratedWorld = dream.getWorldManager().getLoaded(migrated);
                check(migratedWorld.getBlockAt(0,100,0).getType() == Material.GRASS_BLOCK, "legacy terrain retained");
                getLogger().info("DREAM PROBE PASS legacy YAML + world migration");
                Home legacyLevel = dream.getHomeManager().byId(UUID.fromString("00000000-0000-0000-0000-000000000088"));
                if (legacyLevel == null) phase = 1;
                else visit(legacyLevel, () -> {
                    check(dream.getWorldManager().getLoaded(legacyLevel).getBlockAt(0,100,0).getType() == Material.DIAMOND_BLOCK, "old level.dat converted terrain");
                    getLogger().info("DREAM PROBE PASS official level.dat import + retained old world");
                    phase = 1;
                });
            });
            phase = 99;
        } else if (phase == 1) {
            dream.getHomeManager().create(player, "skyblock");
            phase = 2;
        } else if (phase == 2) {
            home = dream.getHomeManager().byPlayer(PLAYER);
            if (home == null || !home.getStatus().equals("ACTIVE")) return;
            world = dream.getWorldManager().getLoaded(home);
            check(world != null, "new world loaded");
            check(world.getBlockAt(0,100,0).getType() == Material.GRASS_BLOCK, "initial island generated");
            check(world.getKey().getNamespace().equals("dreamskyblock"), "native namespace");
            check(world.getWorldPath().equals(getServer().getLevelDirectory().resolve("dimensions/dreamskyblock/" + dream.getWorldManager().getFiles().worldName(home.getId()))), "native path");
            worldUid = world.getUID();
            world.getBlockAt(4,105,4).setType(Material.DIAMOND_BLOCK);
            home.setLevel(100); home.setBorderSize(dream.getLevelService().borderSizeFor(100));
            dream.getWorldManager().applyFlags(home);
            dream.getHomeManager().toggleFlag(player, HomeFlag.PVP, () -> Unit.INSTANCE);
            phase = 20;
        } else if (phase == 3) {
            check(world.getWorldBorder().getSize() == 384.0, "upgrade border maximum");
            check(world.getUID().equals(worldUid) && world.getBlockAt(4,105,4).getType() == Material.DIAMOND_BLOCK, "upgrade does not regenerate");
            AtomicInteger callbacks = new AtomicInteger();
            for (int i = 0; i < 6; i++) dream.getWorldManager().load(home, template(home), null, ok -> {
                check(ok, "concurrent duplicate load");
                check(dream.getWorldManager().getLoaded(home).getUID().equals(worldUid), "single world instance");
                if (callbacks.incrementAndGet() == 6) phase = 4;
                return Unit.INSTANCE;
            });
            phase = 99;
        } else if (phase == 4) {
            dream.getWorldManager().scheduleUnload(home, 1L);
            phase = 5;
        } else if (phase == 5) {
            if (dream.getWorldManager().getLoaded(home) != null) return;
            check(getServer().getWorld(worldUid) == null, "safe unload");
            visit(home, () -> {
                check(dream.getWorldManager().getLoaded(home).getBlockAt(4,105,4).getType() == Material.DIAMOND_BLOCK, "unload reload preserves blocks");
                getLogger().info("DREAM PROBE PASS create + upgrade384 + duplicate load + delayed unload + reload");
                phase = 6;
            });
            phase = 99;
        } else if (phase == 6) {
            complete(dream.getHomeManager().backup(home.getId()), token -> {
                backup = token;
                check(dream.getWorldManager().getLoaded(home) == null, "backup unloads first");
                visit(home, () -> {
                    dream.getWorldManager().getLoaded(home).getBlockAt(4,105,4).setType(Material.EMERALD_BLOCK);
                    phase = 7;
                });
            });
            phase = 99;
        } else if (phase == 7) {
            complete(dream.getHomeManager().restore(home.getId(), backup), restored -> {
                home = restored;
                visit(home, () -> {
                    check(dream.getWorldManager().getLoaded(home).getBlockAt(4,105,4).getType() == Material.DIAMOND_BLOCK, "backup restores terrain");
                    check(home.getLevel() == 100 && home.getBorderSize() == 384.0, "backup restores metadata");
                    getLogger().info("DREAM PROBE PASS backup + restore world/SQLite agreement");
                    phase = 8;
                });
            });
            phase = 99;
        } else if (phase == 8) {
            dream.getHomeManager().delete(player);
            phase = 9;
        } else if (phase == 9) {
            if (dream.getHomeManager().byPlayer(PLAYER) != null) return;
            check(home.getStatus().equals("DELETED"), "deletion tombstone retained");
            complete(dream.getHomeManager().restore(home.getId(), backup), restored -> {
                home = restored;
                check(home.isOwner(PLAYER), "restore deleted island ownership");
                check(dream.getHomeManager().byPlayer(PLAYER).getId().equals(home.getId()), "restored membership index");
                EntityScheduler scheduler = player.getScheduler();
                dream.getHomeManager().delete(player(UUID.fromString("f11815e2-7cb1-48c7-a1d3-5ccf84f2e924"), "PurpurProbe", scheduler));
                phase = 10;
            });
            phase = 99;
        } else if (phase == 10) {
            if (dream.getHomeManager().byId(MIGRATED) != null) return;
                getServer().getAsyncScheduler().runNow(this, ioTask -> {
                    try {
                        check(Files.exists(dream.getDataFolder().toPath().resolve("data/homes.db")), "SQLite path");
                        check(Files.exists(dream.getDataFolder().toPath().resolve("playerdata/PurpurProbe_" + MIGRATED + "/home.yml")), "legacy YAML retained");
                        check(Files.exists(dream.getDataFolder().toPath().resolve("playerdata/PurpurProbe_" + MIGRATED + "/world/data/paper/metadata.dat")), "legacy world retained");
                        check(dream.getWorldManager().getFiles().pendingRestores().isEmpty(), "restored SQLite transaction acknowledged");
                        Files.writeString(state, home.getId() + "\n" + worldUid + "\n");
                        getServer().getGlobalRegionScheduler().execute(this, () -> {
                            getLogger().info("DREAM PROBE ALL PASS migration + create + upgrade + duplicate load + unload/reload + backup/restore + delete/archive/restore + stable API");
                            getServer().shutdown();
                        });
                    } catch (Throwable error) { failAsync(error); }
                });
            phase = 99;
        } else if (phase == 20) {
            if (!Boolean.TRUE.equals(home.getFlags().get(HomeFlag.PVP)) || !world.getPVP()) return;
            dream.getHomeManager().invite(player, member);
            String token = dream.getHomeManager().inviteTokens(MEMBER).getFirst();
            dream.getHomeManager().accept(member, token);
            phase = 21;
        } else if (phase == 21) {
            Home joined = dream.getHomeManager().byPlayer(MEMBER);
            if (joined == null) return;
            check(joined.getId().equals(home.getId()) && home.getOwners().size() == 2, "invite membership transaction");
            dream.getHomeManager().leave(member);
            phase = 22;
        } else if (phase == 22) {
            if (dream.getHomeManager().byPlayer(MEMBER) != null) return;
            check(home.getOwners().size() == 1 && !player.hasPermission("dreamskyblock.admin"), "ordinary use-only permission and leave transaction");
            getLogger().info("DREAM PROBE PASS ordinary .use + invite/accept + leave + flag persistence/runtime");
            phase = 3;
        } else if (phase == 30) {
            if (!Boolean.FALSE.equals(home.getFlags().get(HomeFlag.PVP))) return;
            dream.getWorldManager().getLoaded(home).save(true);
            java.lang.reflect.Method setter = java.util.Arrays.stream(dream.getWorldManager().getFiles().getClass().getMethods())
                .filter(method -> method.getName().startsWith("setTransactionCheckpoint")).findFirst().orElseThrow();
            setter.invoke(dream.getWorldManager().getFiles(), (kotlin.jvm.functions.Function1<String,Unit>) stage -> {
                if (stage.equals("committed")) throw new IllegalStateException("injected crash before SQLite restore commit");
                return Unit.INSTANCE;
            });
            dream.getHomeManager().restore(home.getId(), backup).whenComplete((restored, error) -> {
                check(error != null, "restore interrupted at committed checkpoint");
                getServer().getAsyncScheduler().runNow(this, ioTask -> {
                    try {
                        check(!dream.getWorldManager().getFiles().pendingRestores().isEmpty(), "durable pending restore metadata");
                        Files.writeString(Path.of("crash-done.flag"), "forced process halt after world commit and before SQLite restore\n");
                        getLogger().info("DREAM PROBE CRASH CHECKPOINT PASS world committed + SQLite not restored; halting isolated server process");
                        Runtime.getRuntime().halt(0);
                    } catch (Throwable failure) { failAsync(failure); }
                });
            });
            phase = 99;
        }
    }

    private ym.dreamSkyblock.model.HomeTemplate template(Home home) { return dream.getConfigService().getTemplates().get(home.getTemplateId()); }
    private void visit(Home selected, Runnable next) {
        dream.getWorldManager().load(selected, template(selected), null, ok -> {
            try { check(ok, "visit " + selected.getId()); next.run(); } catch (Throwable error) { fail(error); }
            return Unit.INSTANCE;
        });
    }
    private <T> void complete(CompletableFuture<T> future, java.util.function.Consumer<T> next) {
        future.whenComplete((result,error) -> getServer().getGlobalRegionScheduler().execute(this, () -> {
            if (error != null) fail(error); else try { next.accept(result); } catch (Throwable ex) { fail(ex); }
        }));
    }
    private void failAsync(Throwable error) { getServer().getGlobalRegionScheduler().execute(this, () -> fail(error)); }
    private void fail(Throwable error) { getLogger().log(java.util.logging.Level.SEVERE, "DREAM PROBE FAIL phase=" + phase, error); getServer().shutdown(); }
    private Player player(UUID id, String name, EntityScheduler scheduler) {
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class[]{Player.class}, (p,m,a) -> switch(m.getName()) {
            case "getUniqueId" -> id;
            case "getName" -> name;
            case "getScheduler" -> scheduler;
            case "hasPermission" -> "dreamskyblock.use".equals(a[0]);
            case "isOnline" -> false;
            default -> defaultValue(m.getReturnType());
        });
    }
    private static void check(boolean value, String reason) { if (!value) throw new IllegalStateException(reason); }
    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive() || type == void.class) return null;
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte)0;
        if (type == short.class) return (short)0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        if (type == double.class) return 0D;
        if (type == char.class) return '\0';
        return null;
    }
}
