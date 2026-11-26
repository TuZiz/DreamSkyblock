import java.nio.file.*;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Invero 1.4.17: retire old refreshes and sync only player storage slots. */
public class PatchInveroWindowRefresh {
    static final String TARGET = "cc/trixey/invero/ui/bukkit/InventoryPacket.class";

    public static byte[] patch(byte[] data) {
        var node = new ClassNode();
        new ClassReader(data).accept(node, 0);
        int changes = 0;
        int storageChanges = 0;
        for (var method : node.methods) {
            if (method.name.equals("updatePlayerItems") && method.desc.equals("(Z)V")) {
                for (var instruction : method.instructions.toArray()) {
                    if (instruction instanceof MethodInsnNode call
                            && call.owner.equals("cc/trixey/invero/ui/bukkit/InventoryPacket")
                            && call.name.equals("update") && call.desc.equals("()V")) {
                        // The original call sends the entire menu every second, replacing
                        // hovered menu items even when only player storage needs syncing.
                        // Leave initial population and explicit menu rendering unchanged.
                        var slots = new InsnList();
                        slots.add(new VarInsnNode(Opcodes.ALOAD, 0));
                        slots.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, call.owner,
                                "getContainerSize", "()I", false));
                        slots.add(new VarInsnNode(Opcodes.ALOAD, 0));
                        slots.add(new FieldInsnNode(Opcodes.GETFIELD, call.owner,
                                "windowItems", "[Lorg/bukkit/inventory/ItemStack;"));
                        slots.add(new InsnNode(Opcodes.ARRAYLENGTH));
                        slots.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                                "java/util/stream/IntStream", "range",
                                "(II)Ljava/util/stream/IntStream;", true));
                        slots.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE,
                                "java/util/stream/IntStream", "toArray", "()[I", true));
                        method.instructions.insertBefore(call, slots);
                        method.instructions.set(call, new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
                                call.owner, "update", "([I)V", false));
                        storageChanges++;
                    }
                }
            }
            if (!method.name.equals("open$lambda$7")) continue;
            for (var instruction : method.instructions.toArray()) {
                if (instruction instanceof MethodInsnNode call
                        && call.getOpcode() == Opcodes.INVOKEVIRTUAL
                        && call.owner.equals("cc/trixey/invero/ui/bukkit/BukkitWindow")
                        && call.name.equals("isViewing") && call.desc.equals("()Z")) {
                    // isViewing only tests a closed flag which is never reset on close.
                    // Registration tracks the exact current window, unlike shared ID 119.
                    method.instructions.set(call, new MethodInsnNode(Opcodes.INVOKESTATIC,
                            "cc/trixey/invero/ui/bukkit/api/WindowManagerKt", "isRegistered",
                            "(Lcc/trixey/invero/ui/bukkit/BukkitWindow;)Z", false));
                    changes++;
                }
            }
        }
        if (changes != 1 || storageChanges != 1)
            throw new IllegalStateException("Unexpected Invero refresh bytecode: " + changes + "/" + storageChanges);
        var writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    public static void main(String[] args) throws Exception {
        Path source = Path.of(args[0]), output = Path.of(args[1]);
        if (source.toAbsolutePath().equals(output.toAbsolutePath()))
            throw new IllegalArgumentException("Source and output must differ");
        // Validate before opening the output, so mismatched versions leave no partial JAR.
        try (var input = new ZipFile(source.toFile())) {
            var target = input.getEntry(TARGET);
            if (target == null) throw new IllegalStateException("InventoryPacket missing");
            byte[] patched = patch(input.getInputStream(target).readAllBytes());
            try (var out = new ZipOutputStream(Files.newOutputStream(output))) {
                var entries = input.entries();
                while (entries.hasMoreElements()) {
                    var entry = entries.nextElement();
                    var copy = new ZipEntry(entry.getName());
                    copy.setTime(entry.getTime());
                    out.putNextEntry(copy);
                    out.write(entry.getName().equals(TARGET) ? patched
                            : input.getInputStream(entry).readAllBytes());
                    out.closeEntry();
                }
            }
        }
        System.out.println("Patched obsolete-window guard and player-storage-only synchronization; all other entries preserved.");
    }
}
