package net.exmo.sre.meeting;

import io.wifi.starrailexpress.api.AreasSettings;
import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.content.block.MountableBlock;
import io.wifi.starrailexpress.content.block.entity.SeatEntity;
import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.event.AllowPlayerDeath;
import io.wifi.starrailexpress.event.AllowPlayerDeathWithKiller;
import io.wifi.starrailexpress.event.OnGameEnd;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMEntities;
import io.wifi.starrailexpress.util.BlockTypeChecker;
import net.exmo.sre.meeting.network.MeetingStateS2CPayload;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.init.ModEffects;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 紧急会议系统（Among Us / 鹅鸭杀式），服务端核心。
 *
 * 由地图配置（{@link AreasSettings#meetingEnabled} 等字段，可在地图配置 GUI 的
 * 「会议」标签页编辑）启用。存活玩家右键尸体即召开会议：
 * <ol>
 * <li>全体存活玩家被传送至会议地点，系统自动搜寻周围的椅子
 * （{@link MountableBlock}）并让玩家就座，多余的人围成一圈站立；</li>
 * <li>开场阶段客户端播放环绕运镜与标题动画（见 {@code MeetingClientHandler}）；</li>
 * <li>讨论阶段为狼人杀式发言：按发言键 / 在聊天栏说话 / 使用语音（svc）都会把
 * 自己标记为「发言中」，镜头自动对准发言者，允许多人同时发言；</li>
 * <li>讨论期间禁止移动 / 攻击 / 技能，任何死亡一律否决；</li>
 * <li>时间到后全员原路返回。</li>
 * </ol>
 * 对外 API 见 {@link MeetingApi}。
 */
public final class MeetingManager {

    /** 开场运镜时长（tick）。 */
    public static final int INTRO_TICKS = 70;
    /** 聊天发言的“发言中”标记保持时长（tick）。 */
    private static final int CHAT_SPEAK_TICKS = 80;
    /** 语音活动的“发言中”标记保持时长（tick）。 */
    private static final int VOICE_SPEAK_TICKS = 15;

    public static final int PHASE_NONE = 0;
    public static final int PHASE_INTRO = 1;
    public static final int PHASE_DISCUSS = 2;

    private record ReturnPos(double x, double y, double z, float yaw, float pitch) {
    }

    private static ServerLevel level;
    private static int phase = PHASE_NONE;
    private static long phaseEndTick;
    private static Vec3 center = Vec3.ZERO;
    private static String reporterName = "";
    private static String victimName = "";
    private static final Map<UUID, ReturnPos> participants = new LinkedHashMap<>();
    private static final List<Integer> seatEntityIds = new ArrayList<>();
    private static final Set<UUID> manualSpeakers = new LinkedHashSet<>();
    private static final Map<UUID, Long> transientSpeakers = new HashMap<>();
    private static List<UUID> lastSyncedSpeakers = List.of();
    private static long cooldownUntilTick;
    private static long bellCooldownUntilTick;
    private static final Set<UUID> reportedBodies = new HashSet<>();
    private static boolean registered;

    private MeetingManager() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;

        // 右键尸体 → 召开会议（优先于打开尸体物品栏）
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClientSide() || !(entity instanceof PlayerBodyEntity body)
                    || !(player instanceof ServerPlayer reporter)) {
                return InteractionResult.PASS;
            }
            if (tryReportBody(reporter, body)) {
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        });

        // 右键钟方块 → 摇铃召开会议
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
                return InteractionResult.PASS;
            }
            BlockState state = world.getBlockState(hitResult.getBlockPos());
            if (!state.is(Blocks.BELL)) {
                return InteractionResult.PASS;
            }
            if (tryBellMeeting(serverPlayer)) {
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        });

        ServerTickEvents.END_SERVER_TICK.register(MeetingManager::tick);
        OnGameEnd.EVENT.register((serverLevel, gameWorldComponent) -> {
            endMeeting(true);
            reportedBodies.clear();
            cooldownUntilTick = 0;
            bellCooldownUntilTick = 0;
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID uuid = handler.player.getUUID();
            participants.remove(uuid);
            manualSpeakers.remove(uuid);
            transientSpeakers.remove(uuid);
        });

        // 会议期间否决一切死亡（forceKill 除外）
        AllowPlayerDeath.EVENT.register((player, deathReason) -> !isActive());
        AllowPlayerDeathWithKiller.EVENT.register((victim, killer, deathReason) -> !isActive());

        // 聊天栏发言 → 标记为发言中（消息照常放行）
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> {
            if (isActive() && phase == PHASE_DISCUSS && participants.containsKey(sender.getUUID())) {
                transientSpeakers.put(sender.getUUID(),
                        sender.level().getGameTime() + CHAT_SPEAK_TICKS);
            }
            return true;
        });
    }

    public static boolean isActive() {
        return phase != PHASE_NONE;
    }

    public static boolean isParticipant(UUID uuid) {
        return participants.containsKey(uuid);
    }

    /** 尸体被右键：满足条件则召开会议。返回是否已消费该交互。 */
    public static boolean tryReportBody(ServerPlayer reporter, PlayerBodyEntity body) {
        ServerLevel serverLevel = reporter.serverLevel();
        AreasSettings settings = settings(serverLevel);
        if (settings == null || !settings.meetingEnabled) {
            return false;
        }
        if (!GameUtils.isPlayerAliveAndSurvival(reporter)) {
            return false;
        }
        if (reportedBodies.contains(body.getUUID())) {
            return false;
        }
        String victim = body.getName().getString();
        UUID owner = body.getPlayerUuid();
        if (owner != null) {
            ServerPlayer ownerPlayer = reporter.server.getPlayerList().getPlayer(owner);
            if (ownerPlayer != null) {
                victim = ownerPlayer.getGameProfile().getName();
            }
        }
        if (!startMeeting(serverLevel, reporter, victim)) {
            return false;
        }
        reportedBodies.add(body.getUUID());
        return true;
    }

    /** 右键钟方块摇铃：满足条件则召开会议。返回是否已消费该交互。 */
    public static boolean tryBellMeeting(ServerPlayer ringer) {
        ServerLevel serverLevel = ringer.serverLevel();
        AreasSettings settings = settings(serverLevel);
        if (settings == null || !settings.meetingEnabled || !settings.bellMeetingEnabled) {
            return false;
        }
        if (!GameUtils.isPlayerAliveAndSurvival(ringer)) {
            return false;
        }
        long now = serverLevel.getGameTime();
        // 首次摇铃：设置开局冷却
        if (bellCooldownUntilTick == 0) {
            bellCooldownUntilTick = now + settings.bellMeetingStartCooldown * 20L;
        }
        if (now < bellCooldownUntilTick) {
            return false;
        }
        if (!startMeeting(serverLevel, ringer, null)) {
            return false;
        }
        bellCooldownUntilTick = now + settings.bellMeetingCooldown * 20L;
        return true;
    }

    /**
     * 召开会议。冷却中 / 已在会议中 / 未启用 / 游戏未运行时返回 false。
     *
     * @param victim 被发现的尸体主人名，紧急按钮式会议传 null
     */
    public static boolean startMeeting(ServerLevel serverLevel, ServerPlayer reporter, @Nullable String victim) {
        AreasSettings settings = settings(serverLevel);
        if (settings == null || !settings.meetingEnabled || isActive()) {
            return false;
        }
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(serverLevel);
        if (game == null || !game.isRunning()) {
            return false;
        }
        long now = serverLevel.getGameTime();
        if (now < cooldownUntilTick) {
            return false;
        }

        level = serverLevel;
        phase = PHASE_INTRO;
        phaseEndTick = now + INTRO_TICKS;
        center = new Vec3(settings.meetingPosition.x, settings.meetingPosition.y, settings.meetingPosition.z);
        reporterName = reporter.getGameProfile().getName();
        victimName = victim == null ? "" : victim;
        participants.clear();
        seatEntityIds.clear();
        manualSpeakers.clear();
        transientSpeakers.clear();
        lastSyncedSpeakers = List.of();

        List<ServerPlayer> alive = serverLevel.players().stream()
                .filter(GameUtils::isPlayerAliveAndSurvival)
                .toList();
        int totalDuration = INTRO_TICKS + settings.meetingDiscussSeconds * 20 + 40;
        List<BlockPos> chairs = scanChairs(serverLevel, settings);

        int index = 0;
        for (ServerPlayer participant : alive) {
            participants.put(participant.getUUID(), new ReturnPos(
                    participant.getX(), participant.getY(), participant.getZ(),
                    participant.getYRot(), participant.getXRot()));
            participant.stopSleeping();
            participant.stopRiding();

            if (index < chairs.size()) {
                seatOnChair(serverLevel, participant, chairs.get(index));
            } else {
                // 没有椅子的玩家围成一圈站立
                int standIndex = index - chairs.size();
                double angle = Math.PI * 2.0 * standIndex / Math.max(1, alive.size() - chairs.size());
                double x = center.x + Math.cos(angle) * 3.5;
                double z = center.z + Math.sin(angle) * 3.5;
                float yaw = (float) (Math.atan2(center.z - z, center.x - x) * 180.0 / Math.PI) - 90.0F;
                participant.teleportTo(serverLevel, x, center.y, z, Set.of(), yaw, 10.0F);
            }
            participant.setDeltaMovement(Vec3.ZERO);
            participant.fallDistance = 0.0F;

            participant.addEffect(new MobEffectInstance(ModEffects.MOVE_BANED, totalDuration, 0, false, false, false));
            participant.addEffect(new MobEffectInstance(ModEffects.USED_BANED, totalDuration, 0, false, false, false));
            participant.addEffect(new MobEffectInstance(ModEffects.SKILL_BANED, totalDuration, 0, false, false, false));
            index++;
        }

        MeetingVoice.joinAll(participants.keySet(), serverLevel.getServer());

        for (ServerPlayer player : serverLevel.players()) {
            player.playNotifySound(SoundEvents.BELL_BLOCK, SoundSource.MASTER, 1.0F, 0.8F);
        }
        broadcastState(serverLevel);
        return true;
    }

    /** 结束会议：全员返回原位、清除限制、拆除临时座位。 */
    public static void endMeeting(boolean silent) {
        if (!isActive() || level == null) {
            return;
        }
        ServerLevel serverLevel = level;
        phase = PHASE_NONE;
        AreasSettings settings = settings(serverLevel);
        cooldownUntilTick = serverLevel.getGameTime()
                + (settings != null ? settings.meetingCooldownSeconds : 60) * 20L;

        for (Map.Entry<UUID, ReturnPos> entry : participants.entrySet()) {
            ServerPlayer participant = serverLevel.getServer().getPlayerList().getPlayer(entry.getKey());
            if (participant == null) {
                continue;
            }
            participant.stopRiding();
            participant.removeEffect(ModEffects.MOVE_BANED);
            participant.removeEffect(ModEffects.USED_BANED);
            participant.removeEffect(ModEffects.SKILL_BANED);
            if (!participant.isSpectator()) {
                ReturnPos pos = entry.getValue();
                participant.teleportTo(serverLevel, pos.x(), pos.y(), pos.z(), Set.of(), pos.yaw(), pos.pitch());
                participant.setDeltaMovement(Vec3.ZERO);
                participant.fallDistance = 0.0F;
            }
        }
        for (int entityId : seatEntityIds) {
            var entity = serverLevel.getEntity(entityId);
            if (entity instanceof SeatEntity) {
                entity.ejectPassengers();
                entity.discard();
            }
        }
        MeetingVoice.leaveAll(participants.keySet(), serverLevel.getServer());

        participants.clear();
        seatEntityIds.clear();
        manualSpeakers.clear();
        transientSpeakers.clear();
        lastSyncedSpeakers = List.of();
        if (!silent) {
            for (ServerPlayer player : serverLevel.players()) {
                player.playNotifySound(SoundEvents.BELL_BLOCK, SoundSource.MASTER, 0.8F, 1.2F);
            }
        }
        broadcastState(serverLevel);
        level = null;
    }

    // ==================== 发言 ====================

    /** 发言键 / GUI 触发。 */
    public static void setManualSpeaking(ServerPlayer player, boolean speaking) {
        if (!isActive() || phase != PHASE_DISCUSS || !participants.containsKey(player.getUUID())) {
            return;
        }
        if (speaking) {
            manualSpeakers.add(player.getUUID());
        } else {
            manualSpeakers.remove(player.getUUID());
        }
    }

    /** svc 语音活动回调（见 TrainVoicePlugin 的 MicrophonePacketEvent 挂钩）。 */
    public static void onVoiceActivity(UUID uuid) {
        if (!isActive() || phase != PHASE_DISCUSS || !participants.containsKey(uuid) || level == null) {
            return;
        }
        transientSpeakers.put(uuid, level.getGameTime() + VOICE_SPEAK_TICKS);
    }

    // ==================== Tick ====================

    private static void tick(MinecraftServer server) {
        if (!isActive() || level == null) {
            return;
        }
        ServerLevel serverLevel = level;
        long now = serverLevel.getGameTime();

        if (phase == PHASE_INTRO && now >= phaseEndTick) {
            AreasSettings settings = settings(serverLevel);
            phase = PHASE_DISCUSS;
            phaseEndTick = now + (settings != null ? settings.meetingDiscussSeconds : 60) * 20L;
            broadcastState(serverLevel);
            return;
        }
        if (phase == PHASE_DISCUSS) {
            transientSpeakers.entrySet().removeIf(entry -> now >= entry.getValue());
            List<UUID> speakers = currentSpeakers();
            if (!speakers.equals(lastSyncedSpeakers) && now % 3 == 0) {
                lastSyncedSpeakers = speakers;
                broadcastState(serverLevel);
            }
            if (now >= phaseEndTick) {
                endMeeting(false);
            }
        }
    }

    private static List<UUID> currentSpeakers() {
        List<UUID> speakers = new ArrayList<>(manualSpeakers);
        for (UUID uuid : transientSpeakers.keySet()) {
            if (!speakers.contains(uuid)) {
                speakers.add(uuid);
            }
        }
        return speakers;
    }

    // ==================== 场景构建 ====================

    /** 搜寻会议点周围的椅子，按与中心的距离排序。 */
    private static List<BlockPos> scanChairs(ServerLevel serverLevel, AreasSettings settings) {
        AABB scanBox = settings.meetingChairScanBox.toAABB();
        BlockPos centerPos = BlockPos.containing(settings.meetingPosition.x, settings.meetingPosition.y,
                settings.meetingPosition.z);
        List<BlockPos> chairs = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(
                centerPos.offset((int) scanBox.minX, (int) scanBox.minY, (int) scanBox.minZ),
                centerPos.offset((int) scanBox.maxX, (int) scanBox.maxY, (int) scanBox.maxZ))) {
            BlockState state = serverLevel.getBlockState(pos);
            if (BlockTypeChecker.isSeatBlock(state.getBlock())) {
                chairs.add(pos.immutable());
            }
        }
        chairs.sort(Comparator.comparingDouble(pos -> pos.distSqr(centerPos)));
        return chairs;
    }

    /** 在椅子上生成临时座位实体并让玩家就座（复刻 MountableBlock 的坐下逻辑）。 */
    private static void seatOnChair(ServerLevel serverLevel, ServerPlayer participant, BlockPos chairPos) {
        BlockState state = serverLevel.getBlockState(chairPos);
        if (!(state.getBlock() instanceof MountableBlock mountable)) {
            return;
        }
        // 传送到椅子旁再上座，避免跨房间 startRiding 失败
        Vec3 chairCenter = chairPos.getCenter();
        float yaw = (float) (Math.atan2(center.z - chairCenter.z, center.x - chairCenter.x) * 180.0 / Math.PI)
                - 90.0F;
        participant.teleportTo(serverLevel, chairCenter.x, chairCenter.y + 0.6, chairCenter.z, Set.of(), yaw, 10.0F);

        SeatEntity seat = TMMEntities.SEAT.create(serverLevel);
        if (seat == null) {
            return;
        }
        Vec3 sitPos = mountable.getSitPos(serverLevel, state, chairPos);
        Vec3 target = chairCenter.add(sitPos);
        seat.moveTo(target.x, target.y, target.z, yaw, 0);
        seat.setSeatPos(chairPos);
        serverLevel.addFreshEntity(seat);
        participant.startRiding(seat, true);
        seatEntityIds.add(seat.getId());
    }

    // ==================== 同步 ====================

    private static void broadcastState(ServerLevel serverLevel) {
        MeetingStateS2CPayload payload = new MeetingStateS2CPayload(
                phase, center.x, center.y, center.z, phaseEndTick,
                reporterName, victimName,
                List.copyOf(participants.keySet()),
                phase == PHASE_DISCUSS ? currentSpeakers() : List.of());
        for (ServerPlayer player : serverLevel.players()) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    @Nullable
    private static AreasSettings settings(ServerLevel serverLevel) {
        AreasWorldComponent component = AreasWorldComponent.KEY.get(serverLevel);
        return component == null ? null : component.areasSettings;
    }
}
