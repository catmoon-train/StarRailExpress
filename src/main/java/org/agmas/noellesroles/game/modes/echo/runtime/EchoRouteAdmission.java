package org.agmas.noellesroles.game.modes.echo.runtime;

import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.game.modes.echo.EchoBootstrap;
import org.agmas.noellesroles.game.modes.echo.map.*;
import java.io.IOException;
import java.util.*;

/** 重置后先分帧加载并持有区块，再做原安全/路线检查；身份分配前失败关闭。 */
public final class EchoRouteAdmission {
    private record Context(String mapId,EchoMapConfig.Vec offset,EchoMapConfig.Box area,boolean jumping,
                           double borderMinX,double borderMaxX,double borderMinZ,double borderMaxZ,Set<UUID> roster) { }
    private static final class Job {
        final Context context;
        final EchoMapConfig map;
        final String files;
        EchoChunkPreparation chunks;
        EchoRoutePermit permit;
        EchoRouteCheck check;
        boolean failed;
        long loadedAt=-1,checkedAt=-20,reportedAt=-20;
        Job(Context context,EchoMapConfig map,String files) { this.context=context; this.map=map; this.files=files; }
    }
    private static final Map<ServerLevel,Job> JOBS=new LinkedHashMap<>();
    private static long tick,rotation;
    private EchoRouteAdmission() { }
    private static boolean starting(ServerLevel level) {
        var game=SREGameWorldComponent.KEY.get(level);
        return game.getGameStatus()==SREGameWorldComponent.GameStatus.STARTING && game.getGameMode().identifier.equals(EchoBootstrap.ID);
    }
    private static Context context(ServerLevel level,Set<UUID> roster) throws IOException {
        var areas=AreasWorldComponent.KEY.get(level); var a=areas.getPlayArea(); var border=level.getWorldBorder();
        String mapId=EchoActiveMap.effective(level.getServer().getWorldPath(LevelResource.ROOT),areas.mapName);
        return new Context(mapId,EchoPreflight.offset(areas),new EchoMapConfig.Box(a.minX,a.minY,a.minZ,a.maxX,a.maxY,a.maxZ),areas.areasSettings.canJump,
                border.getMinX(),border.getMaxX(),border.getMinZ(),border.getMaxZ(),roster);
    }
    private static String files(ServerLevel level,String mapId) throws IOException {
        var root=level.getServer().getWorldPath(LevelResource.ROOT).toRealPath();
        return EchoMapRepository.digest(root,mapId)+":"+org.agmas.noellesroles.game.modes.echo.rules.EchoRuleRepository.digest(root);
    }
    private static EchoRoutePermit.Stamp stamp(ServerLevel level,Job job) {
        return new EchoRoutePermit.Stamp(job.context.toString()+job.files,EchoTerrainRevision.current(level));
    }
    private static void message(ServerLevel level,String key,Object... args) { level.players().forEach(p -> p.sendSystemMessage(Component.translatable(key,args))); }
    private static void release(Job job) {
        var chunks=job.chunks; job.chunks=null;
        if(chunks!=null) try { chunks.close(); }
        catch(RuntimeException failure) { Noellesroles.LOGGER.error("回声区块票据释放异常",failure); }
    }
    private static void fail(ServerLevel level,Job job,String reason) {
        if(!job.failed) {
            job.failed=true;
            if(job.permit!=null) job.permit.fail();
            release(job);
            message(level,"echo.map.admission_failed",reason);
        }
    }
    private static boolean current(ServerLevel level,Job job,boolean checkFiles) throws IOException {
        if(job.failed) return false;
        if(!starting(level) || !job.context.equals(context(level,job.context.roster()))) { fail(level,job,"地图、偏移、设置或边界已改变"); return false; }
        if(job.permit!=null && !job.permit.valid(stamp(level,job),tick)) { fail(level,job,"地形/区块发生变化或许可已过期"); return false; }
        if(checkFiles) {
            job.checkedAt=tick;
            if(!job.files.equals(files(level,job.context.mapId()))) { fail(level,job,"地图或规则文件已改变"); return false; }
        }
        return true;
    }
    /** 保持STARTING；失败交回公共否决闸门统一恢复玩家准备状态。 */
    public static boolean defer(ServerLevel level,List<ServerPlayer> players) {
        if(!starting(level)) { discard(level); return false; }
        Job job=JOBS.get(level);
        try {
            var roster=Set.copyOf(players.stream().map(ServerPlayer::getUUID).toList());
            if(job!=null && !roster.equals(job.context.roster())) { fail(level,job,"参赛名单已改变"); return false; }
            if(job==null) {
                var c=context(level,roster);
                job=new Job(c,null,"");
                if(JOBS.size()>=2) { JOBS.put(level,job); fail(level,job,"开局路线任务已达上限"); return false; }
                var configuration=EchoPreflight.configuration(level,roster.size());
                if(!configuration.valid()) {
                    JOBS.put(level,job);
                    configuration.errors().stream().limit(8).forEach(error -> level.players().forEach(p -> p.sendSystemMessage(error)));
                    fail(level,job,"配置预检未通过"); return false;
                }
                job=new Job(c,configuration.prepared().map(),files(level,c.mapId()));
                JOBS.put(level,job);
                job.chunks=EchoChunkTickets.acquire(level,c.area(),tick);
                message(level,"echo.runtime.chunks_started",job.chunks.total());
            }
            if(!current(level,job,false)) return false;
            return job.permit==null || job.permit.status()==EchoRoutePermit.Status.SCANNING || job.permit.status()==EchoRoutePermit.Status.MANUAL;
        } catch(IOException | RuntimeException failure) {
            Noellesroles.LOGGER.warn("回声开局路线准备失败",failure);
            if(job!=null) { JOBS.put(level,job); fail(level,job,String.valueOf(failure.getMessage())); }
            return false;
        }
    }
    public static boolean consume(ServerLevel level,EchoMapConfig map,List<ServerPlayer> players) {
        Job job=JOBS.get(level);
        try {
            if(job==null || job.failed || job.permit==null || job.permit.status()!=EchoRoutePermit.Status.AUTHORIZED || !map.equals(job.map)
                    || !job.context.roster().equals(Set.copyOf(players.stream().map(ServerPlayer::getUUID).toList())) || !current(level,job,true)) return false;
            if(!job.permit.consume(stamp(level,job),tick)) return false;
            EchoSessions.holdChunks(level,job.chunks);
            job.chunks=null; // 所有权转移；整局保留，直到门体恢复后才释放。
            return true;
        } catch(IOException | RuntimeException failure) { Noellesroles.LOGGER.warn("回声路线许可校验失败",failure); return false; }
        finally { discard(level); }
    }
    public static int confirm(CommandSourceStack source,String nonce) throws IOException {
        if(!source.hasPermission(2)) throw new IOException("需要OP2权限");
        var level=source.getLevel(); var job=JOBS.get(level);
        if(job==null || job.failed || job.permit==null || job.permit.status()!=EchoRoutePermit.Status.MANUAL) throw new IOException("没有待人工验收的开局路线；已知失败不能放行");
        UUID supplied;
        try { supplied=UUID.fromString(nonce); } catch(IllegalArgumentException invalid) { fail(level,job,"确认令牌格式错误"); throw new IOException("确认令牌无效"); }
        if(!current(level,job,true) || !job.permit.confirm(supplied,stamp(level,job),tick)) {
            fail(level,job,"确认令牌过期、失效或已使用"); throw new IOException("确认令牌过期、失效或已使用");
        }
        Noellesroles.LOGGER.info("回声路线人工验收: operator={} dimension={} map={} files={} revision={}",source.getTextName(),level.dimension().location(),job.context.mapId(),job.files,EchoTerrainRevision.current(level));
        message(level,"echo.map.admission_confirmed"); return 1;
    }
    public static void cancel(ServerLevel level) { var job=JOBS.get(level); if(job!=null) fail(level,job,"管理员取消路线准备"); }
    public static void tick(MinecraftServer server) {
        tick++;
        for(var level:List.copyOf(JOBS.keySet())) if(!starting(level)) discard(level);
        if(JOBS.isEmpty()) return;
        var levels=List.copyOf(JOBS.keySet()); var level=levels.get((int)(rotation++%levels.size())); var job=JOBS.get(level);
        if(job.failed) return;
        try {
            if(!current(level,job,tick-job.checkedAt>=20)) return;
            if(job.permit==null) {
                boolean ready=job.chunks.advance(tick);
                if(tick-job.reportedAt>=20) {
                    job.reportedAt=tick;
                    message(level,"echo.runtime.chunks_progress",job.chunks.loaded(),job.chunks.total());
                }
                if(!ready) return;
                // FULL加载事件全部落地后再取stamp，不能让自己的CHUNK_LOAD令刚建立的许可失效。
                if(job.loadedAt<0) { job.loadedAt=tick; return; }
                if(tick-job.loadedAt<2) return;
                var safety=EchoPreflight.check(level,job.context.roster().size(),false);
                if(!safety.valid() || !job.map.equals(safety.prepared().map())) {
                    safety.errors().stream().limit(8).forEach(error -> level.players().forEach(p -> p.sendSystemMessage(error)));
                    fail(level,job,"区块加载后的安全预检未通过"); return;
                }
                if(!current(level,job,true)) return;
                EchoTerrainRevision.watch(level,job.context.area());
                job.permit=new EchoRoutePermit(stamp(level,job),tick);
                job.check=new EchoRouteCheck(level,job.map);
                message(level,"echo.map.admission_started");
            }
            if(job.permit.status()!=EchoRoutePermit.Status.SCANNING) return;
            long end=System.nanoTime()+2_000_000L;
            do { job.check.advance(16); } while(job.check.status()==EchoRouteCheck.Status.RUNNING && System.nanoTime()<end);
            if(job.check.status()!=EchoRouteCheck.Status.RUNNING && current(level,job,true)) {
                var result=switch(job.check.status()) { case PASSED -> EchoRoutePermit.Result.PASSED; case MANUAL -> EchoRoutePermit.Result.MANUAL; default -> EchoRoutePermit.Result.FAILED; };
                job.permit.complete(result,stamp(level,job),tick);
                job.check.issues().stream().limit(12).forEach(issue -> message(level,"echo.map.error",issue));
                if(result==EchoRoutePermit.Result.MANUAL) {
                    message(level,"echo.map.admission_manual");
                    level.players().stream().filter(p -> p.hasPermissions(2)).forEach(p -> p.sendSystemMessage(Component.translatable("echo.map.admission_token","/sre:echo map routes confirm "+job.permit.nonce())));
                } else if(result==EchoRoutePermit.Result.FAILED) fail(level,job,"路线检查失败");
                else message(level,"echo.map.admission_passed",job.check.visited());
            }
        } catch(IOException | RuntimeException failure) { Noellesroles.LOGGER.warn("回声开局路线扫描失败",failure); fail(level,job,"路线/区块准备异常: "+failure.getMessage()); }
    }
    public static void discard(ServerLevel level) { var job=JOBS.remove(level); if(job!=null) release(job); }
    public static void unload(ServerLevel level) { discard(level); EchoTerrainRevision.unload(level); }
    public static void stop() { List.copyOf(JOBS.keySet()).forEach(EchoRouteAdmission::discard); EchoTerrainRevision.stop(); tick=0; rotation=0; }
}
