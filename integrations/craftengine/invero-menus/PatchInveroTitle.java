import java.nio.file.*;
import java.util.zip.*;
import java.io.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Invero 1.4.17 modern title: preserve Adventure font/color via JSON. */
public class PatchInveroTitle {
    public static void main(String[] args) throws Exception {
        Path source=Path.of(args[0]), output=Path.of(args[1]);
        int changes=0;
        try(var in=new ZipFile(source.toFile());var out=new ZipOutputStream(Files.newOutputStream(output))) {
            var entries=in.entries();
            while(entries.hasMoreElements()) {
                var e=entries.nextElement(); byte[] data=in.getInputStream(e).readAllBytes();
                if(e.getName().equals("cc/trixey/invero/ui/bukkit/nms/NMSImplModern.class")) {
                    var node=new ClassNode(); new ClassReader(data).accept(node,0);
                    for(var method:node.methods) if(method.name.equals("sendWindowOpen")) {
                        for(var ins:method.instructions.toArray()) if(ins instanceof MethodInsnNode call) {
                            if(call.owner.equals("net/kyori/adventure/text/serializer/legacy/LegacyComponentSerializer") && call.name.equals("legacySection")) {
                                method.instructions.remove(call); changes++;
                            } else if(call.owner.equals("net/kyori/adventure/text/serializer/legacy/LegacyComponentSerializer") && call.name.equals("serialize")) {
                                method.instructions.set(call,new MethodInsnNode(Opcodes.INVOKESTATIC,"cc/trixey/invero/common/message/Message","transformToJson","(Lnet/kyori/adventure/text/Component;)Ljava/lang/String;",false)); changes++;
                            } else if(call.owner.equals("net/minecraft/network/chat/Component") && call.name.equals("literal")) {
                                method.instructions.set(call,new MethodInsnNode(Opcodes.INVOKESTATIC,"org/bukkit/craftbukkit/util/CraftChatMessage","fromJSON","(Ljava/lang/String;)Lnet/minecraft/network/chat/Component;",false)); changes++;
                            }
                        }
                    }
                    if(changes!=3)throw new IllegalStateException("Unexpected Invero bytecode; refusing patch: "+changes);
                    var writer=new ClassWriter(0);node.accept(writer);data=writer.toByteArray();
                }
                var entry=new ZipEntry(e.getName());entry.setTime(e.getTime());out.putNextEntry(entry);out.write(data);out.closeEntry();
            }
        }
        if(changes!=3)throw new IllegalStateException("Modern class missing");
        System.out.println("Patched only NMSImplModern title conversion (3 call sites).");
    }
}
