import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;
import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import ym.dreamSkyblock.DreamSkyblock;
import ym.dreamSkyblock.border.HomeBorderDisplay;
import ym.dreamSkyblock.model.Home;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Synthetic player transport; real Leaf worlds, native borders, scheduler, creation and DB. */
public final class DreamBorderProbe extends JavaPlugin {
    private DreamSkyblock dream;
    private HomeBorderDisplay display;
    private Actor owner, visitor;
    private Home home;
    private World island;
    private int phase, ticks;
    private static final UUID OWNER=UUID.fromString("00000000-0000-0000-0000-000000000088");
    @Override public void onEnable() {
        getServer().getGlobalRegionScheduler().runAtFixedRate(this,t->{
            try { step(); } catch(Throwable error) { getLogger().log(java.util.logging.Level.SEVERE,"DREAM BORDER FAILED phase="+phase,error);t.cancel();getServer().shutdown(); }
        },20L,2L);
    }
    private void step() throws Exception {
        check(++ticks<500,"timeout phase="+phase);
        if(phase==-1) {if(dream.getHomeManager().byPlayer(OWNER)==null)phase=0;return;}
        if(phase==0) {
            dream=(DreamSkyblock)getServer().getPluginManager().getPlugin("DreamSkyblock");
            if(dream==null || !dream.getHomeManager().getReady())return;
            owner=new Actor(OWNER);visitor=new Actor(UUID.randomUUID());
            if(dream.getHomeManager().byPlayer(OWNER)!=null){dream.getHomeManager().delete(owner.player);phase=-1;return;}
            check(dream.getLevelService().borderSizeFor(1)==48.0,"configured new default 48");
            dream.getHomeManager().create(owner.player,"skyblock");phase=1;
        } else if(phase==1) {
            home=dream.getHomeManager().byPlayer(OWNER);
            if(home==null || !home.getStatus().equals("ACTIVE"))return;
            island=dream.getWorldManager().getLoaded(home);
            if(island==null)return;
            check(home.getBorderSize()==48.0 && island.getWorldBorder().getSize()==48.0,"real new world keeps 48 native boundary");
            owner.world=island;owner.location=new Location(island,0,101,0);
            display=new HomeBorderDisplay(dream,dream.getHomeManager(),dream.getWorldManager());display.start();display.start();
            display.update(owner.player);
            check(owner.border!=null && owner.border.getWorld()==null && owner.border.getSize()>1000000,"client-only far border hides native wall");
            check(owner.particles==0,"no particles in center");
            int updates=owner.borderUpdates;display.update(owner.player);
            check(updates==owner.borderUpdates,"no repeated border packets when unchanged");
            owner.border=null;display.update(owner.player);
            check(owner.border!=null && owner.border.getSize()>1000000,"cleared override is reapplied after respawn");
            owner.location.setX(22);display.update(owner.player);
            check(owner.particles==27 && owner.points.stream().allMatch(p->p[0]==24.0),"localized dust on actual east edge");
            check(island.getWorldBorder().isInside(new Location(island,23,101,0)) && !island.getWorldBorder().isInside(new Location(island,25,101,0)),"native bounds unaffected by visual override");
            visitor.world=island;visitor.location=new Location(island,0,101,-22);visitor.permission=false;
            var previous=getServer().createWorldBorder();previous.setSize(200);visitor.border=previous;
            display.join(new PlayerJoinEvent(visitor.player,net.kyori.adventure.text.Component.empty()));
            check(visitor.border!=previous && visitor.particles==27,"non-member visitor without use permission also sees particles");
            visitor.world=getServer().getWorlds().getFirst();visitor.location=new Location(visitor.world,0,100,0);
            visitor.border=null; // The server may reset the override while switching worlds.
            display.changed(new PlayerChangedWorldEvent(visitor.player,island));
            check(visitor.border==previous,"leaving restores previous personal border");
            int particles=visitor.particles;display.update(visitor.player);check(visitor.particles==particles,"no particles outside home worlds");
            home.setLevel(2);home.setBorderSize(dream.getLevelService().borderSizeFor(2));dream.getWorldManager().applyFlags(home);phase=2;
        } else if(phase==2) {
            if(island.getWorldBorder().getSize()!=64.0)return;
            owner.particles=0;owner.points.clear();display.update(owner.player);
            check(owner.particles==0,"old 24 edge no longer highlighted after expansion");
            owner.location.setX(30);display.update(owner.player);
            check(owner.particles==27 && owner.points.stream().allMatch(p->p[0]==32.0),"level2 outline follows 64 block native edge");
            check(island.getWorldBorder().getSize()==64.0 && owner.border.getSize()>1000000,"server upgrade and client hidden border independent");
            var external=getServer().createWorldBorder();owner.border=external;
            owner.world=getServer().getWorlds().getFirst();owner.location=new Location(owner.world,0,100,0);
            display.changed(new PlayerChangedWorldEvent(owner.player,island));check(owner.border==external,"do not overwrite a newer override owned elsewhere");
            owner.border=null;owner.world=island;owner.location=new Location(island,0,101,0);
            display.changed(new PlayerChangedWorldEvent(owner.player,getServer().getWorlds().getFirst()));
            check(owner.border!=null,"reenter reapplies hidden border");
            display.quit(new PlayerQuitEvent(owner.player,net.kyori.adventure.text.Component.empty(),PlayerQuitEvent.QuitReason.DISCONNECTED));
            var field=HomeBorderDisplay.class.getDeclaredField("views");field.setAccessible(true);
            check(((Map<?,?>)field.get(display)).isEmpty(),"quit removes sessions");
            display.stop();display.stop();
            var task=HomeBorderDisplay.class.getDeclaredField("task");task.setAccessible(true);check(task.get(display)==null,"stop releases owned task");
            getLogger().info("DREAM BORDER ALL PASS default48 + native bounds + client-only hidden border + near-only particles + visitor + expansion + enter/leave/prior-border restore + override ownership + quit/stop");
            getServer().shutdown();phase=99;
        }
    }
    private final class Actor {
        Player player;World world=getServer().getWorlds().getFirst();Location location=new Location(world,0,101,0);
        WorldBorder border;int particles,borderUpdates;boolean permission=true;List<double[]> points=new ArrayList<>();
        Actor(UUID id) {
            var scheduler=(EntityScheduler)Proxy.newProxyInstance(EntityScheduler.class.getClassLoader(),new Class[]{EntityScheduler.class},(p,m,a)->defaults(m.getReturnType()));
            player=(Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class[]{Player.class},(p,m,a)->switch(m.getName()) {
                case "getUniqueId"->id;case "getName"->"BorderProbe";case "getServer"->getServer();case "isOnline","isValid"->true;
                case "hasPermission"->permission && "dreamskyblock.use".equals(a[0]);case "getScheduler"->scheduler;
                case "getWorld"->world;case "getLocation"->location.clone();case "getWorldBorder"->border;
                case "setWorldBorder"->{border=(WorldBorder)a[0];borderUpdates++;yield null;}
                case "spawnParticle"->{check(a[0]==Particle.DUST,"dust particle type");particles++;points.add(new double[]{(double)a[1],(double)a[2],(double)a[3]});yield null;}
                case "teleportAsync"->{location=((Location)a[0]).clone();world=location.getWorld();yield CompletableFuture.completedFuture(true);}
                case "hashCode"->id.hashCode();case "equals"->p==a[0];case "toString"->"BorderProbe";
                default->defaults(m.getReturnType());
            });
        }
    }
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private static Object defaults(Class<?> t){if(!t.isPrimitive())return null;if(t==boolean.class)return false;if(t==int.class)return 0;if(t==long.class)return 0L;if(t==double.class)return 0d;if(t==float.class)return 0f;if(t==byte.class)return (byte)0;if(t==short.class)return (short)0;return null;}
}
