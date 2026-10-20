import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;
import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import ym.dreamSkyblock.DreamSkyblock;
import ym.dreamSkyblock.gui.*;
import ym.dreamSkyblock.model.*;
import ym.dreamSkyblock.text.MessageService;
import ym.dreamSkyblock.reminder.NoHomeReminder;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import kotlin.Unit;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;

/** Synthetic players and events; real Bukkit inventories, metadata, DB and world services. */
public final class DreamGuiProbe extends JavaPlugin {
    private DreamSkyblock dream;
    private HomeGuiManager gui;
    private Actor owner, friend;
    private Home home;
    private int phase, ticks;
    private Inventory original;
    private ItemStack[] settingsBefore;
    private int settingsSlot;
    private NoHomeReminder reminders;
    private Actor denied;
    private int reminderStageTick;
    private CompletableFuture<Boolean> treeScheduled;
    static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000077");
    static final UUID FRIEND = UUID.fromString("00000000-0000-0000-0000-000000000055");
    @Override public void onEnable() {
        getServer().getGlobalRegionScheduler().runAtFixedRate(this, task -> {
            try { step(); } catch(Throwable e) { getLogger().log(java.util.logging.Level.SEVERE,"DREAM GUI PROBE FAILED phase="+phase,e); task.cancel(); getServer().shutdown(); }
        },20L,5L);
    }
    private void step() throws Exception {
        check(++ticks<700,"timeout phase="+phase);
        if(phase==-1) {
            if(dream.getHomeManager().byPlayer(OWNER)==null)phase=0;
        } else if(phase==0) {
            dream=(DreamSkyblock)getServer().getPluginManager().getPlugin("DreamSkyblock");
            if(dream==null || !dream.getHomeManager().getReady())return;
            gui=new HomeGuiManager(dream,dream.getConfigService(),dream.getHomeManager(),new MessageService(dream.getConfigService().getMessages()));
            owner=new Actor(OWNER,"GuiOwner"); friend=new Actor(FRIEND,"GuiFriend");
            if(dream.getHomeManager().byPlayer(OWNER)!=null) { dream.getHomeManager().delete(owner.player);phase=-1;return; }
            check(!dream.getConfigService().getCreationTemplates().containsKey("vanilla"),"old configured vanilla excluded from creation");
            check(dream.getConfigService().getTemplates().containsKey("vanilla"),"legacy template kept for existing loads");
            dream.getHomeManager().create(owner.player,"vanilla");
            check(dream.getHomeManager().byPlayer(OWNER)==null,"vanilla direct creation rejected");
            gui.open(friend.player,new MenuRequest(HomeMenu.INVITES,0,null,null));
            check(friend.top.getItem(10)==null && friend.has("menu:main"),"no invites leaves blank content with back navigation");
            command(owner, "家"); check(owner.menu()==HomeMenu.MAIN,"Chinese root opens creation without home");
            check(getServer().getPluginCommand("is")==dream.getCommand("home") && getServer().getPluginCommand("dsb")==dream.getCommand("home"),"root aliases registered");
            check(getServer().getPluginCommand("家")==dream.getCommand("home") && getServer().getPluginCommand("回家")==null,"new Chinese root replaces old alias");
            check(owner.has("menu:create_confirm") && owner.has("menu:invites") && !owner.has("teleport"),"guest buttons");
            check(!owner.has("menu:template_selection") && owner.actions().stream().filter("menu:create_confirm"::equals).count()==1,"legacy main has one direct creation entry");
            check(dream.getConfigService().getMenus().size()==11,"preview menu is no longer loaded");
            var mainYaml=dream.getConfigService().getMenus().get(HomeMenu.MAIN);
            check(mainYaml.getStringList("components.create.lore").stream().anyMatch(x->x.contains("<description>")) && mainYaml.getStringList("components.create.lore").stream().anyMatch(x->x.contains("<platform>")),"legacy main includes island introduction in Lore");
            ItemStack marker=new ItemStack(Material.DIAMOND);
            InventoryDragEvent drag=new InventoryDragEvent(owner.view,marker,marker,false,Map.of(10,marker));
            gui.drag(drag); check(drag.isCancelled(),"drag blocked");
            InventoryClickEvent shift=new InventoryClickEvent(owner.view,InventoryType.SlotType.CONTAINER,owner.top.getSize()+1,ClickType.SHIFT_LEFT,InventoryAction.MOVE_TO_OTHER_INVENTORY);
            gui.click(shift);check(shift.isCancelled(),"shift transfer blocked");
            owner.click("menu:create_confirm");phase=1;
        } else if(phase==1) {
            check(owner.menu()==HomeMenu.CONFIRM,"creation button opens second confirmation directly");
            check(((HomeMenuHolder)owner.top.getHolder()).getRequest().getTemplate().equals("skyblock"),"direct confirmation selects valid skyblock template");
            check(dream.getHomeManager().byPlayer(OWNER)==null,"confirmation has no world or membership side effect");
            owner.click("menu:main");phase=3;
        } else if(phase==3) {
            check(owner.menu()==HomeMenu.MAIN && dream.getHomeManager().byPlayer(OWNER)==null,"cancel creates nothing");
            getLogger().info("DREAM GUI DIRECT CREATE PASS island introduction in Lore + preview menu absent + legacy migration + direct second confirmation/cancel");
            gui.openConfirm(owner.player,"skyblock");owner.click("create");owner.close();phase=4;
        } else if(phase==4) {
            check(dream.getHomeManager().byPlayer(OWNER)==null,"closed session ignores queued create");
            gui.openHelp(owner.player);checkHelp(owner);gui.openSettings(owner.player);phase=5;
        } else if(phase==5) {
            check(owner.menu()==HomeMenu.SETTINGS && owner.has("menu:world_rules") && owner.has("menu:visitor_permissions"),"separate setting category buttons");
            owner.click("menu:world_rules");phase=51;
        } else if(phase==51) {
            check(owner.menu()==HomeMenu.WORLD_RULES && !owner.has("toggle:stranger_place"),"world rule screen isolated");
            owner.click("toggle:pvp");phase=6;
        } else if(phase==6) {
            check(dream.getHomeManager().byPlayer(OWNER)==null,"guest settings are read only");
            gui.openConfirm(owner.player,"skyblock");owner.click("create");phase=7;
        } else if(phase==7) {
            home=dream.getHomeManager().byPlayer(OWNER);if(home==null || !home.getStatus().equals("ACTIVE"))return;
            DreamTreeProbe.verify(dream, home);
            treeScheduled=DreamTreeProbe.scheduled(dream,home);phase=71;
        } else if(phase==71) {
            if(!treeScheduled.isDone())return;
            check(treeScheduled.getNow(false),"native automatic leaf ticks complete successfully");
            command(owner,"is","菜单");check(owner.menu()==HomeMenu.ISLAND,"Chinese menu opens island screen");
            check(dream.getCommand("home").tabComplete(owner.player,"家",new String[]{""}).contains("菜单"),"Chinese completions");
            check(!dream.getCommand("home").tabComplete(owner.player,"家",new String[]{""}).contains("恢复"),"admin Chinese completions hidden");
            check(owner.has("teleport") && owner.has("upgrade") && owner.has("menu:members") && !owner.has("menu:create_confirm"),"island buttons");
            original=owner.top;owner.click("upgrade");phase=8;
        } else if(phase==8) {
            check(owner.top==original,"upgrade button opens no sub UI");
            owner.click("menu:settings");phase=9;
        } else if(phase==9) {
            check(owner.menu()==HomeMenu.SETTINGS,"settings hub");
            owner.click("menu:visitor_permissions");phase=91;
        } else if(phase==91) {
            check(owner.menu()==HomeMenu.VISITOR_PERMISSIONS && !owner.has("toggle:pvp"),"visitor screen isolated");
            original=owner.top;settingsBefore=original.getContents();
            settingsSlot=((HomeMenuHolder)original.getHolder()).getActions().entrySet().stream().filter(e->e.getValue().equals("toggle:stranger_place")).findFirst().orElseThrow().getKey();
            owner.click("toggle:stranger_place");phase=92;
        } else if(phase==92) {
            if(!Boolean.TRUE.equals(home.getFlags().get(HomeFlag.STRANGER_PLACE)))return;
            check(owner.top==original,"visitor toggle retains inventory");
            for(int slot=0;slot<original.getSize();slot++)check(slot==settingsSlot ? !Objects.equals(settingsBefore[slot],original.getItem(slot)) : Objects.equals(settingsBefore[slot],original.getItem(slot)),"visitor toggle changes one slot="+slot);
            owner.click("menu:settings");phase=93;
        } else if(phase==93) {
            check(owner.menu()==HomeMenu.SETTINGS,"visitor back to hub");
            owner.click("menu:world_rules");phase=94;
        } else if(phase==94) {
            check(owner.menu()==HomeMenu.WORLD_RULES,"world rules");
            original=owner.top;settingsBefore=original.getContents();
            settingsSlot=((HomeMenuHolder)original.getHolder()).getActions().entrySet().stream().filter(e->e.getValue().equals("toggle:pvp")).findFirst().orElseThrow().getKey();
            owner.click("toggle:pvp");phase=10;
        } else if(phase==10) {
            if(!Boolean.TRUE.equals(home.getFlags().get(HomeFlag.PVP)))return;
            if(!dream.getWorldManager().getLoaded(home).getPVP())return; // Wait for the post-commit main-thread application.
            check(dream.getWorldManager().getLoaded(home).getPVP(),"setting transaction applied to world");
            check(owner.top==original,"settings commit retains open inventory");
            for(int slot=0;slot<original.getSize();slot++)check(slot==settingsSlot ? !Objects.equals(settingsBefore[slot],original.getItem(slot)) : Objects.equals(settingsBefore[slot],original.getItem(slot)),"only toggled setting changes slot="+slot);
            dream.getHomeManager().invite(owner.player,friend.player);
            gui.open(friend.player,new MenuRequest(HomeMenu.INVITES,0,null,null));
            String accept=friend.actions().stream().filter(x->x.startsWith("accept:")).findFirst().orElseThrow();
            boolean nameFound=Arrays.stream(friend.top.getContents()).filter(Objects::nonNull).anyMatch(i->i.getItemMeta().itemName().toString().contains("GuiOwner"));
            check(nameFound,"inviter visible");friend.click(accept);phase=11;
        } else if(phase==11) {
            if(dream.getHomeManager().byPlayer(FRIEND)==null)return;
            check(home.getOwners().size()==2,"GUI invite accepted");
            gui.open(owner.player,new MenuRequest(HomeMenu.MEMBERS,0,null,null));owner.click("member:"+FRIEND);phase=12;
        } else if(phase==12) {
            check(owner.menu()==HomeMenu.MEMBER_CONFIRM,"member removal second confirmation");
            owner.click("remove-member");phase=13;
        } else if(phase==13) {
            if(dream.getHomeManager().byPlayer(FRIEND)!=null)return;
            check(home.getOwners().size()==1 && owner.menu()==HomeMenu.MEMBERS,"GUI removal committed then refreshed");
            gui.openHelp(owner.player);checkHelp(owner);owner.click("menu:main");phase=14;
        } else if(phase==14) {
            check(owner.menu()==HomeMenu.ISLAND,"single help returns to island main");
            checkPaging();
            gui.open(owner.player,new MenuRequest(HomeMenu.MEMBERS,0,null,null));owner.click("member:"+OWNER);phase=15;
        } else if(phase==15) {
            owner.click("remove-member");phase=16;
        } else if(phase==16) {
            check(dream.getHomeManager().byPlayer(OWNER)!=null,"last member cannot leave");
            dream.getHomeManager().invite(owner.player,friend.player);
            dream.getHomeManager().accept(friend.player,dream.getHomeManager().inviteTokens(FRIEND).getFirst());phase=17;
        } else if(phase==17) {
            if(dream.getHomeManager().byPlayer(FRIEND)==null)return;
            gui.open(friend.player,new MenuRequest(HomeMenu.MEMBERS,0,null,null));friend.click("member:"+FRIEND);phase=18;
        } else if(phase==18) {
            friend.click("remove-member");phase=19;
        } else if(phase==19) {
            if(dream.getHomeManager().byPlayer(FRIEND)!=null)return;
            gui.openMain(friend.player);check(friend.has("menu:create_confirm"),"leave returns to guest main");
            command(owner,"家");phase=20;
        } else if(phase==20) {
            if(owner.teleports<2)return; // Teleport completion follows asynchronous chunk/world readiness.
            check(owner.teleports>=2,"return uses teleport service");
            getLogger().info("DREAM GUI PROBE ALL PASS conditional main + direct creation/island Lore/no preview menu + create confirm/cancel/stale + drag/shift + single help categorized Lore/legacy migration + split rule/visitor screens with single-slot updates + Chinese root/subcommands and direct /home teleport + invite accept + remove/leave + paging/blank empty lists + upgrade placeholder + return service");
            if(Boolean.getBoolean("dream.gui.partial")) {
                gui.open(friend.player,new MenuRequest(HomeMenu.INVITES,0,null,null));
                // Simulate a list shrinking between display and the scheduled click.
                ((HomeMenuHolder)friend.top.getHolder()).getActions().put(44,"next");
                original=friend.top;
                friend.click("next");friend.click("menu:main");phase=30;return;
            }
            reminders=new NoHomeReminder(dream,dream.getHomeManager(),new MessageService(dream.getConfigService().getMessages()));
            owner.close();friend.close();
            denied=new Actor(UUID.fromString("00000000-0000-0000-0000-000000000099"),"NoPermission");denied.usePermission=false;
            reminders.start();reminders.start(); // Starting twice must not duplicate the scheduled task.
            reminders.join(new PlayerJoinEvent(owner.player,Component.empty()));
            reminders.join(new PlayerJoinEvent(friend.player,Component.empty()));
            reminders.join(new PlayerJoinEvent(denied.player,Component.empty()));
            reminderStageTick=ticks;phase=21;
        } else if(phase==21) {
            if(ticks-reminderStageTick<8)check(friend.reminderChats==0,"first reminder delayed");
            if(friend.reminderChats==0)return;
            check(friend.reminderChats==1 && owner.reminderChats==0 && denied.reminderChats==0,"single chat, owner/permission checks");
            gui.openMain(friend.player);reminderStageTick=ticks;phase=22;
        } else if(phase==22) {
            if(ticks-reminderStageTick<68)return;
            check(friend.reminderChats==1,"open home menu pauses reminders beyond interval");
            friend.close();phase=23;
        } else if(phase==23) {
            if(friend.reminderChats<2)return;
            check(friend.reminderChats==2,"one repeated chat after menu closes");
            getLogger().info("DREAM REMINDER CHAT/GUI PAUSE PASS");
            dream.getHomeManager().invite(owner.player,friend.player);
            dream.getHomeManager().accept(friend.player,dream.getHomeManager().inviteTokens(FRIEND).getFirst());phase=24;
        } else if(phase==24) {
            if(dream.getHomeManager().byPlayer(FRIEND)==null)return;
            reminderStageTick=ticks;phase=25;
        } else if(phase==25) {
            if(ticks-reminderStageTick<68)return;
            check(friend.reminderChats==2,"joining island stops reminders");
            dream.getHomeManager().leave(friend.player);phase=26;
        } else if(phase==26) {
            if(dream.getHomeManager().byPlayer(FRIEND)!=null)return;
            reminders.join(new PlayerJoinEvent(friend.player,Component.empty()));phase=27;
        } else if(phase==27) {
            if(friend.reminderChats<3)return;
            reminders.quit(new PlayerQuitEvent(friend.player,Component.empty(),PlayerQuitEvent.QuitReason.DISCONNECTED));reminderStageTick=ticks;phase=28;
        } else if(phase==28) {
            if(ticks-reminderStageTick<68)return;
            check(friend.reminderChats==3,"quit cancels further reminders");
            reminders.join(new PlayerJoinEvent(friend.player,Component.empty()));
            reminders.stop();reminderStageTick=ticks;phase=29;
        } else if(phase==29) {
            if(ticks-reminderStageTick<20)return;
            check(friend.reminderChats==3 && owner.reminderChats==0 && denied.reminderChats==0,"stop clears task and sessions");
            check(friend.actionBars==0,"no actionbar messages");
            getLogger().info("DREAM REMINDER ALL PASS delayed/repeated single chat + exact /home text/click + GUI pause/resume + member/permission guard + quit/stop cleanup + no actionbar");
            getServer().shutdown();phase=99;
        } else if(phase==30) {
            check(friend.top==original && friend.menu()==HomeMenu.INVITES,"page refresh keeps inventory and rejects queued old-page navigation");
            gui.open(owner.player,new MenuRequest(HomeMenu.PLAYERS,0,null,null));
            ((HomeMenuHolder)owner.top.getHolder()).getActions().put(10,"invite:"+FRIEND);
            original=owner.top;owner.click("invite:"+FRIEND);phase=31;
        } else if(phase==31) {
            check(owner.top==original && owner.menu()==HomeMenu.PLAYERS,"invite click and missing online target retain inventory");
            getLogger().info("DREAM GUI PARTIAL UPDATE ALL PASS setting single-slot commit + unchanged zero writes + page diff/clear/action remap + stale queued navigation + same inventory");
            getServer().shutdown();phase=99;
        }
    }
    private void command(Actor actor,String root,String... args) {
        check(getServer().getPluginCommand(root).execute(actor.player,root,args),"command execution "+root);
    }
    private void checkHelp(Actor actor) {
        check(actor.menu()==HomeMenu.HELP,"single help screen");
        check(actor.actions().stream().filter(action->!action.isEmpty()).count()==1 && actor.has("menu:main"),"only help back action");
        for(int slot:new int[]{11,13,15,31}) {
            var item=actor.top.getItem(slot);
            check(item!=null && item.getItemMeta().lore()!=null && item.getItemMeta().lore().size()>=10,"categorized help Lore slot="+slot);
        }
        verifyItems(actor.top);
    }
    private void checkPaging() {
        var data=new ArrayList<MenuEntry>();
        for(int i=0;i<30;i++)data.add(new MenuEntry(Map.of("inviter","Player"+i,"token","token"+i,"members","1","limit","4","seconds","300"),null));
        var renderer=new HomeMenuRenderer(new MessageService(dream.getConfigService().getMessages()));
        var yaml=dream.getConfigService().getMenus().get(HomeMenu.INVITES);
        var first=renderer.render(friend.player,null,new MenuRequest(HomeMenu.INVITES,0,null,null),yaml,Map.of(),data,Map.of());
        var last=renderer.render(friend.player,null,new MenuRequest(HomeMenu.INVITES,99,null,null),yaml,Map.of(),data,Map.of());
        check(((HomeMenuHolder)first.getHolder()).getActions().containsValue("next"),"pagination next");
        check(((HomeMenuHolder)last.getHolder()).getRequest().getPage()==1 && ((HomeMenuHolder)last.getHolder()).getActions().containsValue("previous") && !((HomeMenuHolder)last.getHolder()).getActions().containsValue("next"),"pagination clamps and disables last next");
        check(last.getItem(10)!=null && last.getItem(21)==null,"partial page keeps real entries and unused slots blank");
        var writes=new ArrayList<Integer>();
        Inventory tracked=(Inventory)Proxy.newProxyInstance(Inventory.class.getClassLoader(),new Class[]{Inventory.class},(p,m,a)->{
            if(m.getName().equals("setItem"))writes.add((int)a[0]);
            try{return m.invoke(first,a);}catch(java.lang.reflect.InvocationTargetException e){throw e.getCause();}
        });
        var before=first.getContents();
        renderer.refresh(tracked,new MenuRequest(HomeMenu.INVITES,99,null,null),yaml,Map.of(),data,Map.of());
        check(((HomeMenuHolder)first.getHolder()).getRequest().getPage()==1,"in-place page clamps");
        for(int slot=0;slot<first.getSize();slot++)check(writes.contains(slot)!=Objects.equals(before[slot],first.getItem(slot)),"writes exactly changed page slots "+slot);
        check(first.getItem(21)==null && !((HomeMenuHolder)first.getHolder()).getActions().containsKey(21),"refresh clears old page items and actions");
        check(((HomeMenuHolder)first.getHolder()).getActions().get(10).equals("accept:token21"),"page actions match new entries");
        writes.clear();
        renderer.refresh(tracked,new MenuRequest(HomeMenu.INVITES,1,null,null),yaml,Map.of(),data,Map.of());
        check(writes.isEmpty(),"unchanged refresh writes no slots");
        var sameAppearance=new ArrayList<>(data);
        sameAppearance.set(21,new MenuEntry(Map.of("inviter","Player21","token","replacement","members","1","limit","4","seconds","300"),null));
        renderer.refresh(tracked,new MenuRequest(HomeMenu.INVITES,1,null,null),yaml,Map.of(),sameAppearance,Map.of());
        check(writes.isEmpty() && ((HomeMenuHolder)first.getHolder()).getActions().get(10).equals("accept:replacement"),"identical item still updates hidden action");
        renderer.refresh(tracked,new MenuRequest(HomeMenu.INVITES,1,null,null),yaml,Map.of(),List.of(),Map.of());
        check(((HomeMenuHolder)first.getHolder()).getRequest().getPage()==0 && first.getItem(10)==null && !((HomeMenuHolder)first.getHolder()).getActions().containsKey(10),"shrinking list clamps and clears old entries");
        for(var menu:new HomeMenu[]{HomeMenu.INVITES,HomeMenu.PLAYERS,HomeMenu.MEMBERS}) {
            var config=dream.getConfigService().getMenus().get(menu);
            var empty=renderer.render(friend.player,null,new MenuRequest(menu,0,null,null),config,Map.of(),List.of(),Map.of());
            var shape=String.join("",config.getStringList("Shape"));
            var sections=config.getConfigurationSection("components");
            for(var id:sections.getKeys(false)) {
                var section=sections.getConfigurationSection(id);
                if(!section.contains("repeat"))continue;
                char symbol=section.getString("symbol").charAt(0);
                for(int slot=0;slot<shape.length();slot++)if(shape.charAt(slot)==symbol) {
                    check(empty.getItem(slot)==null,"empty list slot stays blank "+menu+":"+slot);
                    check(!((HomeMenuHolder)empty.getHolder()).getActions().containsKey(slot),"empty slot has no action");
                }
            }
            check(((HomeMenuHolder)empty.getHolder()).getActions().containsValue("menu:main") || ((HomeMenuHolder)empty.getHolder()).getActions().containsValue("menu:members"),"empty list keeps navigation");
        }
        getLogger().info("DREAM GUI EMPTY LIST PASS blank slots/no placeholder/no action + retained navigation + real entries/partial page");
    }
    private final class Actor {
        Player player; Inventory top,bottom; InventoryView view; int teleports,reminderChats,actionBars;boolean usePermission=true;
        Actor(UUID id,String name) {
            bottom=Bukkit.createInventory(null,27);
            var scheduler=(EntityScheduler)Proxy.newProxyInstance(EntityScheduler.class.getClassLoader(),new Class[]{EntityScheduler.class},(p,m,a)->defaults(m.getReturnType()));
            player=(Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class[]{Player.class},(p,m,a)->switch(m.getName()) {
                case "getUniqueId"->id;case "getName"->name;case "getServer"->getServer();case "isOnline","isValid"->true;
                case "hasPermission"->usePermission && "dreamskyblock.use".equals(a[0]);case "getScheduler"->scheduler;
                case "sendMessage"->{
                    for(var value:a)if(value instanceof Component component && plainText(component).contains("你还没有空岛")) {
                        check(plainText(component).equals("你还没有空岛，输入 /home 即可创建！"),"exact reminder text");
                        check(hasHomeClick(component),"clickable /home command");verifyItalic(component,TextDecoration.State.NOT_SET);reminderChats++;
                    }
                    yield null;
                }
                case "sendActionBar"->{actionBars++;yield null;}
                case "getWorld"->getServer().getWorlds().getFirst();
                case "getOpenInventory"->view;
                case "openInventory"->{top=(Inventory)a[0];yield view;}
                case "closeInventory"->{close();yield null;}
                case "teleportAsync"->{teleports++;yield CompletableFuture.completedFuture(true);}
                case "hashCode"->id.hashCode();case "equals"->p==a[0];case "toString"->name;
                default->defaults(m.getReturnType());
            });
            view=(InventoryView)Proxy.newProxyInstance(InventoryView.class.getClassLoader(),new Class[]{InventoryView.class},(p,m,a)->switch(m.getName()) {
                case "getTopInventory"->top;case "getBottomInventory"->bottom;case "getPlayer"->player;case "getType"->InventoryType.CHEST;
                case "getInventory"->((int)a[0]<top.getSize()?top:bottom);case "convertSlot"->(int)a[0]%top.getSize();
                case "getCursor"->new ItemStack(Material.AIR);case "getTitle","getOriginalTitle"->"Probe";default->defaults(m.getReturnType());
            });close();
        }
        void close(){top=Bukkit.createInventory(null,9);}
        HomeMenu menu(){return ((HomeMenuHolder)top.getHolder()).getRequest().getMenu();}
        Collection<String> actions(){return ((HomeMenuHolder)top.getHolder()).getActions().values();}
        boolean has(String action){return actions().contains(action);}
        void click(String action){
            var holder=(HomeMenuHolder)top.getHolder();
            int slot=holder.getActions().entrySet().stream().filter(e->e.getValue().equals(action)).findFirst().orElseThrow(()->new AssertionError("missing action "+action)).getKey();
            verifyItems(top);
            var event=new InventoryClickEvent(view,InventoryType.SlotType.CONTAINER,slot,ClickType.LEFT,InventoryAction.PICKUP_ALL);
            gui.click(event);check(event.isCancelled(),"click cancellation");
        }
    }
    private void verifyItems(Inventory inventory){
        for(var item:inventory.getContents())if(item!=null){var meta=item.getItemMeta();if(meta.itemName()!=null)verifyItalic(meta.itemName(),TextDecoration.State.NOT_SET);if(meta.lore()!=null)for(var lore:meta.lore())verifyItalic(lore,TextDecoration.State.NOT_SET);}
    }
    private void verifyItalic(Component node,TextDecoration.State inherited){var value=node.decoration(TextDecoration.ITALIC);var effective=value==TextDecoration.State.NOT_SET?inherited:value;check(effective==TextDecoration.State.FALSE,"item italic false");for(var child:node.children())verifyItalic(child,effective);}
    private static String plainText(Component component){var text=new StringBuilder(component instanceof net.kyori.adventure.text.TextComponent t?t.content():"");for(var child:component.children())text.append(plainText(child));return text.toString();}
    private static boolean hasHomeClick(Component component){return net.kyori.adventure.text.event.ClickEvent.runCommand("/home").equals(component.clickEvent()) || component.children().stream().anyMatch(DreamGuiProbe::hasHomeClick);}
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static Object defaults(Class<?> t){if(!t.isPrimitive())return null;if(t==boolean.class)return false;if(t==int.class)return 0;if(t==long.class)return 0L;if(t==double.class)return 0d;if(t==float.class)return 0f;if(t==byte.class)return (byte)0;if(t==short.class)return (short)0;return null;}
}
