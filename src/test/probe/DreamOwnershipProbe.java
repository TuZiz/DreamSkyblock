import org.bukkit.*;
import org.bukkit.block.Chest;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import ym.dreamSkyblock.DreamSkyblock;
import ym.dreamSkyblock.model.Home;
import kotlin.Unit;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Two real Leaf starts, with a disposable DB-row loss between them; no production data. */
public final class DreamOwnershipProbe extends JavaPlugin {
    private DreamSkyblock dream;
    private Home home;
    private int phase,ticks;
    private boolean loaded;
    private CompletableFuture<String> backup;
    private final NamespacedKey marker=new NamespacedKey("dreamownershipprobe","marker");
    @Override public void onEnable() {
        getServer().getGlobalRegionScheduler().runAtFixedRate(this,task->{
            try { step(); }
            catch(Throwable error){ getLogger().log(java.util.logging.Level.SEVERE,"DREAM OWNERSHIP FAILED phase="+phase,error);task.cancel();getServer().shutdown(); }
        },20L,2L);
    }
    private void step() throws Exception {
        check(++ticks<400,"timeout");
        boolean prepare=System.getProperty("dream.ownership.phase","verify").equals("prepare");
        Path receipt=getDataFolder().toPath().resolve("fixture.properties");
        if(phase==0) {
            dream=(DreamSkyblock)getServer().getPluginManager().getPlugin("DreamSkyblock");
            if(dream==null || !dream.getHomeManager().getReady())return;
            if(prepare) {
                home=dream.getHomeManager().all().stream().filter(h->h.getStatus().equals("ACTIVE")).findFirst().orElseThrow();
                Properties p=new Properties();p.setProperty("home-id",home.getId().toString());
                p.setProperty("owners",home.getOwners().toString());p.setProperty("level",Integer.toString(home.getLevel()));
                Files.createDirectories(receipt.getParent());try(var w=Files.newBufferedWriter(receipt)){p.store(w,"isolated ownership fixture");}
            } else {
                Properties p=new Properties();try(var r=Files.newBufferedReader(receipt)){p.load(r);}
                home=dream.getHomeManager().byId(UUID.fromString(p.getProperty("home-id")));
                check(home!=null && home.getStatus().equals("ACTIVE"),"startup automatically restored active island");
                check(home.getOwners().toString().equals(p.getProperty("owners")),"owners preserved");
                check(home.getLevel()==Integer.parseInt(p.getProperty("level")),"level preserved");
            }
            dream.getWorldManager().load(home,dream.getConfigService().getTemplates().get(home.getTemplateId()),null,ok->{check(ok,"load");loaded=true;return Unit.INSTANCE;});phase=1;
        } else if(phase==1 && loaded) {
            World w=dream.getWorldManager().getLoaded(home);
            if(prepare) {
                Properties p=new Properties();try(var r=Files.newBufferedReader(receipt)){p.load(r);}
                p.setProperty("world-uuid",w.getUID().toString());p.setProperty("world-path",w.getWorldFolder().getCanonicalPath());
                try(var out=Files.newBufferedWriter(receipt)){p.store(out,"isolated ownership fixture");}
                w.getBlockAt(9,100,9).setType(Material.EMERALD_BLOCK);
                w.getBlockAt(10,100,9).setType(Material.CHEST);
                ((Chest)w.getBlockAt(10,100,9).getState()).getInventory().setItem(0,new ItemStack(Material.DIAMOND,11));
                w.getPersistentDataContainer().set(marker,PersistentDataType.STRING,"survives-recovery");
                backup=dream.getHomeManager().backup(home.getId());phase=2;
            } else {
                Properties p=new Properties();try(var r=Files.newBufferedReader(receipt)){p.load(r);}
                check(w.getUID().toString().equals(p.getProperty("world-uuid")),"world UUID preserved");
                check(w.getWorldFolder().getCanonicalPath().equals(p.getProperty("world-path")),"world path preserved");
                check(w.getBlockAt(9,100,9).getType()==Material.EMERALD_BLOCK,"building preserved");
                var item=((Chest)w.getBlockAt(10,100,9).getState()).getInventory().getItem(0);
                check(item!=null && item.getType()==Material.DIAMOND && item.getAmount()==11,"container preserved");
                check("survives-recovery".equals(w.getPersistentDataContainer().get(marker,PersistentDataType.STRING)),"world PDC preserved");
                check(Files.exists(dream.getDataFolder().toPath().resolve("data/recovery/pending-worlds.properties")),"durable audit report exists");
                getLogger().info("DREAM OWNERSHIP ALL PASS: startup reattached missing SQLite row; native world UUID/path, owners, level, buildings, chest and world PDC preserved");
                getServer().shutdown();phase=99;
            }
        } else if(phase==2 && backup.isDone()) {
            check(backup.getNow(null)!=null,"backup finished");
            getLogger().info("DREAM OWNERSHIP PREPARED: real saved world and committed recovery evidence ready");getServer().shutdown();phase=99;
        }
    }
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
