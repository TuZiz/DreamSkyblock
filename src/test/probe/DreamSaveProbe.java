import org.bukkit.*;
import org.bukkit.block.Chest;
import org.bukkit.entity.ArmorStand;
import org.bukkit.event.*;
import org.bukkit.event.world.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import ym.dreamSkyblock.DreamSkyblock;
import ym.dreamSkyblock.model.Home;
import ym.dreamSkyblock.world.HomeWorldManager;
import kotlin.Unit;
import java.util.*;
import java.util.concurrent.*;

/** Real native save/unload, dirty chunks/entities, SQLite and backup/restore; no client required. */
public final class DreamSaveProbe extends JavaPlugin implements Listener {
    private DreamSkyblock dream;
    private Home home;
    private World world;
    private UUID worldId;
    private int phase,ticks,saves,base,unloads,until;
    private boolean cancel,actionRan;
    private volatile boolean loaded;
    private volatile Throwable failure;
    private CompletableFuture<?> operation;
    private String backup;
    private ExecutorService probeIo;
    private final NamespacedKey marker=new NamespacedKey("dreamsaveprobe","marker");
    private static final String VALUE="saved-island";
    @Override public void onEnable() {
        getServer().getPluginManager().registerEvents(this,this);
        getServer().getGlobalRegionScheduler().runAtFixedRate(this, task -> {
            try { step(); }
            catch(Throwable error) { getLogger().log(java.util.logging.Level.SEVERE,"DREAM SAVE FAILED phase="+phase,error);task.cancel();getServer().shutdown(); }
        },20L,2L);
    }
    @EventHandler public void save(WorldSaveEvent event) { if(event.getWorld().getUID().equals(worldId)){check(Bukkit.isPrimaryThread(),"save main thread");saves++;} }
    @EventHandler(priority=EventPriority.HIGHEST) public void unload(WorldUnloadEvent event) {
        if(event.getWorld().getUID().equals(worldId)){unloads++;if(cancel)event.setCancelled(true);}
    }
    private void step() throws Exception {
        check(++ticks<1200,"timeout phase="+phase);
        if(failure!=null)throw new AssertionError("load callback failed",failure);
        if(phase==0) {
            dream=(DreamSkyblock)getServer().getPluginManager().getPlugin("DreamSkyblock");
            if(dream==null || !dream.getHomeManager().getReady())return;
            home=dream.getHomeManager().all().stream().filter(h->h.getStatus().equals("ACTIVE") && !h.getOwners().isEmpty()).findFirst().orElseThrow();
            load();phase=1;
        } else if(phase==1) {
            if(!loaded)return;
            world=dream.getWorldManager().getLoaded(home);worldId=world.getUID();
            world.getBlockAt(3,100,3).setType(Material.EMERALD_BLOCK);
            world.getBlockAt(4,100,3).setType(Material.CHEST);
            ((Chest)world.getBlockAt(4,100,3).getState()).getInventory().setItem(0,new ItemStack(Material.DIAMOND,7));
            var stand=world.spawn(new Location(world,5.5,101,3.5),ArmorStand.class);
            stand.setGravity(false);stand.setPersistent(true);stand.getPersistentDataContainer().set(marker,PersistentDataType.STRING,VALUE);
            world.getPersistentDataContainer().set(marker,PersistentDataType.STRING,VALUE);
            cancel=true;base=saves;actionRan=false;
            operation=dream.getWorldManager().maintain(home,false,()->{actionRan=true;return CompletableFuture.completedFuture(Unit.INSTANCE);});phase=2;
        } else if(phase==2) {
            if(!operation.isDone())return;
            check(operation.isCompletedExceptionally(),"cancelled unload rejects maintenance");
            check(!actionRan && saves==base,"cancelled unload does not save or enter file transaction");
            check(dream.getWorldManager().getLoaded(home)==world && getServer().getWorld(worldId)==world,"cancelled world remains tracked");
            cancel=false;base=saves;
            operation=dream.getWorldManager().maintain(home,false,()->{
                check(getServer().getWorld(worldId)==null && dream.getWorldManager().getLoaded(home)==null,"file work only after native unload");
                actionRan=true;return CompletableFuture.completedFuture(Unit.INSTANCE);
            });phase=3;
        } else if(phase==3) {
            if(!operation.isDone())return;operation.getNow(null);
            check(actionRan && saves==base+1,"maintenance/native unload has exactly one world save");
            load();phase=4;
        } else if(phase==4) {
            if(!loaded)return;verify();
            // Disabled periodic autosave must not disable explicit save=true unloading.
            world.setAutoSave(false);world.getBlockAt(3,100,3).setType(Material.DIAMOND_BLOCK);base=saves;
            // The visit callback queues the default idle timer after publishing loaded=true.
            // Let that queued task install its timer before replacing it with the test's short timer.
            until=ticks+3;phase=40;
        } else if(phase==40) {
            if(ticks<until)return;
            dream.getWorldManager().cancelUnload(home);dream.getWorldManager().scheduleUnload(home,1);phase=5;
        } else if(phase==5) {
            if(dream.getWorldManager().getLoaded(home)!=null)return;
            check(saves==base+1,"delayed empty unload saves once even with periodic autosave disabled");
            load();phase=6;
        } else if(phase==6) {
            if(!loaded)return;
            world=dream.getWorldManager().getLoaded(home);
            check(world.getBlockAt(3,100,3).getType()==Material.DIAMOND_BLOCK,"dirty block saved with autosave=false");
            world.getBlockAt(3,100,3).setType(Material.EMERALD_BLOCK);base=saves;
            operation=dream.getHomeManager().backup(home.getId());phase=7;
        } else if(phase==7) {
            if(!operation.isDone())return;backup=(String)operation.getNow(null);
            check(saves==base+1 && dream.getWorldManager().getLoaded(home)==null,"backup performs one save then snapshots offline world");
            load();phase=8;
        } else if(phase==8) {
            if(!loaded)return;verify();
            world.getBlockAt(3,100,3).setType(Material.GOLD_BLOCK);
            ((Chest)world.getBlockAt(4,100,3).getState()).getInventory().setItem(0,new ItemStack(Material.DIAMOND,2));
            base=saves;operation=dream.getHomeManager().restore(home.getId(),backup);phase=9;
        } else if(phase==9) {
            if(!operation.isDone())return;home=(Home)operation.getNow(null);
            check(saves==base+1,"restore safely unloads dirty current world once");
            check(dream.getHomeManager().byPlayer(home.getOwners().iterator().next()).getId().equals(home.getId()),"SQLite ownership remains linked");
            load();phase=10;
        } else if(phase==10) {
            if(!loaded)return;verify();
            // Dedicated manager instances exercise stop without stopping the live plugin mid-check.
            probeIo=Executors.newSingleThreadExecutor();
            var fallback=new HomeWorldManager(dream,dream.getConfigService(),dream.getWorldManager().getFiles(),probeIo);
            fallback.adopt(List.of(home));cancel=true;base=saves;
            fallback.shutdown();
            check(getServer().getWorld(worldId)==world && saves==base+1,"stop cancelled unload falls back to one explicit save");
            var success=new HomeWorldManager(dream,dream.getConfigService(),dream.getWorldManager().getFiles(),probeIo);
            success.adopt(List.of(home));cancel=false;base=saves;
            success.shutdown();
            check(getServer().getWorld(worldId)==null && saves==base+1,"empty stop uses exactly one native save/unload");
            probeIo.shutdown();probeIo=null;
            load();phase=11;
        } else if(phase==11) {
            if(!loaded)return;verify();
            getLogger().info("DREAM SAVE ALL PASS: exactly one save per native maintenance/delayed/backup/restore/empty-stop; cancelled unload retains world and blocks disk work; stop fallback saves once; dirty blocks/chest items/ArmorStand/entity+worldPDC persist; autosave=false still persists; SQLite ownership intact");
            until=ticks+3;phase=12;
        } else if(phase==12 && ticks>=until) { getServer().shutdown();phase=99; }
    }
    private void load() {
        loaded=false;dream.getWorldManager().load(home,dream.getConfigService().getTemplates().get(home.getTemplateId()),null,ok->{
            if(!ok)failure=new AssertionError("native load failed");loaded=true;return Unit.INSTANCE;
        });
    }
    private void verify() {
        world=dream.getWorldManager().getLoaded(home);worldId=world.getUID();
        check(world.getBlockAt(3,100,3).getType()==Material.EMERALD_BLOCK,"block retained after reload/restore");
        var item=((Chest)world.getBlockAt(4,100,3).getState()).getInventory().getItem(0);
        check(item!=null && item.getType()==Material.DIAMOND && item.getAmount()==7,"container contents retained");
        check(VALUE.equals(world.getPersistentDataContainer().get(marker,PersistentDataType.STRING)),"world PDC retained");
        check(world.getEntitiesByClass(ArmorStand.class).stream().anyMatch(s->VALUE.equals(s.getPersistentDataContainer().get(marker,PersistentDataType.STRING))),"entity+PDC retained");
    }
    @Override public void onDisable(){if(probeIo!=null)probeIo.shutdown();}
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
