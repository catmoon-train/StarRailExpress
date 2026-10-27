package org.agmas.noellesroles.game.modes.echo.runtime;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.game.modes.echo.EchoBootstrap;
import org.agmas.noellesroles.game.modes.echo.result.EchoResultSnapshot;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** 世界实例为键；注册的 GameMode 对象本身不持有全服共用的对局。 */
public final class EchoSessions {
    private static final Map<ServerLevel, EchoMatch> MATCHES = new IdentityHashMap<>();
    private static final Map<ServerLevel, EchoPreflight.Prepared> PREPARED = new IdentityHashMap<>();
    private static final java.util.Set<ServerLevel> DEBUG=java.util.Collections.newSetFromMap(new IdentityHashMap<>());
    public static boolean debug(ServerLevel level) { return DEBUG.contains(level); }
    public static void markDebug(ServerLevel level) { DEBUG.add(level); }
    private record HeldChunks(org.agmas.noellesroles.game.modes.echo.map.EchoChunkPreparation lease,String map,long acquired) { }
    private static final Map<ServerLevel,HeldChunks> CHUNKS=new IdentityHashMap<>();
    private static String mapContext(ServerLevel level) {
        var areas=io.wifi.starrailexpress.cca.AreasWorldComponent.KEY.get(level);
        return areas.mapName+"|"+EchoPreflight.offset(areas)+"|"+areas.getPlayArea();
    }
    static void holdChunks(ServerLevel level,org.agmas.noellesroles.game.modes.echo.map.EchoChunkPreparation chunks) {
        String context=mapContext(level);
        releaseChunks(level);
        CHUNKS.put(level,new HeldChunks(java.util.Objects.requireNonNull(chunks),context,sequence));
    }
    /** 门体清理完成以后调用；只撤销本次准入持有的票据。 */
    public static void releaseChunks(ServerLevel level) {
        PREPARED.remove(level);
        var held=CHUNKS.remove(level);
        if(held!=null) try { held.lease().close(); }
        catch(RuntimeException failure) { org.agmas.noellesroles.Noellesroles.LOGGER.error("回声区块租约清理失败",failure); }
    }
    private static long sequence;
    private static final java.util.Set<ServerPlayer> JOINED=java.util.Collections.newSetFromMap(new IdentityHashMap<>());
    public static void joined(ServerPlayer player) { JOINED.add(player); }
    public static void disconnected(ServerPlayer player) {
        JOINED.remove(player);
        for(var match:List.copyOf(MATCHES.values())) if(match.participants().contains(player.getUUID())) match.disconnect(player);
    }

    private EchoSessions() { }

    public static EchoMatch get(ServerLevel level) { return MATCHES.get(level); }
    public static void prepare(ServerLevel level, EchoPreflight.Prepared prepared) { DEBUG.remove(level); PREPARED.put(level, prepared); }

    public static void initialize(ServerLevel level, List<ServerPlayer> players) {
        var prepared = PREPARED.remove(level);
        try {
            if (prepared == null || prepared.profile().playerCount() != players.size() || !CHUNKS.containsKey(level)) {
                throw new IllegalStateException("回声最终预检快照或区块租约缺失");
            }
            MATCHES.put(level, new EchoMatch(level, prepared, players));
        } catch(RuntimeException failure) { releaseChunks(level); throw failure; }
    }

    public static void startTick(MinecraftServer server) {
        sequence++;
        for(var level:List.copyOf(CHUNKS.keySet())) {
            var held=CHUNKS.get(level); var game=SREGameWorldComponent.KEY.get(level); var match=get(level);
            boolean changed;
            try { changed=!held.map().equals(mapContext(level)); }
            catch(RuntimeException invalid) { changed=true; }
            boolean active=game.getGameMode().identifier.equals(EchoBootstrap.ID)
                    && game.getGameStatus()==SREGameWorldComponent.GameStatus.ACTIVE;
            boolean preparing=game.getGameMode().identifier.equals(EchoBootstrap.ID)
                    && game.getGameStatus()==SREGameWorldComponent.GameStatus.STARTING
                    && PREPARED.containsKey(level) && sequence-held.acquired()<100;
            if(changed || (!active && !preparing) || (active && (match==null || match.result()!=null))) {
                try {
                    if(match!=null && match.result()==null) match.settle(EchoResultSnapshot.Outcome.VOID,changed?"MAP_CHANGED":"SESSION_ENDED",active);
                } finally { releaseChunks(level); }
            }
        }
        for(var player:List.copyOf(JOINED)) {
            JOINED.remove(player);
            if(server.getPlayerList().getPlayer(player.getUUID())!=player) continue;
            var match=MATCHES.values().stream().filter(m -> m.result()==null && m.participants().contains(player.getUUID())).findFirst().orElse(get(player.serverLevel()));
            if(match!=null) try { match.join(player); }
            catch(RuntimeException failure) {
                org.agmas.noellesroles.Noellesroles.LOGGER.error("回声重连恢复失败 {}",player.getUUID(),failure);
                match.settle(EchoResultSnapshot.Outcome.VOID,"RECONNECT_FAILURE",true);
            }
        }
        for (ServerLevel level : server.getAllLevels()) {
            var game = SREGameWorldComponent.KEY.get(level);
            var match = get(level);
            if (match != null && game.getGameMode().identifier.equals(EchoBootstrap.ID)
                    && game.getGameStatus() == SREGameWorldComponent.GameStatus.ACTIVE) match.startTick(sequence);
        }
    }

    public static void endTick(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            var game = SREGameWorldComponent.KEY.get(level);
            var match = get(level);
            if (match != null && game.getGameMode().identifier.equals(EchoBootstrap.ID)
                    && game.getGameStatus() == SREGameWorldComponent.GameStatus.ACTIVE) match.endTick();
        }
    }

    public static void unload(ServerLevel level) {
        EchoMatch match = MATCHES.remove(level);
        PREPARED.remove(level);
        try {
            if (match != null) {
                if (match.result() == null) match.settle(EchoResultSnapshot.Outcome.VOID, "WORLD_UNLOADED", false);
                match.finalizeMatch();
            }
        } finally { releaseChunks(level); DEBUG.remove(level); }
    }

    public static void stopServer(MinecraftServer server) {
        try {
            for (ServerLevel level : server.getAllLevels()) try { unload(level); }
            catch(RuntimeException failure) { org.agmas.noellesroles.Noellesroles.LOGGER.error("回声停服清理失败",failure); }
        } finally {
            for(var level:List.copyOf(CHUNKS.keySet())) releaseChunks(level);
            PREPARED.clear(); JOINED.clear(); DEBUG.clear();
        }
    }
}
