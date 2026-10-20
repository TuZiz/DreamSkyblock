import org.bukkit.*;
import org.bukkit.block.data.type.Leaves;
import ym.dreamSkyblock.DreamSkyblock;
import ym.dreamSkyblock.model.Home;
import ym.dreamSkyblock.world.StarterTreeLeaves;

/** Isolated server only: invoke native scheduled/random ticks explicitly because proxy players do not tick chunks. */
final class DreamTreeProbe {
    static java.util.concurrent.CompletableFuture<Boolean> scheduled(DreamSkyblock dream, Home home) {
        var result=new java.util.concurrent.CompletableFuture<Boolean>();
        var world=dream.getWorldManager().getLoaded(home);
        var template=dream.getConfigService().getTemplates().get(home.getTemplateId());
        ym.dreamSkyblock.world.IslandGenerator.INSTANCE.generate(world,template);
        var positions=StarterTreeLeaves.INSTANCE.canopy(template);
        var marker=new NamespacedKey("dreamskyblock","starter_leaves_v1");
        var persistent=(Leaves)Material.OAK_LEAVES.createBlockData();persistent.setPersistent(true);persistent.setDistance(1);
        for(var pos:positions) {
            world.setChunkForceLoaded(pos.getFirst()>>4,pos.getThird()>>4,true);
            world.getChunkAt(pos.getFirst()>>4,pos.getThird()>>4).getPersistentDataContainer().remove(marker);
            world.getBlockAt(pos.getFirst(),pos.getSecond(),pos.getThird()).setBlockData(persistent,false);
        }
        StarterTreeLeaves.INSTANCE.repairLoaded(world,template);
        dream.getServer().getGlobalRegionScheduler().runDelayed(dream,task->{
            try {
                for(var pos:positions) {
                    var block=world.getBlockAt(pos.getFirst(),pos.getSecond(),pos.getThird());
                    check(block.getBlockData() instanceof Leaves && ((Leaves)block.getBlockData()).getDistance()<7,"automatic scheduled distance recompute preserves connected canopy");
                }
                int x=(int)template.getCenter().getX()+2,y=template.getPlatformY()+1,z=(int)template.getCenter().getZ()+2;
                for(int dy=0;dy<4;dy++)world.getBlockAt(x,y+dy,z).setType(Material.AIR,true);
                dream.getServer().getGlobalRegionScheduler().runDelayed(dream,later->{
                    try {
                        for(var pos:positions) {
                            var data=world.getBlockAt(pos.getFirst(),pos.getSecond(),pos.getThird()).getBlockData();
                            check(!(data instanceof Leaves) || ((Leaves)data).getDistance()==7,"automatic native ticks disconnect chopped tree");
                        }
                        tick(world,positions,true,1);
                        for(var pos:positions)check(world.getBlockAt(pos.getFirst(),pos.getSecond(),pos.getThird()).getType()==Material.AIR,"native random tick decays automatically disconnected leaves");
                        dream.getLogger().info("DREAM TREE SCHEDULED PASS native automatic distance ticks for repaired connected and chopped canopy");
                        for(var pos:positions)world.setChunkForceLoaded(pos.getFirst()>>4,pos.getThird()>>4,false);
                        result.complete(true);
                    }catch(Throwable error){result.completeExceptionally(error);}
                },20L);
            }catch(Throwable error){result.completeExceptionally(error);}
        },20L);
        return result;
    }
    static void verify(DreamSkyblock dream, Home home) throws Exception {
        var world=dream.getWorldManager().getLoaded(home);
        var template=dream.getConfigService().getTemplates().get(home.getTemplateId());
        var positions=StarterTreeLeaves.INSTANCE.canopy(template);
        check(world.getGameRuleValue(GameRule.RANDOM_TICK_SPEED)==3,"vanilla internal random tick rate");
        for(var pos:positions) check(!((Leaves)world.getBlockAt(pos.getFirst(),pos.getSecond(),pos.getThird()).getBlockData()).isPersistent(),"new canopy is natural");
        var marker=new NamespacedKey("dreamskyblock","starter_leaves_v1");
        int x=(int)template.getCenter().getX()+2,y=template.getPlatformY()+1,z=(int)template.getCenter().getZ()+2;
        var decoration=world.getBlockAt(x+7,y+2,z);
        var persistent=(Leaves)Material.OAK_LEAVES.createBlockData();persistent.setPersistent(true);persistent.setDistance(7);
        decoration.setBlockData(persistent,false);
        for(var pos:positions) {
            world.getChunkAt(pos.getFirst()>>4,pos.getThird()>>4).getPersistentDataContainer().remove(marker);
            world.getBlockAt(pos.getFirst(),pos.getSecond(),pos.getThird()).setBlockData(persistent,false);
        }
        StarterTreeLeaves.INSTANCE.repairLoaded(world,template);
        tick(world,positions,false,8);
        for(var pos:positions) {
            var leaf=(Leaves)world.getBlockAt(pos.getFirst(),pos.getSecond(),pos.getThird()).getBlockData();
            check(!leaf.isPersistent() && leaf.getDistance()<7,"repaired connected leaves retain tree");
        }
        tick(world,positions,true,1);
        for(int dy=0;dy<4;dy++)world.getBlockAt(x,y+dy,z).setType(Material.AIR,true);
        tick(world,positions,false,8);
        tick(world,positions,true,1);
        for(var pos:positions)check(world.getBlockAt(pos.getFirst(),pos.getSecond(),pos.getThird()).getType()==Material.AIR,"orphan canopy decays through native randomTick");
        check(((Leaves)decoration.getBlockData()).isPersistent(),"decorations outside starter footprint unchanged");
        var point=positions.getFirst();
        var replacement=world.getBlockAt(point.getFirst(),point.getSecond(),point.getThird());
        replacement.setBlockData(persistent,false);
        StarterTreeLeaves.INSTANCE.repairLoaded(world,template);
        check(((Leaves)replacement.getBlockData()).isPersistent(),"one-time marker preserves later player placement");
        dream.getLogger().info("DREAM TREE ALL PASS natural new canopy + legacy connected/orphan repair + native decay/drop path + decoration guard + one-time chunk marker + internal randomTickSpeed=3");
    }
    private static void tick(World world,java.util.List<kotlin.Triple<Integer,Integer,Integer>> positions,boolean random,int repeats) throws Exception {
        var level=world.getClass().getMethod("getHandle").invoke(world);
        var serverLevel=Class.forName("net.minecraft.server.level.ServerLevel");
        var blockPos=Class.forName("net.minecraft.core.BlockPos");
        var randomSource=Class.forName("net.minecraft.util.RandomSource");
        var rng=randomSource.getMethod("create").invoke(null);
        var stateType=Class.forName("net.minecraft.world.level.block.state.BlockBehaviour$BlockStateBase");
        var method=stateType.getMethod(random?"randomTick":"tick",serverLevel,blockPos,randomSource);
        for(int i=0;i<repeats;i++)for(var pos:positions) {
            var point=blockPos.getConstructor(int.class,int.class,int.class).newInstance(pos.getFirst(),pos.getSecond(),pos.getThird());
            var state=level.getClass().getMethod("getBlockState",blockPos).invoke(level,point);
            method.invoke(state,level,point,rng);
        }
    }
    private static void check(boolean condition,String detail) { if(!condition)throw new AssertionError(detail); }
}
