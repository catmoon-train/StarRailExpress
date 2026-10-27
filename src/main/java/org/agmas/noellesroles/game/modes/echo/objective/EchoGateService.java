package org.agmas.noellesroles.game.modes.echo.objective;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.game.modes.echo.map.EchoMapConfig;

import java.io.IOException;
import java.util.*;

/** 门的纯状态投影到限定方块区域；写入失败由会话中止，不继续消耗钥匙。 */
public final class EchoGateService {
    private final ServerLevel level;
    private final EchoMapConfig map;
    private final EchoGateState state;
    private final org.agmas.noellesroles.game.modes.echo.rules.EchoRuleConfig.Gate rules;
    private final EchoGateJournal journal;
    private final Map<EchoGateJournal.Pos,String> original=new LinkedHashMap<>();
    private boolean coreOpen,evacOpen;
    private boolean cleaned;

    public EchoGateService(ServerLevel level,UUID matchId,EchoMapConfig map) {
        this(level,matchId,map,org.agmas.noellesroles.game.modes.echo.rules.EchoRuleConfig.DEFAULT.gate());
    }
    public EchoGateService(ServerLevel level,UUID matchId,EchoMapConfig map,org.agmas.noellesroles.game.modes.echo.rules.EchoRuleConfig.Gate rules) {
        this.level=level; this.map=map; this.rules=rules; state=new EchoGateState(rules.overloadTicks());
        List<EchoMapConfig.Box> volumes=new ArrayList<>();
        map.coreGate().doors().forEach(d -> volumes.add(d.volume()));
        volumes.add(map.evacuation().doorVolume());
        for(var volume:volumes) for(BlockPos pos:BlockPos.betweenClosed((int)volume.minX(),(int)volume.minY(),(int)volume.minZ(),
                (int)volume.maxX()-1,(int)volume.maxY()-1,(int)volume.maxZ()-1)) {
            original.put(key(pos),serialize(level.getBlockState(pos)));
        }
        journal=new EchoGateJournal(level.getServer().getWorldPath(LevelResource.ROOT),matchId,
                level.dimension().location().toString(),map.mapId(),original);
    }
    public EchoGateState state() { return state; }
    public static Vec3 vec(EchoMapConfig.Vec p) { return new Vec3(p.x(),p.y(),p.z()); }
    public static AABB box(EchoMapConfig.Box b) { return new AABB(b.minX(),b.minY(),b.minZ(),b.maxX(),b.maxY(),b.maxZ()); }
    private static EchoGateJournal.Pos key(BlockPos p) { return new EchoGateJournal.Pos(p.getX(),p.getY(),p.getZ()); }
    private static String serialize(net.minecraft.world.level.block.state.BlockState state) { return NbtUtils.writeBlockState(state).toString(); }

    public static EchoGateJournal.Access access(ServerLevel level) {
        return new EchoGateJournal.Access() {
            public String read(EchoGateJournal.Pos p) { return serialize(level.getBlockState(new BlockPos(p.x(),p.y(),p.z()))); }
            public void write(EchoGateJournal.Pos p,String state) {
                try {
                    var restored=NbtUtils.readBlockState(level.holderLookup(Registries.BLOCK),TagParser.parseTag(state));
                    BlockPos pos=new BlockPos(p.x(),p.y(),p.z());
                    level.setBlock(pos,restored,3);
                    if(!level.getBlockState(pos).equals(restored)) throw new IllegalStateException("门体方块写入未生效");
                } catch(Exception failure) { throw new IllegalStateException("门体状态恢复失败",failure); }
            }
        };
    }
    public boolean openCore(long now) { return openCore(now,rules.coreOpenTicks()); }
    public boolean openCore(long now,int ticks) {
        if(!state.openCore(now,ticks)) return false;
        synchronize(now); return true;
    }
    public void beginEvacuation(long now) { state.beginEvacuation(); synchronize(now); }
    public boolean overload(long now) {
        if(!state.overload(now)) return false;
        synchronize(now); return true;
    }
    public void tick(long now) { synchronize(now); }

    private void synchronize(long now) {
        boolean nextCore=state.coreOpen(now),nextEvac=state.evacuationOpen(now);
        Map<EchoGateJournal.Pos,String> changes=new LinkedHashMap<>();
        if(coreOpen!=nextCore) for(var door:map.coreGate().doors()) project(door.volume(),nextCore,changes,false);
        if(evacOpen!=nextEvac) project(map.evacuation().doorVolume(),nextEvac,changes,true);
        if(!changes.isEmpty()) {
            try { journal.apply(changes,access(level)); }
            catch(IOException failure) { throw new IllegalStateException("门体日志/方块写入失败",failure); }
        }
        var match=org.agmas.noellesroles.game.modes.echo.runtime.EchoSessions.get(level);
        if(match!=null) {
            if(coreOpen!=nextCore) for(var door:map.coreGate().doors()) EchoAudio.nearby(match,vec(door.controlPos()),nextCore?net.minecraft.sounds.SoundEvents.IRON_DOOR_OPEN:net.minecraft.sounds.SoundEvents.IRON_DOOR_CLOSE,32,1);
            if(evacOpen!=nextEvac) EchoAudio.nearby(match,vec(map.evacuation().controlPos()),nextEvac?net.minecraft.sounds.SoundEvents.IRON_DOOR_OPEN:net.minecraft.sounds.SoundEvents.IRON_DOOR_CLOSE,32,1);
        }
        coreOpen=nextCore; evacOpen=nextEvac;
    }

    private void project(EchoMapConfig.Box volume,boolean open,Map<EchoGateJournal.Pos,String> changes,boolean evacuation) {
        if(!open) eject(volume,evacuation);
        for(var entry:original.entrySet()) {
            var p=entry.getKey();
            if(box(volume).contains(p.x()+0.5,p.y()+0.5,p.z()+0.5)) {
                changes.put(p,open?serialize(Blocks.AIR.defaultBlockState()):entry.getValue());
            }
        }
    }

    private void eject(EchoMapConfig.Box volume,boolean evacuation) {
        AABB door=box(volume);
        for(ServerPlayer player:level.players()) {
            if(player.isSpectator() || !player.getBoundingBox().intersects(door)) continue;
            if(evacuation) { teleport(player,map.evacuation().outsideSafe()); continue; }
            List<Vec3> candidates=new ArrayList<>(List.of(new Vec3(door.minX-0.4,player.getY(),player.getZ()),
                    new Vec3(door.maxX+0.4,player.getY(),player.getZ()),new Vec3(player.getX(),player.getY(),door.minZ-0.4),
                    new Vec3(player.getX(),player.getY(),door.maxZ+0.4)));
            candidates.sort(Comparator.comparingDouble(p -> p.distanceToSqr(player.position())));
            Vec3 safe=candidates.stream().filter(p -> !player.getBoundingBox().move(p.subtract(player.position())).intersects(door)
                    && level.noCollision(player,player.getBoundingBox().move(p.subtract(player.position())))
                    && level.getBlockState(BlockPos.containing(p).below()).isCollisionShapeFullBlock(level,BlockPos.containing(p).below()))
                    .findFirst().orElse(vec(new EchoMapConfig.Vec(map.coreGate().freeExit().outsideSafe().x(),
                            map.coreGate().freeExit().outsideSafe().y(),map.coreGate().freeExit().outsideSafe().z())));
            player.teleportTo(level,safe.x,safe.y,safe.z,player.getYRot(),player.getXRot());
        }
    }
    public static void teleport(ServerPlayer player,EchoMapConfig.Spawn p) {
        player.teleportTo(player.serverLevel(),p.x(),p.y(),p.z(),p.yaw(),p.pitch());
    }
    public void cleanup() {
        if(cleaned) return;
        cleaned=true;
        for(var door:map.coreGate().doors()) eject(door.volume(),false);
        eject(map.evacuation().doorVolume(),true);
        try {
            if(!journal.restore(access(level))) Noellesroles.LOGGER.error("回声门体恢复存在外部编辑冲突: {}",map.mapId());
        } catch(IOException|RuntimeException failure) { Noellesroles.LOGGER.error("回声门体恢复失败: {}",map.mapId(),failure); }
    }
}
