import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import ym.dreamSkyblock.DreamSkyblock;
import ym.dreamSkyblock.api.*;
import ym.dreamSkyblock.model.Home;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Real ServicesManager/value API, SQLite and world lifecycle. Synthetic player transport only. */
public final class DreamValueProbe extends JavaPlugin {
    private DreamSkyblock dream;
    private DreamSkyblockApi api;
    private Home home;
    private Player owner;
    private volatile Throwable failure;
    private volatile boolean completed;
    private volatile String backup;
    private int phase,ticks;
    private final HomeValueKey first=new HomeValueKey("test-machines","first");
    private final HomeValueKey second=HomeValueKey.block("test-machines",1,100,2);
    private static final UUID OWNER=UUID.fromString("00000000-0000-0000-0000-000000000066");
    @Override public void onEnable(){
        getServer().getGlobalRegionScheduler().runAtFixedRate(this,t->{
            try{step();}catch(Throwable e){getLogger().log(java.util.logging.Level.SEVERE,"DREAM VALUE FAILED phase="+phase,e);t.cancel();getServer().shutdown();}
        },20L,2L);
    }
    private void step() {
        check(++ticks<900,"timeout phase="+phase);
        if(failure!=null)throw new AssertionError("async phase="+phase,failure);
        if(phase==-1){if(dream.getHomeManager().byPlayer(OWNER)==null)phase=0;return;}
        if(phase==0){
            dream=(DreamSkyblock)getServer().getPluginManager().getPlugin("DreamSkyblock");
            if(dream==null || !dream.getHomeManager().getReady())return;
            api=getServer().getServicesManager().load(DreamSkyblockApi.class);check(api!=null && api.isReady(),"service registered");
            owner=actor();home=dream.getHomeManager().byPlayer(OWNER);
            if(Boolean.getBoolean("dream.value.restart")){
                check(home!=null,"restarted home exists");
                verifyRestored();
                check(api.setValue(home.getId(),second,100,3)!=null,"stale retry future after restart");
                watch(api.setValue(home.getId(),second,100,3).thenAccept(r->check(r.getStatus()==HomeValueStatus.STALE && r.getTotalValue()==550,"restart retains tombstone")));
                phase=8;return;
            }
            if(home!=null){dream.getHomeManager().delete(owner);phase=-1;return;}
            dream.getHomeManager().create(owner,"skyblock");phase=1;
        }else if(phase==1){
            home=dream.getHomeManager().byPlayer(OWNER);if(home==null || !home.getStatus().equals("ACTIVE"))return;
            check(api.findHomeByWorldKey("dreamskyblock:dreamskyblock_"+home.getId().toString().replace("-","")).getHomeId().equals(home.getId()),"world-key lookup");
            var one=CompletableFuture.supplyAsync(()->api.setValue(home.getId(),first,550,1)).thenCompose(x->x);
            var two=api.setValue(home.getId(),second,100,1);
            watch(CompletableFuture.allOf(one,two));phase=2;
        }else if(phase==2){
            if(!completed)return;
            var view=api.findHome(home.getId());check(view.getExperience()==650 && view.getLevel()==4,"concurrent source updates sum without loss");
            if(dream.getWorldManager().getLoaded(home).getWorldBorder().getSize()!=128)return;
            check(view.getBorderSize()==128,"native border expanded on API value commit");
            watch(api.setValue(home.getId(),first,550,1).thenAccept(r->check(r.getStatus()==HomeValueStatus.UNCHANGED,"idempotent registration"))
                .thenCompose(x->api.setValue(home.getId(),first,551,1).handle((r,e)->{check(e!=null,"same revision different value rejected");return null;}))
                .thenCompose(x->api.removeValue(home.getId(),second,2)).thenAccept(r->check(r.getTotalValue()==550 && r.getLevel()==3 && r.getBorderSize()==128,"removal downgrades and keeps unlocked border"))
                .thenCompose(x->api.setValue(home.getId(),second,100,1)).thenAccept(r->check(r.getStatus()==HomeValueStatus.STALE,"old placement rejected"))
                .thenCompose(x->api.removeValue(home.getId(),second,2)).thenAccept(r->check(r.getStatus()==HomeValueStatus.UNCHANGED,"duplicate removal"))
                .thenCompose(x->api.setValue(home.getId(),second,100,3)).thenAccept(r->check(r.getTotalValue()==650,"new placement revision accepted"))
                .thenCompose(x->api.removeValue(home.getId(),second,4)));
            phase=3;
        }else if(phase==3){
            if(!completed)return;
            check(api.valueRecords(home.getId()).get(second).getRevision()==4,"current removal revision");
            try{api.valueRecords(home.getId()).clear();throw new AssertionError("mutable map");}catch(UnsupportedOperationException expected){}
            watch(dream.getHomeManager().backup(home.getId()).thenAccept(id->backup=id));phase=4;
        }else if(phase==4){
            if(!completed)return;
            watch(api.removeValue(home.getId(),first,2).thenAccept(r->check(r.getTotalValue()==0 && r.getLevel()==1 && r.getBorderSize()==128,"all machines removed, no border shrink")));phase=5;
        }else if(phase==5){
            if(!completed)return;
            watch(dream.getHomeManager().restore(home.getId(),backup).thenAccept(restored->home=restored));phase=6;
        }else if(phase==6){
            if(!completed)return;
            verifyRestored();
            watch(api.setValue(home.getId(),second,100,3).thenAccept(r->check(r.getStatus()==HomeValueStatus.STALE,"backup restored removal revision"))
                .thenCompose(x->api.setValue(home.getId(),first,-1,5).handle((r,e)->{check(e!=null,"negative values rejected asynchronously");return null;}))
                .thenCompose(x->api.setValue(UUID.randomUUID(),first,1,1).handle((r,e)->{check(e!=null,"orphan home rejected");return null;})));
            phase=7;
        }else if(phase==7){
            if(!completed)return;
            getLogger().info("DREAM VALUE ALL PASS external async API + concurrent sources + level up/down + no shrink + idempotency/conflict/stale/tombstone + world-key lookup + immutable records + world border + backup/restore");
            getServer().shutdown();phase=99;
        }else if(phase==8){
            if(!completed)return;
            getServer().getPluginManager().disablePlugin(dream);
            watch(api.setValue(home.getId(),first,1,100).handle((r,e)->{check(e!=null && !api.isReady(),"disabled API rejects writes");return null;}));phase=9;
        }else if(phase==9){
            if(!completed)return;
            getLogger().info("DREAM VALUE RESTART ALL PASS persisted records + total/level + restored tombstone + disabled service rejects writes");
            getServer().shutdown();phase=99;
        }
    }
    private void verifyRestored(){
        var view=api.findHome(home.getId());check(view.getExperience()==550 && view.getLevel()==3 && view.getBorderSize()==128,"restored total/level/border");
        check(api.valueRecords(home.getId()).get(first).getValue()==550 && api.valueRecords(home.getId()).get(first).getRevision()==1,"restored facility data");
        check(api.valueRecords(home.getId()).get(second).getValue()==0 && api.valueRecords(home.getId()).get(second).getRevision()==4,"restored tombstone data");
    }
    private void watch(CompletableFuture<?> future){completed=false;future.whenComplete((r,e)->{failure=e;completed=true;});}
    private Player actor(){
        var scheduler=(EntityScheduler)Proxy.newProxyInstance(EntityScheduler.class.getClassLoader(),new Class[]{EntityScheduler.class},(p,m,a)->defaults(m.getReturnType()));
        return (Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class[]{Player.class},(p,m,a)->switch(m.getName()){
            case "getUniqueId"->OWNER;case "getName"->"ValueOwner";case "getServer"->getServer();case "isOnline","isValid"->true;
            case "hasPermission"->"dreamskyblock.use".equals(a[0]);case "getScheduler"->scheduler;
            case "getWorld"->getServer().getWorlds().getFirst();case "teleportAsync"->CompletableFuture.completedFuture(true);
            case "hashCode"->OWNER.hashCode();case "equals"->p==a[0];case "toString"->"ValueOwner";default->defaults(m.getReturnType());
        });
    }
    private static void check(boolean value,String detail){if(!value)throw new AssertionError(detail);}
    private static Object defaults(Class<?> t){if(!t.isPrimitive())return null;if(t==boolean.class)return false;if(t==int.class)return 0;if(t==long.class)return 0L;if(t==double.class)return 0d;if(t==float.class)return 0f;if(t==byte.class)return (byte)0;if(t==short.class)return (short)0;return null;}
}
