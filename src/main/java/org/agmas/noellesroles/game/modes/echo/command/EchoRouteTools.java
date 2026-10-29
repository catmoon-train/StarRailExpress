package org.agmas.noellesroles.game.modes.echo.command;

import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import org.agmas.noellesroles.game.modes.echo.map.*;
import org.agmas.noellesroles.game.modes.echo.runtime.EchoPreflight;
import java.io.IOException;
import java.util.*;

/** 全服至多两个只读路线任务；主线程小步轮转，结果不是永久开局授权。 */
public final class EchoRouteTools {
    private record Job(ServerLevel level,EchoMapEditState.Context context,EchoMapConfig source,String selection,long revision,boolean jumping,EchoRouteCheck check,long start) { }
    private static final Map<UUID,Job> JOBS=new LinkedHashMap<>();
    private static long tick,rotation;
    private EchoRouteTools() { }
    private static EchoMapEditState.Context context(ServerLevel level) throws IOException {
        var areas=AreasWorldComponent.KEY.get(level);
        if(areas.mapName==null||areas.mapName.isBlank()) throw new IOException("请先用现有Areas工具加载实战地图（当前未选择地图）");
        // 与开局预检一致：绑定优先，没有绑定按 Areas 地图名；绑定变化会让在途任务失效。
        String mapId=EchoActiveMap.effective(level.getServer().getWorldPath(LevelResource.ROOT),areas.mapName);
        return new EchoMapEditState.Context(level.dimension().location().toString(),mapId,EchoPreflight.offset(areas));
    }
    public static int start(CommandSourceStack source,String id) throws IOException {
        EchoMapTools.heavy(source);
        if(!(source.getEntity() instanceof ServerPlayer p)) throw new IOException("路线扫描只能由玩家启动");
        if(JOBS.containsKey(p.getUUID())) throw new IOException("已有路线任务；可用 map routes cancel 撤销");
        if(JOBS.size()>=2) throw new IOException("全服路线任务已达2个，请稍后重试");
        var map=EchoMapTools.selected(source,id); if(!EchoMapTools.validateWorld(source,map)) return 0;
        var binding=context(source.getLevel());
        var area=AreasWorldComponent.KEY.get(source.getLevel()).getPlayArea();
        long revision=EchoTerrainRevision.watch(source.getLevel(),new EchoMapConfig.Box(area.minX,area.minY,area.minZ,area.maxX,area.maxY,area.maxZ));
        JOBS.put(p.getUUID(),new Job(source.getLevel(),binding,map,id,revision,AreasWorldComponent.KEY.get(source.getLevel()).areasSettings.canJump,new EchoRouteCheck(source.getLevel(),map.translated(binding.offset())),tick));
        source.sendSuccess(() -> Component.translatable("echo.map.routes_started"),false); return 1;
    }
    public static int cancel(CommandSourceStack source) {
        org.agmas.noellesroles.game.modes.echo.runtime.EchoRouteAdmission.cancel(source.getLevel());
        if(source.getEntity() instanceof ServerPlayer p) disconnected(p.getUUID());
        source.sendSuccess(() -> Component.translatable("echo.map.routes_cancelled"),false); return 1;
    }
    public static void disconnected(UUID id) { JOBS.remove(id); }
    public static void unload(ServerLevel level) { JOBS.entrySet().removeIf(e -> e.getValue().level()==level); }
    public static void stop() { JOBS.clear(); tick=0; rotation=0; }
    public static void tick(MinecraftServer server) {
        tick++; if(JOBS.isEmpty()) return;
        var ids=List.copyOf(JOBS.keySet()); UUID id=ids.get((int)(rotation++%ids.size())); var job=JOBS.get(id); var p=server.getPlayerList().getPlayer(id);
        try {
            if(p==null || !p.hasPermissions(2) || p.serverLevel()!=job.level() || SREGameWorldComponent.KEY.get(job.level()).getGameStatus()!=SREGameWorldComponent.GameStatus.INACTIVE || !job.context().equals(context(job.level()))) {
                JOBS.remove(id); if(p!=null) p.sendSystemMessage(Component.translatable("echo.map.routes_cancelled")); return;
            }
            if(job.revision()!=EchoTerrainRevision.current(job.level()) || job.jumping()!=AreasWorldComponent.KEY.get(job.level()).areasSettings.canJump
                    || ((tick%20==0 || job.check().status()!=EchoRouteCheck.Status.RUNNING) && !job.source().equals(EchoMapTools.selected(p.createCommandSourceStack(),job.selection())))) {
                JOBS.remove(id); p.sendSystemMessage(Component.translatable("echo.map.routes_cancelled")); return;
            }
            long end=System.nanoTime()+2_000_000L;
            do { job.check().advance(16); } while(job.check().status()==EchoRouteCheck.Status.RUNNING && System.nanoTime()<end);
            if(job.check().status()!=EchoRouteCheck.Status.RUNNING || tick-job.start()>=1200) {
                if(!job.source().equals(EchoMapTools.selected(p.createCommandSourceStack(),job.selection()))) {
                    JOBS.remove(id); p.sendSystemMessage(Component.translatable("echo.map.routes_cancelled")); return;
                }
                JOBS.remove(id);
                String key=switch(job.check().status()) { case PASSED -> "echo.map.routes_passed"; case FAILED -> "echo.map.routes_failed"; default -> "echo.map.routes_manual"; };
                p.sendSystemMessage(Component.translatable(key,job.check().visited()));
                job.check().issues().stream().limit(12).forEach(issue -> p.sendSystemMessage(Component.translatable("echo.map.error",issue)));
            }
        } catch(IOException | RuntimeException failure) {
            JOBS.remove(id); org.agmas.noellesroles.Noellesroles.LOGGER.warn("回声路线诊断失败 {}",id,failure);
            if(p!=null) p.sendSystemMessage(Component.translatable("echo.map.routes_manual",job.check().visited()));
        }
    }
}
