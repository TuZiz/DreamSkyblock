import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import ym.dreamSkyblock.DreamSkyblock;
import ym.dreamSkyblock.gui.*;
import ym.dreamSkyblock.text.MessageService;
import java.lang.reflect.Proxy;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Real registered command, menus/inventories/config; synthetic online directory and senders. */
public final class DreamTargetGuiProbe extends JavaPlugin {
    private final List<Actor> actors=new ArrayList<>();
    private Server directory;
    private int ticks;
    @Override public void onEnable() {
        getServer().getGlobalRegionScheduler().runAtFixedRate(this, task -> {
            try {
                check(++ticks<100,"initialization timeout");
                var dream=(DreamSkyblock)getServer().getPluginManager().getPlugin("DreamSkyblock");
                if(dream==null || !dream.getHomeManager().getReady())return;
                runChecks(dream);task.cancel();getServer().shutdown();
            } catch(Throwable error) {
                getLogger().log(java.util.logging.Level.SEVERE,"DREAM TARGET GUI FAILED",error);task.cancel();getServer().shutdown();
            }
        },20L,2L);
    }
    private void runChecks(DreamSkyblock dream) throws Exception {
        check(Bukkit.isPrimaryThread(),"main-thread command and inventory work");
        directory=(Server)Proxy.newProxyInstance(Server.class.getClassLoader(),new Class[]{Server.class},(p,m,a)->switch(m.getName()) {
            case "getOnlinePlayers" -> actors.stream().filter(x->x.online).map(x->x.player).toList();
            case "getPlayerExact" -> actors.stream().filter(x->x.online && x.name.equalsIgnoreCase((String)a[0])).map(x->x.player).findFirst().orElse(null);
            case "getPlayer" -> actors.stream().filter(x->x.online && x.id.equals(a[0])).map(x->x.player).findFirst().orElse(null);
            default -> m.invoke(getServer(),a);
        });
        var home=dream.getHomeManager().all().stream().filter(x->x.getStatus().equals("ACTIVE") && !x.getOwners().isEmpty()).findFirst().orElseThrow();
        var owner=new Actor(home.getOwners().iterator().next(),"TargetOwner",false);
        var guest=new Actor(UUID.randomUUID(),"TargetGuest",false);
        var admin=new Actor(UUID.randomUUID(),"TargetAdmin",true);
        List<String> consoleMessages=new ArrayList<>();
        var console=(ConsoleCommandSender)Proxy.newProxyInstance(ConsoleCommandSender.class.getClassLoader(),new Class[]{ConsoleCommandSender.class},(p,m,a)->switch(m.getName()) {
            case "getServer"->directory;case "getName"->"CONSOLE";case "hasPermission","isOp"->true;
            case "sendMessage"->{capture(consoleMessages,a);yield null;}
            default->defaults(m.getReturnType());
        });
        var command=getServer().getPluginCommand("is");
        check(command==dream.getCommand("home"),"is alias registered to existing command");
        execute(command,console,"is","gui",guest.name);check(guest.menu()==HomeMenu.MAIN,"console opens guest creation menu");
        execute(command,admin.player,"is","gui",owner.name);check(owner.menu()==HomeMenu.ISLAND,"admin opens target island instead of own menu");
        execute(command,console,"is","gui",owner.id.toString());check(owner.menu()==HomeMenu.ISLAND,"UUID lookup opens member menu");
        execute(command,admin.player,"家","菜单",guest.name);check(guest.menu()==HomeMenu.MAIN,"Chinese subcommand targets same GUI");
        int before=owner.opens;execute(command,guest.player,"is","gui",owner.name);
        check(owner.opens==before && guest.last().contains("只有管理员"),"ordinary player cannot target another player");
        execute(command,guest.player,"is","gui");check(guest.menu()==HomeMenu.MAIN,"ordinary self GUI still available");
        execute(command,owner.player,"is","gui");check(owner.menu()==HomeMenu.ISLAND,"member self GUI still available");
        execute(command,console,"is","gui");check(last(consoleMessages).contains("用法：/is gui"),"console missing target gets localized usage");
        before=guest.opens;execute(command,console,"is","gui",guest.name,"extra");
        check(guest.opens==before && last(consoleMessages).contains("用法：/is gui"),"extra args cannot open menu");
        execute(command,console,"is","gui","TargetG");check(last(consoleMessages).contains("找不到该在线玩家"),"partial names never target a different player");
        execute(command,console,"is","gui","bad-uuid");check(last(consoleMessages).contains("找不到该在线玩家"),"bad UUID safe error");
        guest.online=false;execute(command,console,"is","gui",guest.name);check(guest.opens==before,"offline target not opened");guest.online=true;
        guest.use=false;execute(command,console,"is","gui",guest.name);
        check(guest.opens==before && guest.last().contains("没有操作家园的权限"),"target use permission is preserved");guest.use=true;
        var ready=dream.getHomeManager().getClass().getDeclaredField("ready");ready.setAccessible(true);ready.setBoolean(dream.getHomeManager(),false);
        try { execute(command,console,"is","gui",guest.name);check(guest.opens==before && last(consoleMessages).contains("家园数据正在加载"),"not-ready request does not open"); }
        finally { ready.setBoolean(dream.getHomeManager(),true); }
        check(command.tabComplete(admin.player,"is",new String[]{"gui","TargetG"}).equals(List.of(guest.name)),"admin online player completion");
        check(command.tabComplete(console,"家",new String[]{"菜单","TargetO"}).equals(List.of(owner.name)),"console Chinese completion");
        check(command.tabComplete(guest.player,"is",new String[]{"gui",""}).isEmpty(),"ordinary player cannot see target completion");
        check(actors.stream().allMatch(x->x.teleports==0),"all targeted/self GUI calls avoid teleporting players");
        var raw=dream.getConfigService().getMessages().getString("gui.command-usage");
        var rendered=new MessageService(dream.getConfigService().getMessages()).component("gui.command-usage",Map.of());
        var serializer=net.kyori.adventure.text.serializer.gson.GsonComponentSerializer.gson();
        var hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        String evidence="[{\"path\":\"src/main/resources/lang/zh_cn.yml\",\"field\":\"gui.command-usage\",\"text_hash\":\""+hash+"\",\"component\":"+serializer.serialize(rendered)+",\"runtime\":\"MessageService on Leaf26.2\",\"placeholders\":[]}]";
        Files.writeString(Path.of("../../target/verification/neuron/target-gui-components.json"),evidence);
        getLogger().info("DREAM TARGET GUI ALL PASS: console/admin/name/UUID/Chinese/member+guest/self/permission/online/arity/ready/completion/no-teleport + real MessageService component");
    }
    private void execute(PluginCommand command,CommandSender sender,String root,String... args) { check(command.execute(sender,root,args),"command handled"); }
    private final class Actor {
        final UUID id;final String name;final boolean admin;final Player player;
        final List<String> messages=new ArrayList<>();boolean online=true,use=true;int opens,teleports;Inventory top;InventoryView view;
        Actor(UUID id,String name,boolean admin) {
            this.id=id;this.name=name;this.admin=admin;top=Bukkit.createInventory(null,9);
            player=(Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class[]{Player.class},(p,m,a)->switch(m.getName()) {
                case "getServer"->directory;case "getName"->name;case "getUniqueId"->id;case "isOnline","isValid"->online;
                case "hasPermission"->"dreamskyblock.admin".equals(a[0])?admin:use;
                case "getWorld"->getServer().getWorlds().getFirst();case "getOpenInventory"->view;
                case "openInventory"->{check(Bukkit.isPrimaryThread(),"inventory main thread");top=(Inventory)a[0];opens++;yield view;}
                case "teleportAsync","teleport"->{teleports++;throw new AssertionError("GUI must not teleport");}
                case "sendMessage"->{capture(messages,a);yield null;}
                case "hashCode"->System.identityHashCode(p);case "equals"->p==a[0];case "toString"->name;
                default->defaults(m.getReturnType());
            });
            view=(InventoryView)Proxy.newProxyInstance(InventoryView.class.getClassLoader(),new Class[]{InventoryView.class},(p,m,a)->switch(m.getName()) {
                case "getTopInventory"->top;case "getPlayer"->player;case "getType"->InventoryType.CHEST;default->defaults(m.getReturnType());
            });actors.add(this);
        }
        HomeMenu menu(){return ((HomeMenuHolder)top.getHolder()).getRequest().getMenu();}
        String last(){return DreamTargetGuiProbe.last(messages);}
    }
    private static void capture(List<String> messages,Object[] args){if(args!=null)for(var value:args)if(value instanceof Component c)messages.add(plain(c));else if(value instanceof String s)messages.add(s);}
    private static String plain(Component c){var text=new StringBuilder(c instanceof TextComponent t?t.content():"");for(var child:c.children())text.append(plain(child));return text.toString();}
    private static String last(List<String> values){return values.isEmpty()?"":values.getLast();}
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static Object defaults(Class<?> t){if(!t.isPrimitive())return null;if(t==boolean.class)return false;if(t==int.class)return 0;if(t==long.class)return 0L;if(t==double.class)return 0d;if(t==float.class)return 0f;if(t==short.class)return (short)0;if(t==byte.class)return (byte)0;return null;}
}
