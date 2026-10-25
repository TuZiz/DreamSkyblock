import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.plugin.java.JavaPlugin;
import io.papermc.paper.connection.PlayerConfigurationConnection;
import io.papermc.paper.event.player.AsyncPlayerSpawnLocationEvent;
import com.destroystokyo.paper.event.player.PlayerPostRespawnEvent;
import net.kyori.adventure.text.Component;
import ym.dreamSkyblock.DreamSkyblock;
import ym.dreamSkyblock.login.*;
import ym.dreamSkyblock.model.Home;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;

/** Real Leaf worlds, IO and scheduler; synthetic player and configuration connection, no packet/client test. */
public final class DreamHomeSpawnProbe extends JavaPlugin {
    private DreamSkyblock dream; private Home home; private Actor actor;
    private HomeInitialSpawn initial; private HomeRespawn respawn;
    private CompletableFuture<Location> login;
    private int phase, ticks, until, count; private boolean connected=true;
    public void onEnable() {
        getServer().getGlobalRegionScheduler().runAtFixedRate(this, task -> {
            try { step(); } catch(Throwable error) {
                getLogger().log(java.util.logging.Level.SEVERE,"DREAM HOME SPAWN FAILED phase="+phase,error);
                task.cancel();getServer().shutdown();
            }
        },20L,2L);
    }
    private Object field(String name)throws Exception {var f=DreamSkyblock.class.getDeclaredField(name);f.setAccessible(true);return f.get(dream);}
    private void step() throws Exception {
        check(++ticks<1200,"timeout phase="+phase);
        if(phase==0) {
            dream=(DreamSkyblock)getServer().getPluginManager().getPlugin("DreamSkyblock");
            if(dream==null||!dream.getHomeManager().getReady())return;
            initial=(HomeInitialSpawn)field("initialSpawn");respawn=(HomeRespawn)field("homeRespawn");
            actor=new Actor(UUID.randomUUID());dream.getHomeManager().create(actor.player,"skyblock");phase=1;
        } else if(phase==1) {
            home=dream.getHomeManager().byPlayer(actor.id);if(home==null||!home.getStatus().equals("ACTIVE"))return;
            count=actor.teleports;actor.world=main();until=ticks+3;phase=10;
        } else if(phase==10) {
            if(ticks<until)return;unloadSoon();phase=2;
        } else if(phase==2) {
            if(loaded()!=null)return;
            var profile=Bukkit.createProfile(actor.id,"HomeSpawnProbe");
            var connection=(PlayerConfigurationConnection)Proxy.newProxyInstance(PlayerConfigurationConnection.class.getClassLoader(),new Class[]{PlayerConfigurationConnection.class},(p,m,a)->switch(m.getName()) {
                case "getProfile"->profile;case "isConnected"->connected;
                case "hashCode"->System.identityHashCode(p);case "equals"->p==a[0];default->defaults(m.getReturnType());
            });
            login=new CompletableFuture<>();var fallback=new Location(main(),0,200,0);
            getServer().getAsyncScheduler().runNow(this, task->{
                try {check(!Bukkit.isPrimaryThread(),"configuration wait must be async");
                    var event=new AsyncPlayerSpawnLocationEvent(connection,fallback,false);
                    initial.spawn(event);login.complete(event.getSpawnLocation());
                }catch(Throwable t){login.completeExceptionally(t);}
            });phase=3;
        } else if(phase==3) {
            if(!login.isDone())return;
            Location destination=login.getNow(null);check(destination.getWorld()==loaded(),"initial spawn directly selects native island");
            check(destination.getY()==home.getSpawn().getY(),"initial spawn uses configured home point");
            check(actor.teleports==count,"no post-join teleport needed for initial placement");
            unloadSoon();until=ticks+18;phase=4;
        } else if(phase==4) {
            if(ticks<until)return;check(loaded()!=null,"configuration reservation prevents empty-world unload");
            initial.joined(new PlayerJoinEvent(actor.player,Component.empty()));
            until=ticks+3;phase=11;
        } else if(phase==11) {
            if(ticks<until)return;unloadSoon();phase=5;
        } else if(phase==5) {
            if(loaded()!=null)return;
            var event=new PlayerRespawnEvent(actor.player,new Location(main(),0,80,0),false,false,false,PlayerRespawnEvent.RespawnReason.DEATH);
            respawn.respawn(event);respawn.placed(new PlayerPostRespawnEvent(actor.player,event.getRespawnLocation(),false,false,false,PlayerRespawnEvent.RespawnReason.DEATH));phase=6;
        } else if(phase==6) {
            if(actor.teleports==count)return;
            check(actor.teleports==count+1 && actor.world==loaded(),"immediate respawn loads island and returns once");
            var event=new PlayerRespawnEvent(actor.player,new Location(main(),0,80,0),false,false,false,PlayerRespawnEvent.RespawnReason.DEATH);
            respawn.respawn(event);check(event.getRespawnLocation().getWorld()==loaded(),"loaded-home default respawn is direct");
            var bed=new Location(main(),12,84,16);var bedEvent=new PlayerRespawnEvent(actor.player,bed,true,false,false,PlayerRespawnEvent.RespawnReason.DEATH);
            respawn.respawn(bedEvent);check(bedEvent.getRespawnLocation().equals(bed),"explicit bed respawn remains intact");
            actor.world=main();until=ticks+3;phase=12;
        } else if(phase==12) {
            if(ticks<until)return;unloadSoon();phase=7;
        } else if(phase==7) {
            if(loaded()!=null)return;
            var event=new PlayerRespawnEvent(actor.player,new Location(main(),0,80,0),false,false,false,PlayerRespawnEvent.RespawnReason.DEATH);
            respawn.respawn(event);respawn.placed(new PlayerPostRespawnEvent(actor.player,event.getRespawnLocation(),false,false,false,PlayerRespawnEvent.RespawnReason.DEATH));
            respawn.quit(new PlayerQuitEvent(actor.player,Component.empty()));actor.online=false;until=ticks+20;phase=8;
        } else if(phase==8) {
            if(ticks<until)return;check(actor.teleports==count+1,"disconnected respawn session never teleports late");
            getLogger().info("DREAM HOME SPAWN ALL PASS; synthetic connection/player, real Leaf worlds and schedulers");getServer().shutdown();phase=99;
        }
    }
    private World loaded(){return dream.getWorldManager().getLoaded(home);}
    private void unloadSoon(){dream.getWorldManager().cancelUnload(home);dream.getWorldManager().scheduleUnload(home,1);}
    private World main(){return getServer().getWorld("world");}
    private final class Actor {
        final UUID id;final Player player;final PersistentDataContainer pdc;
        World world=main();boolean online=true;int teleports;
        Actor(UUID id) {
            this.id=id;pdc=new ItemStack(Material.STONE).getItemMeta().getPersistentDataContainer();
            player=(Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class[]{Player.class},(p,m,a)->switch(m.getName()) {
                case "getUniqueId"->id;case "getName"->"HomeSpawnProbe";case "getServer"->getServer();
                case "isOnline","isValid","hasPermission"->online;case "getWorld"->world;
                case "getPersistentDataContainer"->pdc;
                case "teleportAsync"->{check(Bukkit.isPrimaryThread()&&online,"live main-thread teleport");var to=(Location)a[0];
                    var event=new PlayerTeleportEvent((Player)p,new Location(world,0,80,0),to);if(respawn!=null)respawn.teleport(event);
                    if(event.isCancelled())yield CompletableFuture.completedFuture(false);
                    world=to.getWorld();teleports++;yield CompletableFuture.completedFuture(true);}
                case "hashCode"->System.identityHashCode(p);case "equals"->p==a[0];case "toString"->"HomeSpawnProbe:"+id;
                default->defaults(m.getReturnType());
            });
        }
    }
    private static Object defaults(Class<?> t){if(!t.isPrimitive())return null;if(t==boolean.class)return false;if(t==int.class)return 0;if(t==long.class)return 0L;if(t==double.class)return 0d;if(t==float.class)return 0f;if(t==byte.class)return (byte)0;if(t==short.class)return (short)0;return null;}
    private static void check(boolean value,String text){if(!value)throw new AssertionError(text);}
}
