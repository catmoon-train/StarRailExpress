package org.agmas.noellesroles.game.modes.echo.map;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import java.util.IdentityHashMap;
import java.util.Map;

/** 仅为正在诊断/准入的实战范围跟踪真实方块及区块生命周期；无观察者时不读 Areas。 */
public final class EchoTerrainRevision {
    private static final class Watch {
        final EchoMapConfig.Box area;
        long revision;
        Watch(EchoMapConfig.Box area) { this.area=area; }
    }
    private static final Map<ServerLevel,Watch> WATCHES=new IdentityHashMap<>();
    private EchoTerrainRevision() { }
    public static long watch(ServerLevel level,EchoMapConfig.Box area) {
        var old=WATCHES.get(level);
        if(old==null || !old.area.equals(area)) { old=new Watch(area); WATCHES.put(level,old); }
        return old.revision;
    }
    public static long current(ServerLevel level) { var watch=WATCHES.get(level); return watch==null?-1:watch.revision; }
    public static void changed(ServerLevel level,BlockPos pos) {
        var watch=WATCHES.get(level); if(watch==null) return; var a=watch.area;
        if(pos.getX()>=a.minX()-2 && pos.getX()<a.maxX()+2 && pos.getY()>=a.minY()-2 && pos.getY()<a.maxY()+2 && pos.getZ()>=a.minZ()-2 && pos.getZ()<a.maxZ()+2) watch.revision++;
    }
    public static void chunkChanged(ServerLevel level,ChunkPos pos) {
        var watch=WATCHES.get(level); if(watch==null) return; var a=watch.area;
        if(pos.getMaxBlockX()>=a.minX()-2 && pos.getMinBlockX()<a.maxX()+2 && pos.getMaxBlockZ()>=a.minZ()-2 && pos.getMinBlockZ()<a.maxZ()+2) watch.revision++;
    }
    public static void unload(ServerLevel level) { WATCHES.remove(level); }
    public static void stop() { WATCHES.clear(); }
}
