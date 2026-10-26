package io.wifi.starrailexpress.settlement;

import com.google.gson.Gson;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.replay.*;
import io.wifi.starrailexpress.cca.*;
import io.wifi.starrailexpress.game.GameUtils;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/** 结算是停局后的独立会话，不把主对局状态切回 ACTIVE，也不生成真实玩家替身实体。 */
public final class SettlementManager {
    private static final Gson GSON = new Gson();
    private static final Map<UUID, Viewer> VIEWERS = new LinkedHashMap<>();
    private static SettlementStage stage;
    private static SettlementSnapshot snapshot;
    private static List<String> fragments = List.of();
    private static UUID session;
    private static boolean built;
    private static boolean registered;

    private SettlementManager() { }

    public static void register() {
        if (registered) return;
        registered = true;
        ServerPlayNetworking.registerGlobalReceiver(SettlementRequest.ID,
                (packet, context) -> context.server().execute(() -> request(context.player(), packet)));
        ServerTickEvents.END_SERVER_TICK.register(SettlementManager::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            Viewer viewer = VIEWERS.get(handler.player.getUUID());
            if (viewer != null) finish(server, viewer, true);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> stopAll(server));
    }

    /** 必须在胜负统计写完、职业和本局统计清空之前调用。失败则退回原有文字结算。 */
    public static SettlementSnapshot capture(ServerLevel world, SREGameRoundEndComponent result,
                                              SREGameWorldComponent game) {
        if (!SREConfig.instance().enableSettlementStage || result.players.isEmpty()
                || result.getWinStatus() == GameUtils.WinStatus.NONE
                || result.getWinStatus() == GameUtils.WinStatus.NO_PLAYER) return null;
        GameReplayManager replay = SRE.REPLAY_MANAGER;
        if (replay == null || replay.currentReplayData == null) return null;
        Map<UUID,Integer> tasks = new HashMap<>();
        List<SettlementSnapshot.Event> events = new ArrayList<>();
        List<GameReplayData.ReplayEvent> rawEvents = replay.currentReplayData.getTimeline();
        int rawIndex = 0;
        for (ReplayTimelineEvent event : replay.getTimelineEvents(true)) {
            GameReplayData.ReplayEvent raw = rawIndex < rawEvents.size() ? rawEvents.get(rawIndex) : null;
            rawIndex++;
            if (raw != null && raw.timestamp != event.timestamp()) raw = null;
            if (event.type() == ReplayEventTypes.EventType.TASK_COMPLETE && event.actor() != null)
                tasks.merge(event.actor().uuid(), 1, Integer::sum);
            // hidden 是回放的公开边界，不能在结算时泄漏内部诊断/私密事件。
            if (event.hidden()) continue;
            var actor = event.actor(); var target = event.target();
            Component text = event.text() == null
                    ? Component.translatable("sre.replay.event." + event.type().name().toLowerCase(Locale.ROOT)) : event.text();
            events.add(new SettlementSnapshot.Event(Math.max(0,event.relativeTimestamp()), event.type().name(),
                    actor == null ? null : actor.uuid(), target == null ? null : target.uuid(),
                    Component.Serializer.toJson(text, world.registryAccess()),
                    eventRole(raw,actor), changedRole(event,raw,target)));
        }
        if (events.isEmpty() || !events.getFirst().type().equals("GAME_START"))
            events.addFirst(new SettlementSnapshot.Event(0,"GAME_START",null,null,
                    Component.Serializer.toJson(Component.translatable("sre.replay.event.game_start"),world.registryAccess()),null,null));
        if (!events.getLast().type().equals("GAME_END")) {
            long start = ReplayDisplayUtils.findGameStartTime(replay.currentReplayData);
            long elapsed = start <= 0 ? events.getLast().time() : Math.max(0,System.currentTimeMillis()-start);
            events.add(new SettlementSnapshot.Event(elapsed,"GAME_END",null,null,
                    Component.Serializer.toJson(replay.currentReplayData.getWinningTitle(),world.registryAccess()),null,null));
        }
        List<SettlementSnapshot.Actor> actors = new ArrayList<>();
        for (var entry : result.players) {
            UUID id = entry.player().getId();
            SRERole role = game.getRole(id);
            if (role == null && !replay.currentReplayData.getPlayerRoles().containsKey(id)) continue;
            ServerPlayer online = world.getServer().getPlayerList().getPlayer(id);
            boolean winner = entry.hasWin() || (online != null
                    && game.getGameMode().isPlayerWinning(world, online, role, result, game));
            String roleId = role == null ? replay.currentReplayData.getInitialPlayerRoleId(id) : role.identifier().toString();
            actors.add(new SettlementSnapshot.Actor(id, entry.player().getName(), roleId == null ? "" : roleId,
                    role == null ? 0xFFD4AF37 : role.getColor(), winner, entry.wasDead(),
                    role != null && role.canUseKiller(), game.getPlayerKills(id), tasks.getOrDefault(id,0)));
        }
        UUID mvp = SettlementSnapshot.selectMvp(actors);
        if (mvp == null) return null;
        var config = SREConfig.instance();
        return new SettlementSnapshot(world.dimension().location().toString(), config.settlementStageX,
                config.settlementStageY, config.settlementStageZ,
                Component.Serializer.toJson(replay.currentReplayData.getWinningTitle(),world.registryAccess()),
                mvp, List.copyOf(actors), List.copyOf(events));
    }

    private static String eventRole(GameReplayData.ReplayEvent raw, ReplayPlayerProfile player) {
        if (player == null) return null;
        if (raw != null && raw.type == GameReplayData.EventType.CHANGE_ROLE && raw.message != null) {
            String[] change = raw.message.split("===",2);
            if (change.length == 2) return change[0];
        }
        if (raw != null && raw.roles != null && raw.roles.containsKey(player.uuid()))
            return raw.roles.get(player.uuid()).toString();
        return player.roleId();
    }

    private static String changedRole(ReplayTimelineEvent event, GameReplayData.ReplayEvent raw,
                                      ReplayPlayerProfile target) {
        if (event.type() == ReplayEventTypes.EventType.CHANGE_ROLE) {
            String message = raw == null ? event.data().get("message") : raw.message;
            if (message != null) {
                String[] change = message.split("===",2);
                if (change.length == 2) return change[1];
            }
        }
        return eventRole(raw,target);
    }


    /** 必须在原来的 resetPlayerAfterGame 和 INACTIVE 写回完成后调用。 */
    public static void begin(ServerLevel world, SettlementSnapshot frozen) {
        if (frozen == null) return;
        MinecraftServer server = world.getServer();
        stopAll(server);
        BlockPos origin = BlockPos.containing(frozen.x(), frozen.y(), frozen.z());
        if (origin.getY() < world.getMinBuildHeight() || origin.getY()+15 >= world.getMaxBuildHeight()
                || Math.abs((long)origin.getX()) > 29999000 || Math.abs((long)origin.getZ()) > 29999000
                || !world.getWorldBorder().isWithinBounds(origin.offset(-16,0,-16))
                || !world.getWorldBorder().isWithinBounds(origin.offset(16,0,16))) {
            SRE.LOGGER.error("结算舞台坐标无效或不在世界边界内，取消舞台演出: {}", origin);
            return;
        }
        // 不允许覆盖大厅/实战/地图模板；配置坐标是专用舞台区域，内部会被重建。
        AABB bounds = new AABB(Vec3.atLowerCornerOf(origin.offset(-16,0,-15)),
                Vec3.atLowerCornerOf(origin.offset(17,15,16)));
        var areas = AreasWorldComponent.KEY.get(world);
        if (bounds.intersects(areas.getReadyArea()) || bounds.intersects(areas.getPlayArea())
                || bounds.intersects(areas.getResetPasteArea()) || bounds.intersects(areas.getResetTemplateArea())) {
            SRE.LOGGER.error("结算舞台与大厅/游戏/地图模板重叠，已拒绝写入: {}", origin);
            return;
        }
        String json = GSON.toJson(frozen);
        if (json.length() > 24000*512) {
            SRE.LOGGER.error("结算快照超过12M字符，取消舞台以免截断回放");
            return;
        }
        List<String> parts = new ArrayList<>();
        for (int i=0; i<json.length();) {
            int end = Math.min(json.length(),i+24000);
            // 不在 UTF-16 代理对中间分片，避免含表情的名字/事件经 UTF-8 编码后变成问号。
            if (end < json.length() && Character.isHighSurrogate(json.charAt(end-1))) end--;
            parts.add(json.substring(i,end)); i=end;
        }
        if (parts.size() > 512) return;
        session = UUID.randomUUID(); snapshot = frozen; fragments = List.copyOf(parts);
        stage = new SettlementStage(world,origin); built = false;
        for (var actor : frozen.actors()) {
            ServerPlayer player = server.getPlayerList().getPlayer(actor.id());
            if (player == null || !ServerPlayNetworking.canSend(player,SettlementPayload.ID)) continue;
            Viewer viewer = new Viewer(player);
            VIEWERS.put(player.getUUID(),viewer);
            send(player,SettlementPayload.PREPARE,"");
        }
        syncBoarding(server);
        if (VIEWERS.isEmpty()) releaseStage();
    }

    private static void request(ServerPlayer player, SettlementRequest packet) {
        Viewer viewer = VIEWERS.get(player.getUUID());
        if (viewer == null || session == null || !session.equals(packet.session())) return;
        switch (packet.action()) {
            case SettlementRequest.BLACK -> viewer.state.blackReady();
            case SettlementRequest.READY -> {
                if (viewer.teleported && built && player.serverLevel() == stage.level
                        && player.position().distanceToSqr(seat()) < 9 && viewer.state.ready()) {
                    send(player,SettlementPayload.PLAY,"");
                }
            }
            case SettlementRequest.HOLD -> viewer.state.hold();
            case SettlementRequest.RELEASE -> viewer.state.release();
            case SettlementRequest.FINISHED -> {
                if (viewer.state.mayFinish(snapshot.durationTicks())) finish(player.getServer(),viewer,false);
            }
            default -> { }
        }
    }

    private static void tick(MinecraftServer server) {
        if (stage == null) return;
        if (GameUtils.isStartingGame || SREGameWorldComponent.KEY.get(stage.level).isRunning()
                || MapVotingComponent.KEY.get(stage.level).isVotingActive()) {
            stopAll(server); return;
        }
        try {
            if (!built && VIEWERS.values().stream().anyMatch(v -> v.state.phase() == SettlementState.Phase.LOADING)) {
                if (!stagePinned) stagePinned = stage.pinTick();
                if (stagePinned) built = stage.buildTick();
            }
            for (Viewer viewer : List.copyOf(VIEWERS.values())) {
                ServerPlayer player = server.getPlayerList().getPlayer(viewer.id);
                if (player == null) { finish(server,viewer,true); continue; }
                if (viewer.teleported && player.serverLevel() != stage.level) {
                    finish(server,viewer,false); continue;
                }
                if (viewer.state.tick(snapshot.durationTicks())) { finish(server,viewer,false); continue; }
                if (built && viewer.state.phase() == SettlementState.Phase.LOADING && !viewer.teleported) {
                    // 每名观众每 tick 最多 2 片，不能在大局结束时突发数 MB 网络流量。
                    int end = Math.min(fragments.size(),viewer.fragment+2);
                    for (; viewer.fragment<end; viewer.fragment++) ServerPlayNetworking.send(player,
                            new SettlementPayload(session,SettlementPayload.SNAPSHOT,viewer.fragment,
                                    fragments.size(),fragments.get(viewer.fragment)));
                    if (viewer.fragment < fragments.size()) continue;
                    player.removeVehicle(); player.setCamera(player);
                    player.setGameMode(GameType.SPECTATOR);
                    Vec3 seat = seat();
                    player.teleportTo(stage.level,seat.x,seat.y,seat.z,0,0);
                    player.setDeltaMovement(Vec3.ZERO);
                    viewer.teleported = true;
                    send(player,SettlementPayload.TELEPORTED,"");
                }
                if (viewer.teleported) {
                    player.setDeltaMovement(Vec3.ZERO); player.fallDistance = 0;
                    if (player.position().distanceToSqr(seat()) > 1)
                        player.teleportTo(stage.level,seat().x,seat().y,seat().z,0,0);
                }
            }
        } catch (RuntimeException exception) {
            SRE.LOGGER.error("结算舞台会话失败，恢复全部观众",exception);
            stopAll(server);
        }
    }

    private static boolean stagePinned;
    private static Vec3 seat() { return new Vec3(snapshot.x()+0.5,snapshot.y()+1,snapshot.z()-10.5); }
    private static void send(ServerPlayer player,int action,String data) {
        if (ServerPlayNetworking.canSend(player,SettlementPayload.ID))
            ServerPlayNetworking.send(player,new SettlementPayload(session,action,0,0,data));
    }

    /** 开局、地图投票、模式投票均同步调用，先恢复真人，再扫描登车名单。 */
    public static void stopAll(MinecraftServer server) {
        for (Viewer viewer : List.copyOf(VIEWERS.values())) finish(server,viewer,false);
        releaseStage();
    }
    public static boolean hasViewers() { return !VIEWERS.isEmpty(); }


    private static void finish(MinecraftServer server, Viewer viewer, boolean disconnected) {
        if (VIEWERS.remove(viewer.id) == null) return;
        viewer.state.close();
        ServerPlayer player = server.getPlayerList().getPlayer(viewer.id);
        if (player != null) {
            ServerLevel back = server.getLevel(viewer.returnDimension);
            if (back == null) back = server.overworld();
            Vec3 target = boardingReturn(back,viewer.position);
            player.setGameMode(viewer.mode);
            player.setCamera(player);
            player.teleportTo(back,target.x,target.y,target.z,viewer.yaw,viewer.pitch);
            player.setDeltaMovement(Vec3.ZERO); player.fallDistance = 0;
            if (!disconnected) send(player,SettlementPayload.STOP,"");
        }
        syncBoarding(server);
        if (VIEWERS.isEmpty()) releaseStage();
    }

    /** 返回原大厅落点；若原点不在登车区，则在准备区内寻找有地板且容得下玩家的位置。 */
    private static Vec3 boardingReturn(ServerLevel world,Vec3 fallback) {
        AABB area = AreasWorldComponent.KEY.get(world).getReadyArea();
        if (area.contains(fallback)) return fallback;
        Vec3 center = area.getCenter();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y=(int)Math.min(area.maxY-1,world.getMaxBuildHeight()-2); y>=Math.max(area.minY,world.getMinBuildHeight()+1)
                && y>=area.maxY-32; y--) {
            for (int x=-8;x<=8;x++) for (int z=-8;z<=8;z++) {
                pos.set((int)Math.floor(center.x)+x,y,(int)Math.floor(center.z)+z);
                Vec3 feet = new Vec3(pos.getX()+0.5,y,pos.getZ()+0.5);
                if (area.contains(feet) && world.getBlockState(pos.below()).isCollisionShapeFullBlock(world,pos.below())
                        && world.noCollision(new AABB(feet.x-0.3,y,feet.z-0.3,feet.x+0.3,y+1.8,feet.z+0.3))) return feet;
            }
        }
        return fallback;
    }

    private static void syncBoarding(MinecraftServer server) {
        if (server == null) return;
        for (ServerLevel world : server.getAllLevels())
            ParticipationComponent.KEY.get(world).setSettlementViewers(Set.copyOf(VIEWERS.keySet()));
    }
    private static void releaseStage() {
        if (stage != null) stage.close();
        stage = null; snapshot = null; fragments = List.of(); session = null; built = false; stagePinned = false;
    }

    private static final class Viewer {
        final UUID id;
        final SettlementState state = new SettlementState();
        final ResourceKey<Level> returnDimension;
        final Vec3 position;
        final float yaw,pitch;
        final GameType mode;
        boolean teleported;
        int fragment;
        Viewer(ServerPlayer player) {
            id=player.getUUID(); returnDimension=player.level().dimension(); position=player.position();
            yaw=player.getYRot(); pitch=player.getXRot(); mode=player.gameMode.getGameModeForPlayer();
        }
    }
}
