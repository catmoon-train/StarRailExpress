package org.agmas.noellesroles.game.modes.echo.map;

import io.wifi.starrailexpress.cca.AreasWorldComponent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** 不加载区块、不写方块的分帧路线诊断；半砖/梯子等非整数路线保守地要求人工复核。 */
public final class EchoRouteCheck {
    public enum Status { RUNNING, PASSED, FAILED, MANUAL }
    private record Goal(String id,EchoMapConfig.Vec point,boolean exact) { }
    private final ServerLevel level;
    private final EchoMapConfig map;
    private final AreasWorldComponent areas;
    private final List<Goal> openGoals=new ArrayList<>(),coreGoals=new ArrayList<>();
    private final List<String> issues=new ArrayList<>();
    private final EchoWalkGraph.Cell root;
    private final boolean jumping;
    private EchoWalkGraph graph;
    private int phase,totalVisited,totalProbes;
    private long cpuNanos;
    private boolean incomplete,specialTerrain;
    private Status status=Status.RUNNING;
    public EchoRouteCheck(ServerLevel level,EchoMapConfig map) {
        if(!map.coordinateSpace().equals("world") || map.carrierSpawns().isEmpty()) throw new IllegalArgumentException("路线需要完整的实战坐标地图和托运者出生点");
        this.level=level; this.map=map; areas=AreasWorldComponent.KEY.get(level); jumping=areas.areasSettings.canJump;
        root=cell(map.carrierSpawns().getFirst().pos());
        spawns("carrier",map.carrierSpawns()); spawns("interceptor",map.interceptorSpawns());
        map.beacons().forEach(p -> (p.region().equals("core")?coreGoals:openGoals).add(new Goal("beacon/"+p.id(),p.pos(),false)));
        map.crystalNodes().forEach(p -> openGoals.add(new Goal("crystal/"+p.id(),p.pos(),false)));
        map.altars().forEach(p -> openGoals.add(new Goal("altar/"+p.id(),p.pos(),false)));
        map.supplyCrates().forEach(p -> (p.quality().equals("BLACK_MARKET")?coreGoals:openGoals).add(new Goal("crate/"+p.id(),p.pos(),false)));
        map.blackCrystalNodes().forEach(p -> openGoals.add(new Goal("black_node/"+p.id(),p.pos(),false)));
        map.blackDropoffs().forEach(p -> openGoals.add(new Goal("black_dropoff/"+p.id(),p.pos(),false)));
        map.coreGate().doors().forEach(p -> openGoals.add(new Goal("core_control/"+p.id(),p.controlPos(),false)));
        openGoals.add(new Goal("free_safe",map.coreGate().freeExit().outsideSafe().pos(),true));
        openGoals.add(new Goal("evacuation_safe",map.evacuation().outsideSafe().pos(),true));
        openGoals.add(new Goal("evacuation_control",map.evacuation().controlPos(),false));
        openGoals.add(new Goal("broker_exit",map.brokerExit().pos(),false));
        coreGoals.add(new Goal("free_control",map.coreGate().freeExit().controlPos(),false));
        begin();
    }
    private void spawns(String kind,List<EchoMapConfig.Spawn> spawns) { for(int i=0;i<spawns.size();i++) openGoals.add(new Goal(kind+"/"+i,spawns.get(i).pos(),true)); }
    private static EchoWalkGraph.Cell cell(EchoMapConfig.Vec p) { return new EchoWalkGraph.Cell((int)Math.floor(p.x()),(int)Math.floor(p.y()),(int)Math.floor(p.z())); }
    private static EchoMapConfig.Vec center(EchoWalkGraph.Cell c) { return new EchoMapConfig.Vec(c.x()+0.5,c.y(),c.z()+0.5); }
    private boolean evacuationPhase() { return phase>=2+map.coreGate().doors().size()*2; }
    private boolean virtualAir(BlockPos pos) {
        int door=phase/2-1;
        var point=new EchoMapConfig.Vec(pos.getX()+0.5,pos.getY()+0.5,pos.getZ()+0.5);
        return evacuationPhase()?map.evacuation().doorVolume().contains(point):door>=0 && map.coreGate().doors().get(door).volume().contains(point);
    }
    private boolean loaded(BlockPos pos) {
        if(!areas.getPlayArea().contains(pos.getX()+0.5,pos.getY(),pos.getZ()+0.5) || pos.getY()<level.getMinBuildHeight() || pos.getY()>=level.getMaxBuildHeight() || !level.getWorldBorder().isWithinBounds(pos)) return false;
        if(!level.hasChunkAt(pos)) { incomplete=true; return false; }
        return true;
    }
    private boolean air(BlockPos pos) {
        if(!loaded(pos)) return false;
        if(virtualAir(pos)) return true;
        var state=level.getBlockState(pos);
        var shape=state.getCollisionShape(level,pos);
        if(!shape.isEmpty() && !state.isCollisionShapeFullBlock(level,pos)) specialTerrain=true;
        return shape.isEmpty() && level.getFluidState(pos).isEmpty() && !hazard(pos);
    }
    private boolean hazard(BlockPos pos) {
        var state=level.getBlockState(pos);
        return state.is(BlockTags.FIRE) || state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.CACTUS) || state.is(Blocks.SWEET_BERRY_BUSH)
                || state.is(Blocks.WITHER_ROSE) || state.is(Blocks.POWDER_SNOW) || state.is(Blocks.CAMPFIRE) || state.is(Blocks.SOUL_CAMPFIRE);
    }
    private boolean clear(EchoWalkGraph.Cell c) { var p=new BlockPos(c.x(),c.y(),c.z()); return air(p) && air(p.above()); }
    private boolean stand(EchoWalkGraph.Cell c) {
        var p=new BlockPos(c.x(),c.y(),c.z()); var below=p.below();
        return clear(c) && loaded(below) && !virtualAir(below) && !hazard(below) && level.getFluidState(below).isEmpty() && level.getBlockState(below).isCollisionShapeFullBlock(level,below);
    }
    private void begin() {
        incomplete=false;
        graph=new EchoWalkGraph(new EchoWalkGraph.Terrain() {
            @Override public boolean stand(EchoWalkGraph.Cell c) { return EchoRouteCheck.this.stand(c); }
            @Override public boolean clear(EchoWalkGraph.Cell c) { return EchoRouteCheck.this.clear(c); }
        },root,jumping,phase%2==1,60_000,360_000);
    }
    private boolean goal(Goal goal) {
        var base=cell(goal.point()); if(goal.exact()) return graph.reached(base);
        for(int x=-3;x<=3;x++) for(int y=-2;y<=2;y++) for(int z=-3;z<=3;z++) {
            var c=base.add(x,y,z); if(!graph.reached(c)) continue;
            var feet=center(c); var eye=new Vec3(feet.x(),feet.y()+1.62,feet.z()); var p=goal.point(); var target=new Vec3(p.x(),p.y(),p.z());
            if(eye.distanceToSqr(target)>9 || (!evacuationPhase() && ((phase<2 && map.coreArea().contains(feet)) || (phase>=2 && !map.coreArea().contains(feet))))) continue;
            // 虚拟开门后已到达核心内；目标LOS仍以真实方块检测，门外不能隔墙算作可操作。
            var hit=level.clip(new ClipContext(eye,target,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,net.minecraft.world.phys.shapes.CollisionContext.empty()));
            if(hit.getType()==HitResult.Type.MISS || hit.getBlockPos().equals(BlockPos.containing(target))) return true;
        }
        return false;
    }
    private void finishPhase() {
        totalVisited+=graph.visitedCount(); totalProbes+=graph.probes();
        // 已发现的闭门绕入即使随后超预算也不能变成人工放行。
        if(phase==0 && graph.reached().stream().anyMatch(c -> map.coreArea().contains(center(c)))) {
            status=Status.FAILED; issues.add("入口关闭时仍能步行进入核心区"); return;
        }
        if(graph.status()==EchoWalkGraph.Status.LIMIT || incomplete) {
            status=Status.MANUAL; issues.add(incomplete?"检查边界包含未加载区块；不强制加载，请制图者人工确认":"步行图超过访问/采样预算；请制图者人工确认"); return;
        }
        String stage=evacuationPhase()?"evacuation":phase<2?"closed":map.coreGate().doors().get(phase/2-1).id();
        if(evacuationPhase() && graph.reached().stream().noneMatch(c -> map.evacuation().zone().contains(center(c)))) issues.add(stage+" -> zone");
        for(var goal:evacuationPhase()?List.<Goal>of(new Goal("exit_drop_anchor",map.evacuation().exitDropAnchor(),true)):phase<2?openGoals:coreGoals) if(!goal(goal)) {
            if(issues.size()<12) issues.add(stage+(phase%2==0?" -> ":" <- ")+goal.id());
        }
        if(!issues.isEmpty()) {
            status=specialTerrain?Status.MANUAL:Status.FAILED;
            if(specialTerrain && issues.size()<12) issues.add("发现半砖/特殊碰撞地形，整数步行图未证明的路线需要人工复核");
            return;
        }
        phase++;
        if(phase>=4+map.coreGate().doors().size()*2) status=Status.PASSED;
        else if(totalVisited>=240_000 || totalProbes>=1_440_000) { status=Status.MANUAL; issues.add("全部入口累计预算已用尽；请人工确认剩余路线"); }
        else begin();
    }
    public Status advance(int nodes) {
        if(status!=Status.RUNNING) return status;
        long start=System.nanoTime(); graph.advance(nodes);
        if(graph.status()!=EchoWalkGraph.Status.RUNNING) finishPhase();
        cpuNanos+=System.nanoTime()-start;
        if(status==Status.RUNNING && cpuNanos>2_000_000_000L) { status=Status.MANUAL; issues.add("累计CPU预算2秒已用尽；请人工确认"); }
        return status;
    }
    public Status status() { return status; }
    public int visited() { return totalVisited+(status==Status.RUNNING?graph.visitedCount():0); }
    public List<String> issues() { return List.copyOf(issues); }
}
