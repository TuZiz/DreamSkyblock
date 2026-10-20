import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;
import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import ym.dreamSkyblock.DreamSkyblock;
import ym.dreamSkyblock.gui.*;
import ym.dreamSkyblock.text.MessageService;
import ym.dreamSkyblock.world.WorldSpawnTeleport;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** Isolated Leaf: real configuration, renderer, click dispatch and service; synthetic player/teleport result. */
public final class DreamSpawnProbe extends JavaPlugin {
    private DreamSkyblock dream;
    private HomeGuiManager gui;
    private WorldSpawnTeleport teleport;
    private Server realServer;
    private Field serverField;
    private Actor actor;
    private boolean missing;
    private Location spawn;
    private int phase, ticks;

    @Override public void onEnable() {
        getServer().getGlobalRegionScheduler().runAtFixedRate(this, task -> {
            try {
                check(++ticks < 120, "timeout");
                if (phase == 0) {
                    dream = (DreamSkyblock) getServer().getPluginManager().getPlugin("DreamSkyblock");
                    if (dream == null || !dream.getHomeManager().getReady()) return;
                    setup(); phase++; return;
                }
                if (phase == 1) {
                    check(actor.teleports == 1 && actor.destination.equals(spawn), "click teleports to world spawn");
                    check(actor.closes == 1, "click closes menu");
                    spawn = spawn.clone().add(4, 0, 2);
                    teleport.teleport(actor.player);
                    check(actor.destination.equals(spawn), "each call reads the current spawn");
                    int before = actor.teleports;
                    missing = true; teleport.teleport(actor.player); missing = false;
                    check(actor.teleports == before && actor.last().contains("world 尚未加载"), "missing world has no fallback teleport");
                    actor.use = false; teleport.teleport(actor.player); actor.use = true;
                    actor.online = false; teleport.teleport(actor.player); actor.online = true;
                    check(actor.teleports == before, "permission and offline checks block teleport");
                    actor.future = CompletableFuture.completedFuture(false);
                    teleport.teleport(actor.player); phase++; return;
                }
                if (phase == 2) {
                    check(actor.last().contains("出生点失败"), "cancelled teleport reports failure on owner thread");
                    actor.messages.clear(); actor.future = new CompletableFuture<>();
                    teleport.teleport(actor.player);
                    actor.future.completeExceptionally(new IllegalStateException("expected probe teleport failure"));
                    phase++; return;
                }
                if (phase == 3) {
                    check(actor.last().contains("出生点失败"), "exceptional teleport reports failure");
                    actor.messages.clear(); actor.future = new CompletableFuture<>();
                    teleport.teleport(actor.player); actor.use = false; actor.future.complete(false);
                    phase++; return;
                }
                check(actor.messages.isEmpty(), "late result rechecks permission");
                serverField.set(dream, realServer);
                getLogger().info("DREAM SPAWN ALL PASS: legacy config top-left/real click/current spawn/missing world/use-only/offline/cancel/exception/late permission/owner thread");
                task.cancel(); getServer().shutdown();
            } catch (Throwable error) {
                try { if (serverField != null) serverField.set(dream, realServer); } catch (Exception cleanupError) { error.addSuppressed(cleanupError); }
                getLogger().log(java.util.logging.Level.SEVERE, "DREAM SPAWN FAILED", error);
                task.cancel(); getServer().shutdown();
            }
        }, 20L, 4L);
    }

    private void setup() throws Exception {
        check(Bukkit.isPrimaryThread(), "main thread");
        realServer = getServer(); spawn = realServer.getWorld("world").getSpawnLocation();
        var home = dream.getHomeManager().all().stream().filter(h -> h.getStatus().equals("ACTIVE") && !h.getOwners().isEmpty()).findFirst().orElseThrow();
        actor = new Actor(home.getOwners().iterator().next());
        World world = (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class[]{World.class}, (p,m,a) ->
            m.getName().equals("getSpawnLocation") ? spawn.clone() : m.invoke(realServer.getWorld("world"), a));
        Server directory = (Server) Proxy.newProxyInstance(Server.class.getClassLoader(), new Class[]{Server.class}, (p,m,a) -> {
            if (m.getName().equals("getWorld") && "world".equals(a[0])) return missing ? null : world;
            if (m.getName().equals("getPlayer") && actor.id.equals(a[0])) return actor.player;
            return m.invoke(realServer, a);
        });
        serverField = JavaPlugin.class.getDeclaredField("server"); serverField.setAccessible(true); serverField.set(dream, directory);
        gui = new HomeGuiManager(dream, dream.getConfigService(), dream.getHomeManager(), new MessageService(dream.getConfigService().getMessages()));
        teleport = new WorldSpawnTeleport(dream, new MessageService(dream.getConfigService().getMessages()));
        gui.openMain(actor.player);
        var holder = (HomeMenuHolder) actor.top.getHolder();
        check(holder.getRequest().getMenu() == HomeMenu.ISLAND && "spawn".equals(holder.getActions().get(0)), "legacy config places action at slot zero");
        check(actor.top.getItem(0).getType() == Material.COMPASS, "top-left compass");
        exportComponents();
        var click = new InventoryClickEvent(actor.view, InventoryType.SlotType.CONTAINER, 0, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        gui.click(click); check(click.isCancelled(), "inventory click stays cancelled");
    }

    private void exportComponents() throws Exception {
        var samples = new ArrayList<Map<String, Object>>();
        var placeholders = new HashMap<String, String>();
        for (String key : List.of("owner", "level", "border", "members", "limit", "state", "experience", "next_threshold", "remaining_value", "player", "template", "home_id", "backup_id", "member")) placeholders.put(key, "3");
        for (String resource : List.of("gui/island.yml", "lang/zh_cn.yml")) {
            var yaml = new org.bukkit.configuration.file.YamlConfiguration();
            try (var input = dream.getResource(resource)) { yaml.loadFromString(new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)); }
            var messages = new MessageService(yaml);
            for (var entry : yaml.getValues(true).entrySet()) {
                String field = entry.getKey();
                if (resource.startsWith("gui/") && !(field.equals("title") || field.endsWith(".name") || field.endsWith(".lore"))) continue;
                if (entry.getValue() instanceof String raw) sample(samples, resource, field, raw, messages, placeholders);
                else if (entry.getValue() instanceof List<?> lines) for (int i = 0; i < lines.size(); i++)
                    if (lines.get(i) instanceof String raw) sample(samples, resource, field + "." + i, raw, messages, placeholders);
            }
        }
        java.nio.file.Files.writeString(java.nio.file.Path.of("../../target/verification/neuron/world-spawn-components.json"), new com.google.gson.Gson().toJson(samples));
    }

    private void sample(List<Map<String, Object>> samples, String resource, String field, String raw, MessageService messages, Map<String, String> placeholders) throws Exception {
        var component = messages.componentRaw(raw, placeholders);
        String hash = HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        String json = net.kyori.adventure.text.serializer.gson.GsonComponentSerializer.gson().serialize(component);
        samples.add(Map.of("path", "src/main/resources/" + resource, "field", field, "text_hash", hash,
            "component", com.google.gson.JsonParser.parseString(json), "runtime", "MessageService on isolated Leaf26.2 build124", "placeholders", new ArrayList<>(placeholders.keySet())));
    }

    private final class Actor {
        final UUID id; final Player player; InventoryView view;
        Inventory top; Location destination; int teleports, closes;
        boolean online = true, use = true;
        CompletableFuture<Boolean> future = CompletableFuture.completedFuture(true);
        final List<String> messages = new ArrayList<>();
        Actor(UUID id) {
            this.id = id; top = Bukkit.createInventory(null, 9);
            EntityScheduler scheduler = (EntityScheduler) Proxy.newProxyInstance(EntityScheduler.class.getClassLoader(), new Class[]{EntityScheduler.class}, (p,m,a) -> {
                if (m.getName().equals("run")) return realServer.getGlobalRegionScheduler().run((org.bukkit.plugin.Plugin) a[0], (Consumer) a[1]);
                return defaults(m.getReturnType());
            });
            player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class[]{Player.class}, (p,m,a) -> switch (m.getName()) {
                case "getUniqueId" -> id; case "getName" -> "SpawnOwner";
                case "isOnline", "isValid" -> online;
                case "hasPermission" -> use && "dreamskyblock.use".equals(a[0]);
                case "getWorld" -> realServer.getWorld("world"); case "getServer" -> dream.getServer();
                case "getScheduler" -> scheduler; case "getOpenInventory" -> this.view;
                case "openInventory" -> { top = (Inventory) a[0]; yield this.view; }
                case "closeInventory" -> { closes++; yield null; }
                case "teleportAsync" -> { check(Bukkit.isPrimaryThread(), "teleport starts on main thread"); teleports++; destination = ((Location) a[0]).clone(); yield future; }
                case "sendMessage" -> { check(Bukkit.isPrimaryThread(), "message on owner thread"); if (a != null) for (Object value : a) if (value instanceof Component c) messages.add(plain(c)); yield null; }
                case "hashCode" -> System.identityHashCode(p); case "equals" -> p == a[0];
                default -> defaults(m.getReturnType());
            });
            view = (InventoryView) Proxy.newProxyInstance(InventoryView.class.getClassLoader(), new Class[]{InventoryView.class}, (p,m,a) -> switch (m.getName()) {
                case "getTopInventory" -> top; case "getPlayer" -> player;
                case "getType" -> InventoryType.CHEST; default -> defaults(m.getReturnType());
            });
        }
        String last() { return messages.isEmpty() ? "" : messages.getLast(); }
    }
    private static String plain(Component c) { var out = new StringBuilder(c instanceof TextComponent t ? t.content() : ""); c.children().forEach(child -> out.append(plain(child))); return out.toString(); }
    private static void check(boolean pass, String message) { if (!pass) throw new AssertionError(message); }
    private static Object defaults(Class<?> t) { if (!t.isPrimitive()) return null; if (t == boolean.class) return false; if (t == int.class) return 0; if (t == long.class) return 0L; if (t == double.class) return 0d; if (t == float.class) return 0f; return null; }
}
