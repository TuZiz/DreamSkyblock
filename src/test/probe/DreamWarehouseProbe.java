import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextDecoration;
import ym.dreamSkyblock.DreamSkyblock;
import ym.dreamSkyblock.gui.*;
import ym.dreamSkyblock.text.MessageService;
import java.lang.reflect.*;
import java.util.*;

/** Real Leaf menus/config/clicks; synthetic player and Genlite command availability, no stock mutation. */
public final class DreamWarehouseProbe extends JavaPlugin {
    private DreamSkyblock dream;
    private HomeGuiManager gui;
    private Server realServer;
    private Field serverField;
    private Player player;
    private Inventory top;
    private InventoryView view;
    private UUID id;
    private boolean enabled = true, use = true, commandResult = true;
    private int phase, ticks, closes, commands, before;
    private final List<String> messages = new ArrayList<>();

    @Override public void onEnable() {
        getServer().getGlobalRegionScheduler().runAtFixedRate(this, task -> {
            try {
                check(++ticks < 120, "initialization timeout");
                if (phase == 0) {
                    dream = (DreamSkyblock) getServer().getPluginManager().getPlugin("DreamSkyblock");
                    if (dream == null || !dream.getHomeManager().getReady()) return;
                    setup(); click(); phase++; return;
                }
                if (phase == 1) {
                    check(commands == 1 && closes == 1, "warehouse click closes menu and dispatches namespaced command once");
                    enabled = false; gui.openMain(player); before = closes; click(); phase++; return;
                }
                if (phase == 2) {
                    check(commands == 1 && closes == before && last().contains("中央仓库暂时不可用"), "missing Genlite keeps menu and reports localized error");
                    enabled = true; commandResult = false; gui.openMain(player); click(); phase++; return;
                }
                if (phase == 3) {
                    check(commands == 2 && last().contains("中央仓库暂时不可用"), "unhandled command reports error");
                    gui.openMain(player); click(); use = false; phase++; return;
                }
                if (phase == 4) {
                    check(commands == 2, "delayed click rechecks permission"); use = true;
                    gui.openMain(player); click(); top = Bukkit.createInventory(null, 9); phase++; return;
                }
                check(commands == 2, "replaced inventory ignores stale click");
                top = Bukkit.createInventory(null, 9);
                serverField.set(dream, realServer);
                getLogger().info("DREAM WAREHOUSE ALL PASS: slot34/legacy migration/real inventory item styles/real click/use-only/namespaced command/plugin absent/command failure/permission recheck/stale session");
                task.cancel(); getServer().shutdown();
            } catch (Throwable error) {
                try { if (serverField != null) serverField.set(dream, realServer); } catch (Exception cleanupError) { error.addSuppressed(cleanupError); }
                getLogger().log(java.util.logging.Level.SEVERE, "DREAM WAREHOUSE FAILED", error);
                task.cancel(); getServer().shutdown();
            }
        }, 20L, 4L);
    }

    private void setup() throws Exception {
        realServer = getServer();
        id = dream.getHomeManager().all().stream().filter(h -> h.getStatus().equals("ACTIVE") && !h.getOwners().isEmpty()).findFirst().orElseThrow().getOwners().iterator().next();
        top = Bukkit.createInventory(null, 9);
        player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class[]{Player.class}, (p,m,a) -> switch (m.getName()) {
            case "getUniqueId" -> id; case "getName" -> "WarehouseOwner"; case "isOnline", "isValid" -> true;
            case "hasPermission" -> use && "dreamskyblock.use".equals(a[0]);
            case "getServer" -> dream.getServer(); case "getWorld" -> realServer.getWorld("world");
            case "getOpenInventory" -> view;
            case "openInventory" -> { top = (Inventory) a[0]; yield view; }
            case "closeInventory" -> { closes++; yield null; }
            case "performCommand" -> { check(Bukkit.isPrimaryThread(), "command on main thread"); check("genlite:genlite warehouse".equals(a[0]), "exact command"); commands++; yield commandResult; }
            case "sendMessage" -> { if (a != null) for (Object value : a) if (value instanceof Component c) messages.add(plain(c)); yield null; }
            case "hashCode" -> System.identityHashCode(p); case "equals" -> p == a[0];
            default -> defaults(m.getReturnType());
        });
        view = (InventoryView) Proxy.newProxyInstance(InventoryView.class.getClassLoader(), new Class[]{InventoryView.class}, (p,m,a) -> switch (m.getName()) {
            case "getTopInventory" -> top; case "getPlayer" -> player; case "getType" -> InventoryType.CHEST; default -> defaults(m.getReturnType());
        });
        PluginManager manager = (PluginManager) Proxy.newProxyInstance(PluginManager.class.getClassLoader(), new Class[]{PluginManager.class}, (p,m,a) ->
            m.getName().equals("isPluginEnabled") && "Genlite".equals(a[0]) ? enabled : m.invoke(realServer.getPluginManager(), a));
        Server directory = (Server) Proxy.newProxyInstance(Server.class.getClassLoader(), new Class[]{Server.class}, (p,m,a) ->
            m.getName().equals("getPluginManager") ? manager : m.invoke(realServer, a));
        serverField = JavaPlugin.class.getDeclaredField("server"); serverField.setAccessible(true); serverField.set(dream, directory);
        gui = new HomeGuiManager(dream, dream.getConfigService(), dream.getHomeManager(), new MessageService(dream.getConfigService().getMessages()));
        gui.openMain(player);
        check("warehouse".equals(((HomeMenuHolder) top.getHolder()).getActions().get(34)), "slot34 action from legacy config");
        var item = top.getItem(34); check(item.getType() == Material.CHEST, "slot34 chest");
        nonItalic(item.getItemMeta().itemName()); item.getItemMeta().lore().forEach(DreamWarehouseProbe::nonItalic);
        var yaml = new org.bukkit.configuration.file.YamlConfiguration();
        try (var input = dream.getResource("gui/island.yml")) { yaml.loadFromString(new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)); }
        var samples = new ArrayList<Map<String, Object>>();
        sample(samples, "gui/island.yml", "components.warehouse.name", yaml.getString("components.warehouse.name"), item.getItemMeta().itemName());
        var lore = yaml.getStringList("components.warehouse.lore");
        for (int i = 0; i < lore.size(); i++) sample(samples, "gui/island.yml", "components.warehouse.lore." + i, lore.get(i), item.getItemMeta().lore().get(i));
        var language = dream.getConfigService().getMessages();
        sample(samples, "lang/zh_cn.yml", "gui.warehouse-unavailable", language.getString("gui.warehouse-unavailable"), new MessageService(language).component("gui.warehouse-unavailable", Map.of()));
        java.nio.file.Files.writeString(java.nio.file.Path.of("../../target/verification/neuron/warehouse-new-components.json"), new com.google.gson.Gson().toJson(samples));
    }

    private void sample(List<Map<String, Object>> samples, String resource, String field, String raw, Component component) throws Exception {
        String hash = HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        String json = net.kyori.adventure.text.serializer.gson.GsonComponentSerializer.gson().serialize(component);
        samples.add(Map.of("path", "src/main/resources/" + resource, "field", field, "text_hash", hash,
            "component", com.google.gson.JsonParser.parseString(json), "runtime", "Real HomeMenuRenderer ItemMeta on isolated Leaf26.2 build124", "placeholders", List.of()));
    }

    private void click() { var click = new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, 34, ClickType.LEFT, InventoryAction.PICKUP_ALL); gui.click(click); check(click.isCancelled(), "click protected"); }
    private String last() { return messages.isEmpty() ? "" : messages.getLast(); }
    private static void nonItalic(Component c) { check(c.decoration(TextDecoration.ITALIC) == TextDecoration.State.FALSE, "all item descendants italic=false"); c.children().forEach(DreamWarehouseProbe::nonItalic); }
    private static String plain(Component c) { var text = new StringBuilder(c instanceof TextComponent t ? t.content() : ""); c.children().forEach(child -> text.append(plain(child))); return text.toString(); }
    private static void check(boolean ok, String detail) { if (!ok) throw new AssertionError(detail); }
    private static Object defaults(Class<?> t) { if (!t.isPrimitive()) return null; if (t == boolean.class) return false; if (t == int.class) return 0; if (t == long.class) return 0L; if (t == double.class) return 0d; if (t == float.class) return 0f; return null; }
}
