import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import org.objectweb.asm.tree.*;

/** Executes the installed callback bytecode with only its platform boundaries replaced. */
public class VerifyInveroWindowRefresh {
    public static class Window {
        static Window current;
        public boolean isViewing() { return true; } // Installed closed flag stays false.
    }
    public static class Windows {
        public static boolean isRegistered(Window window) { return Window.current == window; }
    }
    public static class Packet {
        final Window window;
        int updates;
        int fullUpdates;
        int[] sentSlots;
        public Item[] windowItems = new Item[81];
        final Player player = new Player();
        Packet(Window window) { this.window = window; }
        public Window getWindow() { return window; }
        public void updatePlayerItems(boolean send) { if (send) updates++; }
        public boolean getHidePlayerInventory() { return false; }
        public Player getViewer() { return player; }
        public int getContainerSize() { return 45; }
        public void update() { fullUpdates++; }
        public void update(int... slots) { sentSlots = slots; }
    }
    public static class Item {}
    public static class Player { final Item[] storage = new Item[36]; }
    public static class Storage {
        public static Item[] copyStorage(Player player) { return player.storage.clone(); }
    }
    public interface Task { void cancel(); }
    public static class TaskState implements Task {
        int cancellations;
        public void cancel() { cancellations++; }
    }
    public static class Unit { public static final Unit INSTANCE = new Unit(); }
    public static class Intrinsics {
        public static void checkNotNullParameter(Object value, String name) {
            Objects.requireNonNull(value, name);
        }
    }
    static class Loader extends ClassLoader {
        Class<?> define(byte[] bytes) { return defineClass(null, bytes, 0, bytes.length); }
    }
    static java.lang.reflect.Method extract(Path jar, String methodName) throws Exception {
        var node = new ClassNode();
        try (var zip = new ZipFile(jar.toFile())) {
            new ClassReader(zip.getInputStream(zip.getEntry(PatchInveroWindowRefresh.TARGET)))
                    .accept(node, 0);
        }
        var method = node.methods.stream().filter(m -> m.name.equals(methodName))
                .findFirst().orElseThrow();
        var isolated = new ClassNode();
        isolated.version = Opcodes.V17;
        isolated.access = Opcodes.ACC_PUBLIC;
        isolated.name = "RefreshCallback";
        isolated.superName = "java/lang/Object";
        method.access = Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC;
        method.name = "run";
        boolean storage = methodName.equals("updatePlayerItems");
        if (storage) method.desc = "(Lcc/trixey/invero/ui/bukkit/InventoryPacket;Z)V";
        isolated.methods.add(method);
        var mappings = new HashMap<String, String>();
        mappings.put("cc/trixey/invero/ui/bukkit/InventoryPacket", Type.getInternalName(Packet.class));
        mappings.put("cc/trixey/invero/ui/bukkit/BukkitWindow", Type.getInternalName(Window.class));
        mappings.put("cc/trixey/invero/ui/bukkit/api/WindowManagerKt", Type.getInternalName(Windows.class));
        mappings.put("cc/trixey/invero/taboolib/common/platform/service/PlatformExecutor$PlatformTask", Type.getInternalName(Task.class));
        mappings.put("kotlin220/Unit", Type.getInternalName(Unit.class));
        mappings.put("kotlin220/jvm/internal/Intrinsics", Type.getInternalName(Intrinsics.class));
        mappings.put("org/bukkit/inventory/ItemStack", Type.getInternalName(Item.class));
        mappings.put("org/bukkit/entity/Player", Type.getInternalName(Player.class));
        mappings.put("cc/trixey/invero/ui/bukkit/PlayerStorageKt", Type.getInternalName(Storage.class));
        var writer = new ClassWriter(0);
        isolated.accept(new ClassRemapper(writer, new SimpleRemapper(mappings)));
        return new Loader().define(writer.toByteArray()).getMethod("run", Packet.class,
                storage ? boolean.class : Task.class);
    }
    static void check(boolean value, String detail) {
        if (!value) throw new AssertionError(detail);
    }
    public static void main(String[] args) throws Exception {
        var before = extract(Path.of(args[0]), "open$lambda$7");
        var after = extract(Path.of(args[1]), "open$lambda$7");
        var old = new Packet(new Window());
        Window.current = old.window;
        after.invoke(null, old, new TaskState());
        check(old.updates == 1, "Active menu must refresh");
        var current = new Packet(new Window());
        Window.current = current.window;
        var baselineTask = new TaskState();
        before.invoke(null, old, baselineTask);
        check(old.updates == 2 && baselineTask.cancellations == 0,
                "Baseline must reproduce stale refresh after switching");
        var obsoleteTask = new TaskState();
        after.invoke(null, old, obsoleteTask);
        check(old.updates == 2 && obsoleteTask.cancellations == 1,
                "Switched menu must cancel without sending old items");
        after.invoke(null, current, new TaskState());
        check(current.updates == 1, "New menu must still refresh");
        Window.current = null;
        var closedTask = new TaskState();
        after.invoke(null, current, closedTask);
        check(current.updates == 1 && closedTask.cancellations == 1,
                "Closed menu must cancel without sending items");
        var beforeSync = extract(Path.of(args[0]), "updatePlayerItems");
        var afterSync = extract(Path.of(args[1]), "updatePlayerItems");
        var baseline = new Packet(new Window());
        beforeSync.invoke(null, baseline, true);
        check(baseline.fullUpdates == 1, "Baseline must resend whole menu");
        var synced = new Packet(new Window());
        Item menuItem = new Item();
        synced.windowItems[30] = menuItem;
        for (int i = 0; i < 36; i++) synced.player.storage[i] = new Item();
        afterSync.invoke(null, synced, false);
        check(synced.sentSlots == null && synced.fullUpdates == 0,
                "Initial population must not send a partial update");
        for (int repeat = 0; repeat < 5; repeat++) afterSync.invoke(null, synced, true);
        check(synced.fullUpdates == 0 && synced.sentSlots.length == 36,
                "Periodic synchronization must avoid full menu resends");
        for (int i = 0; i < 36; i++) {
            check(synced.sentSlots[i] == i + 45, "Only player inventory slots may be sent");
            int slot = 45 + (i < 9 ? i + 27 : i - 9);
            check(synced.windowItems[slot] == synced.player.storage[i], "Storage mapping changed");
        }
        check(synced.windowItems[30] == menuItem, "Hovered menu item must remain intact");
        System.out.println("PASS: baseline stale/full-menu resends reproduced; active/switch/close and storage-only sync pass.");
    }
}
