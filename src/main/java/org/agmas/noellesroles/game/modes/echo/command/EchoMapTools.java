package org.agmas.noellesroles.game.modes.echo.command;

import org.agmas.noellesroles.game.modes.echo.rules.EchoRosterPolicy;
import org.agmas.noellesroles.game.modes.echo.map.EchoMapValidator;
import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.agmas.noellesroles.game.modes.echo.map.*;
import org.agmas.noellesroles.game.modes.echo.runtime.EchoPreflight;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

/** OP 地图编辑适配；会话、目标与世界检查全在服务器线程，绝不直接修改地图建筑。 */
public final class EchoMapTools {
    private record Session(ServerLevel level,EchoMapEditState state) { }
    private static final Map<UUID,Session> SESSIONS=new HashMap<>();
    private static final Map<UUID,Long> HEAVY=new HashMap<>();
    private static long tick;
    private EchoMapTools() { }
    @FunctionalInterface public interface Action { int run() throws IOException; }
    public static int run(CommandSourceStack source,Action action) {
        if(!source.hasPermission(2)) return 0;
        try { return action.run(); }
        catch(IOException | IllegalArgumentException failure) {
            source.sendFailure(Component.translatable("echo.map.error",failure.getMessage())); return 0;
        }
    }
    private static ServerPlayer player(CommandSourceStack source) throws IOException {
        if(source.getEntity() instanceof ServerPlayer player) return player;
        throw new IOException("此编辑操作只能由玩家执行");
    }
    private static void idle(ServerLevel level) throws IOException {
        if(SREGameWorldComponent.KEY.get(level).getGameStatus()!=SREGameWorldComponent.GameStatus.INACTIVE) throw new IOException("仅允许在非对局、非准备阶段使用地图工具");
    }
    private static EchoMapEditState.Context context(ServerLevel level,String mapId) throws IOException {
        requireAreasMap(level);
        try { EchoMapRepository.requireMapId(mapId); }
        catch(IOException invalid) { throw new IOException("非法的回声地图ID: "+mapId); }
        return new EchoMapEditState.Context(level.dimension().location().toString(),mapId,EchoPreflight.offset(AreasWorldComponent.KEY.get(level)));
    }

    /** 当前生效的回声地图：switchmap 绑定优先，没有绑定按 Areas 地图名。 */
    private static String effectiveMapId(ServerLevel level) throws IOException {
        requireAreasMap(level);
        String mapId=EchoActiveMap.effective(world(level),AreasWorldComponent.KEY.get(level).mapName);
        try { EchoMapRepository.requireMapId(mapId); }
        catch(IOException invalid) { throw new IOException("当前回声地图名不是合法mapId: "+mapId); }
        return mapId;
    }

    private static void requireAreasMap(ServerLevel level) throws IOException {
        var areas=AreasWorldComponent.KEY.get(level);
        if(areas.mapName==null || areas.mapName.isBlank()) throw new IOException("请先用现有Areas工具加载实战地图（当前未选择地图）");
    }
    private static Path world(ServerLevel level) { return level.getServer().getWorldPath(LevelResource.ROOT); }
    private static EchoMapEditState edit(CommandSourceStack source) throws IOException {
        var p=player(source); idle(p.serverLevel()); var s=SESSIONS.get(p.getUUID());
        if(s==null) throw new IOException("没有编辑会话；先执行 map new 或 map edit");
        // 会话维度/偏移用草稿自己的 mapId 比对：切换绑定不应误杀在途编辑。
        if(s.level()!=p.serverLevel() || !s.state().valid(context(p.serverLevel(),s.state().context().mapId()),tick)) {
            SESSIONS.remove(p.getUUID()); throw new IOException("编辑会话因切图、偏移变化或超时失效");
        }
        s.state().touch(tick); return s.state();
    }
    static void heavy(CommandSourceStack source) throws IOException {
        var p=player(source); Long previous=HEAVY.get(p.getUUID());
        if(previous!=null && tick-previous<20) throw new IOException("请间隔1秒再执行地图检查/预览/保存");
        HEAVY.put(p.getUUID(),tick);
    }
    public static int begin(CommandSourceStack source,String id,boolean fresh) throws IOException {
        var p=player(source); idle(p.serverLevel()); EchoMapRepository.requireMapId(id);
        var context=context(p.serverLevel(),id);
        if(SESSIONS.containsKey(p.getUUID())) throw new IOException("已有草稿，请先保存或 map cancel；不会自动覆盖内存编辑");
        if(SESSIONS.size()>=16) throw new IOException("全服编辑会话已达16个");
        var draft=fresh?EchoMapDraft.empty(id):new EchoMapDraft(EchoMapRepository.load(world(p.serverLevel()),id));
        SESSIONS.put(p.getUUID(),new Session(p.serverLevel(),new EchoMapEditState(context,draft,tick)));
        source.sendSuccess(() -> Component.translatable("echo.map.editing",id,fresh?"new":"edit"),false);
        if(!id.equals(AreasWorldComponent.KEY.get(p.serverLevel()).mapName))
            source.sendSuccess(() -> Component.translatable("echo.switchmap.load.template_warning",id,AreasWorldComponent.KEY.get(p.serverLevel()).mapName),false);
        return 1;
    }
    public static int cancel(CommandSourceStack source) throws IOException {
        SESSIONS.remove(player(source).getUUID()); source.sendSuccess(() -> Component.translatable("echo.map.cancelled"),false); return 1;
    }
    private static BlockPos ray(ServerPlayer player) throws IOException {
        var hit=player.pick(16,0,false);
        if(!(hit instanceof BlockHitResult block) || hit.getType()!=HitResult.Type.BLOCK || !player.serverLevel().hasChunkAt(block.getBlockPos())) throw new IOException("请瞄准16格内已加载的方块");
        return block.getBlockPos();
    }
    public static int mark(CommandSourceStack source,String kind,String id,int minPlayers) throws IOException {
        var state=edit(source); var p=player(source); EchoMapConfig.Vec point;
        if(kind.endsWith("_spawn") || Set.of("spectator","free_safe","evacuation_safe","drop_anchor").contains(kind)) point=new EchoMapConfig.Vec(p.getX(),p.getY(),p.getZ());
        else { var pos=org.agmas.noellesroles.game.modes.echo.content.EchoMachineInteractions.root(p.serverLevel(),ray(p)); point=new EchoMapConfig.Vec(pos.getX()+0.5,pos.getY()+0.5,pos.getZ()+0.5); }
        state.draft().mark(kind,id,state.template(point),p.getYRot(),p.getXRot(),minPlayers);
        source.sendSuccess(() -> Component.translatable("echo.map.changed",kind,id,state.draft().revision()),false); return 1;
    }
    public static int corner(CommandSourceStack source,boolean first) throws IOException {
        var state=edit(source); var pos=ray(player(source)); state.corner(first,new EchoMapConfig.Vec(pos.getX(),pos.getY(),pos.getZ()));
        source.sendSuccess(() -> Component.translatable("echo.map.corner",first?"a":"b",pos.toShortString()),false); return 1;
    }
    public static int volume(CommandSourceStack source,String kind,String id) throws IOException {
        var state=edit(source); state.draft().volume(kind,id,state.selection());
        source.sendSuccess(() -> Component.translatable("echo.map.changed",kind,id,state.draft().revision()),false); return 1;
    }
    public static int remove(CommandSourceStack source,String field,String id) throws IOException {
        var state=edit(source); state.draft().remove(field,id);
        source.sendSuccess(() -> Component.translatable("echo.map.changed",field,id,state.draft().revision()),false); return 1;
    }
    public static int list(CommandSourceStack source) throws IOException {
        var state=edit(source); var m=state.draft().snapshot();
        source.sendSuccess(() -> Component.translatable("echo.map.summary",m.mapId(),state.draft().revision(),m.carrierSpawns().size(),m.interceptorSpawns().size(),m.brokerSpawns().size()),false);
        summary(source,"beacons",m.beacons().stream().map(EchoMapConfig.Beacon::id).toList());
        summary(source,"crystalNodes",m.crystalNodes().stream().map(EchoMapConfig.Node::id).toList());
        summary(source,"altars",m.altars().stream().map(EchoMapConfig.Point::id).toList());
        summary(source,"supplyCrates",m.supplyCrates().stream().map(EchoMapConfig.Crate::id).toList());
        summary(source,"blackCrystalNodes",m.blackCrystalNodes().stream().map(EchoMapConfig.Point::id).toList());
        summary(source,"blackDropoffs",m.blackDropoffs().stream().map(EchoMapConfig.Point::id).toList());
        summary(source,"doors",m.coreGate().doors().stream().map(EchoMapConfig.Door::id).toList()); return 1;
    }
    private static void summary(CommandSourceStack source,String field,List<String> ids) {
        source.sendSuccess(() -> Component.translatable("echo.map.list",field,ids.size(),String.join(", ",ids.stream().limit(24).toList())+(ids.size()>24?" …":"")),false);
    }
    public static EchoMapConfig selected(CommandSourceStack source,String id) throws IOException {
        idle(source.getLevel());
        if(id==null && source.getEntity() instanceof ServerPlayer p && SESSIONS.containsKey(p.getUUID())) return edit(source).draft().snapshot();
        // 世界检查按偏移平移，与侧文件名解耦；不给 id 时用当前生效（绑定或 Areas 同名）地图。
        String selected=id==null?effectiveMapId(source.getLevel()):id; EchoMapRepository.requireMapId(selected);
        return EchoMapRepository.load(world(source.getLevel()),selected);
    }
    public static boolean validateWorld(CommandSourceStack source,EchoMapConfig map) { return validateWorld(source,map,false); }
    /** quiet 用于 random 之类的静默重试：失败只返回值，不刷屏。 */
    public static boolean validateWorld(CommandSourceStack source,EchoMapConfig map,boolean quiet) {
        for(int count=EchoRosterPolicy.MIN_PLAYERS;count<=EchoMapValidator.validationPlayerLimit(map);count++) {
            var check=EchoPreflight.check(source.getLevel(),count,false,map);
            if(!check.valid()) {
                if(!quiet) check.errors().stream().limit(12).forEach(source::sendFailure);
                return false;
            }
        }
        return true;
    }
    public static int validate(CommandSourceStack source,String id) throws IOException {
        heavy(source); if(!validateWorld(source,selected(source,id))) return 0;
        source.sendSuccess(() -> Component.translatable("echo.command.map_valid"),false); return 1;
    }
    public static int save(CommandSourceStack source,String id,String nonce) throws IOException {
        var state=edit(source); heavy(source); var map=state.draft().exported(id);
        if(!id.equals(state.context().mapId())) throw new IOException("保存mapId必须与当前Areas地图一致");
        if(!validateWorld(source,map)) return 0;
        Path root=world(source.getLevel()); String digest=EchoMapRepository.digest(root,id);
        if(nonce!=null) digest=state.consume(UUID.fromString(nonce),id,digest,tick);
        else if(!digest.isEmpty()) {
            var token=state.confirm(id,digest,tick);
            String command="/sre:echo map save "+id+" confirm "+token.nonce();
            source.sendSuccess(() -> Component.translatable("echo.map.confirm",command).withStyle(style -> style.withClickEvent(new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.SUGGEST_COMMAND,command))),false); return 1;
        }
        EchoMapRepository.save(root,map,digest); state.saved();
        source.sendSuccess(() -> Component.translatable("echo.map.saved",id),false); return 1;
    }
    public static int preview(CommandSourceStack source,String id) throws IOException {
        heavy(source); var p=player(source); var map=selected(source,id).translated(EchoPreflight.offset(AreasWorldComponent.KEY.get(source.getLevel())));
        var payload=EchoMapPreviewPayload.from(source.getLevel().dimension().location(),map);
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(p,payload);
        source.sendSuccess(() -> Component.translatable("echo.map.preview",payload.entries().size()),false); return 1;
    }
    public static int reload(CommandSourceStack source) {
        source.sendSuccess(() -> Component.translatable("echo.map.reload"),false); return 1;
    }
    /**
     * 用 {@link AreasWorldComponent#gameModes} 声明 / 取消声明回响模式。
     * 声明后立即写回地图配置，否则重启或换图就丢了，等于没限制。
     */
    public static int gate(CommandSourceStack source, Boolean declared) throws IOException {
        var level = source.getLevel();
        idle(level);
        var areas = AreasWorldComponent.KEY.get(level);
        if (areas.mapName == null || areas.mapName.isBlank()) throw new IOException("当前没有加载任何Areas地图");
        if (declared != null) {
            if (EchoMapGate.setSupported(areas, declared)) areas.sync();
            if (!io.wifi.starrailexpress.game.MapManager.saveCurrentMap(level, areas.mapName, true))
                throw new IOException("地图配置写回失败：" + areas.mapName);
        }
        source.sendSuccess(() -> Component.literal(EchoMapGate.describe(areas)), false);
        return 1;
    }
    public static void disconnected(UUID player) { SESSIONS.remove(player); HEAVY.remove(player); }
    public static void unload(ServerLevel level) { SESSIONS.entrySet().removeIf(e -> e.getValue().level()==level); }
    public static void stop() { SESSIONS.clear(); HEAVY.clear(); tick=0; }
    public static void tick(MinecraftServer server) {
        tick++; if(tick%20!=0) return;
        SESSIONS.entrySet().removeIf(entry -> {
            var p=server.getPlayerList().getPlayer(entry.getKey()); var session=entry.getValue();
            if(p==null || !p.hasPermissions(2) || p.serverLevel()!=session.level()) return true;
            try { idle(session.level()); return !session.state().valid(context(session.level(),session.state().context().mapId()),tick); }
            catch(IOException | IllegalArgumentException invalid) { return true; }
        });
        HEAVY.entrySet().removeIf(e -> tick-e.getValue()>1200);
    }
}
