import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.*;
import org.bukkit.plugin.java.JavaPlugin;
import ym.dreamSkyblock.DreamSkyblock;
import ym.dreamSkyblock.login.HomeLoginReturn;
import ym.dreamSkyblock.model.Home;
import net.kyori.adventure.text.Component;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Real Leaf scheduler, native worlds, SQLite and Craft PDC; synthetic Player/events only. */
public final class DreamLoginProbe extends JavaPlugin {
    private DreamSkyblock dream;
    private HomeLoginReturn login;
    private Home home;
    private Actor actor, old;
    private int phase, ticks, until;
    private byte[] saved;
    private static final NamespacedKey KEY = new NamespacedKey("dreamskyblock", "login_return_home");
    private static final Path STATE = Path.of("../../target/verification/diagnostics/login-return-state.txt");
    @Override public void onEnable() {
        getServer().getGlobalRegionScheduler().runAtFixedRate(this, task -> {
            try { step(); } catch(Throwable error) {
                getLogger().log(java.util.logging.Level.SEVERE, "DREAM LOGIN FAILED phase="+phase, error);
                task.cancel(); getServer().shutdown();
            }
        }, 20L, 2L);
    }
    private void step() throws Exception {
        check(Bukkit.isPrimaryThread(), "all lifecycle/PDC work on main thread");
        check(++ticks < 900, "timeout phase="+phase);
        if(phase==0) {
            dream=(DreamSkyblock)getServer().getPluginManager().getPlugin("DreamSkyblock");
            if(dream==null || !dream.getHomeManager().getReady())return;
            login=new HomeLoginReturn(dream,dream.getHomeManager(),dream.getWorldManager());login.start();
            if(Boolean.getBoolean("dream.login.restart")) {
                var lines=Files.readAllLines(STATE);
                home=dream.getHomeManager().byId(UUID.fromString(lines.get(0)));
                check(home!=null && home.isOwner(UUID.fromString(lines.get(1))), "SQLite ownership survived restart");
                actor=new Actor(UUID.fromString(lines.get(1)));
                actor.pdc.readFromBytes(Base64.getDecoder().decode(lines.get(2)),true);
                check(dream.getWorldManager().getLoaded(home)==null,"native island initially unloaded after restart");
                join(actor);phase=90;return;
            }
            actor=new Actor(UUID.randomUUID());
            dream.getHomeManager().create(actor.player,"skyblock");phase=1;
        } else if(phase==1) {
            home=dream.getHomeManager().byPlayer(actor.id);
            if(home==null || !home.getStatus().equals("ACTIVE"))return;
            check(actor.world==dream.getWorldManager().getLoaded(home),"creation reached real island");
            quit(actor);
            check(marker(actor),"own island quit records home UUID");
            saved=actor.pdc.serializeToBytes();
            Files.createDirectories(STATE.getParent());
            Files.write(STATE,List.of(home.getId().toString(),actor.id.toString(),Base64.getEncoder().encodeToString(saved)));
            dream.getWorldManager().scheduleUnload(home,1);phase=2;
        } else if(phase==2) {
            if(dream.getWorldManager().getLoaded(home)!=null)return;
            actor=new Actor(actor.id);actor.pdc.readFromBytes(saved,true);join(actor);phase=3;
        } else if(phase==3) {
            if(actor.teleports==0)return;
            check(actor.world==dream.getWorldManager().getLoaded(home) && actor.teleports==1,"unloaded island loaded once and reached");
            check(actor.destination.getX()==home.getSpawn().getX() && actor.destination.getY()==home.getSpawn().getY(),"uses configured home teleport point");
            pulse();pulse();check(actor.teleports==1,"no repeated automatic teleport");
            quit(actor);actor.world=main();actor.online=true;join(actor);pulse();
            phase=4;
        } else if(phase==4) {
            if(actor.teleports<2)return;
            // Start a new login while the native world is already loaded: keep original position.
            int count=actor.teleports;join(actor);pulse();check(actor.teleports==count,"already on home keeps position");
            actor.world=main();quit(actor);check(!marker(actor),"main world quit clears return marker");
            actor.online=true;join(actor);pulse();check(actor.teleports==count,"no marker does not teleport");
            actor.pdc.set(KEY,PersistentDataType.STRING,home.getId().toString());
            old=actor;join(old);pulse();quit(old);
            actor=new Actor(old.id);actor.pdc.readFromBytes(saved,true);join(actor);pulse();
            until=ticks+15;phase=5;
        } else if(phase==5) {
            if(ticks<until)return;
            check(old.teleports==2 && actor.teleports==1,"rapid quit/rejoin cancels old queued return");
            check(marker(old),"quit during pending load retains marker");
            actor.world=main();join(actor);pulse();
            login.teleport(new PlayerTeleportEvent(actor.player,new Location(main(),0,80,0),new Location(main(),20,80,20)));
            until=ticks+15;phase=6;
        } else if(phase==6) {
            if(ticks<until)return;
            check(actor.teleports==1 && !marker(actor),"manual teleport cancels late return");
            actor.pdc.set(KEY,PersistentDataType.STRING,home.getId().toString());
            join(actor);pulse();actor.permission=false;until=ticks+15;phase=7;
        } else if(phase==7) {
            if(ticks<until)return;
            check(actor.teleports==1,"permission revoked before load completion prevents teleport");
            actor.permission=true;actor.pdc.set(KEY,PersistentDataType.STRING,home.getId().toString());
            home.setStatus("RECOVERY");join(actor);pulse();check(actor.teleports==1,"recovery home not entered");
            home.setStatus("ACTIVE");
            var ready=dream.getHomeManager().getClass().getDeclaredField("ready");ready.setAccessible(true);ready.setBoolean(dream.getHomeManager(),false);
            pulse();check(actor.teleports==1,"waits for home initialization");
            ready.setBoolean(dream.getHomeManager(),true);pulse();until=ticks+15;phase=8;
        } else if(phase==8) {
            if(ticks<until)return;
            check(actor.teleports==2,"returns once service ready and maintenance complete");
            actor.world=main();join(actor);home.getOwners().remove(actor.id);pulse();
            check(!marker(actor),"removed member cannot use stale marker");home.getOwners().add(actor.id);
            actor.pdc.set(KEY,PersistentDataType.STRING,"broken-uuid");join(actor);check(!actor.pdc.has(KEY),"malformed marker cleared safely");
            actor.pdc.set(KEY,PersistentDataType.STRING,UUID.randomUUID().toString());join(actor);pulse();
            check(!marker(actor),"different or deleted home ID cleared");
            actor.pdc.set(KEY,PersistentDataType.STRING,home.getId().toString());join(actor);pulse();login.stop();
            until=ticks+15;phase=9;
        } else if(phase==9) {
            if(ticks<until)return;
            check(actor.teleports==2,"stopped listener rejects queued return");
            check(marker(actor),"stop preserves pending return marker");
            getLogger().info("DREAM LOGIN ALL PASS: native unload/load, real Craft PDC byte roundtrip, spawn, no-marker, already-home, rapid reconnect, manual cancellation, permissions, initialization, recovery, stale ownership, stop");
            getServer().shutdown();phase=99;
        } else if(phase==90) {
            if(actor.teleports==0)return;
            check(actor.teleports==1 && actor.world==dream.getWorldManager().getLoaded(home),"restart marker returns to persisted native island");
            getLogger().info("DREAM LOGIN RESTART PASS: persisted SQLite ownership + native island + serialized Craft PDC return marker");
            getServer().shutdown();phase=99;
        }
    }
    private void pulse() throws Exception { var method=HomeLoginReturn.class.getDeclaredMethod("pulse");method.setAccessible(true);method.invoke(login); }
    private void join(Actor actor) { actor.online=true;login.join(new PlayerJoinEvent(actor.player,Component.empty())); }
    private void quit(Actor actor) { login.quit(new PlayerQuitEvent(actor.player,Component.empty()));actor.online=false; }
    private boolean marker(Actor actor) { return home.getId().toString().equals(actor.pdc.get(KEY,PersistentDataType.STRING)); }
    private World main() { return getServer().getWorlds().getFirst(); }
    private final class Actor {
        final UUID id;final Player player;final PersistentDataContainer pdc;
        World world=main();boolean online=true,permission=true;int teleports;Location destination;
        Actor(UUID id) {
            this.id=id;pdc=new ItemStack(Material.STONE).getItemMeta().getPersistentDataContainer();
            player=(Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class[]{Player.class},(p,m,a)->switch(m.getName()) {
                case "getUniqueId"->id;case "getName"->"LoginProbe";case "getServer"->getServer();
                case "isOnline","isValid"->online;case "hasPermission"->permission;
                case "getWorld"->world;case "getPersistentDataContainer"->pdc;
                case "teleportAsync"->{
                    check(Bukkit.isPrimaryThread(),"teleport applied on main thread");
                    check(online,"disconnected Player object must never reach teleportAsync");
                    var to=(Location)a[0];var event=new PlayerTeleportEvent((Player)p,new Location(world,0,80,0),to);
                    login.teleport(event);
                    if(event.isCancelled())yield CompletableFuture.completedFuture(false);
                    var from=world;world=to.getWorld();destination=to;teleports++;
                    if(world!=from)login.changed(new PlayerChangedWorldEvent((Player)p,from));
                    yield CompletableFuture.completedFuture(true);
                }
                case "hashCode"->System.identityHashCode(p);case "equals"->p==a[0];case "toString"->"LoginProbe:"+id;
                default->defaults(m.getReturnType());
            });
        }
    }
    private static Object defaults(Class<?> type) {
        if(!type.isPrimitive())return null;if(type==boolean.class)return false;if(type==int.class)return 0;
        if(type==long.class)return 0L;if(type==double.class)return 0d;if(type==float.class)return 0f;
        if(type==byte.class)return (byte)0;if(type==short.class)return (short)0;return null;
    }
    private static void check(boolean value,String message) { if(!value)throw new AssertionError(message); }
}
