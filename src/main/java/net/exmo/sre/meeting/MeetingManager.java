/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package net.exmo.sre.meeting;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.api.AreasSettings;
import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.cca.SREGameTimeComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.content.block.MountableBlock;
import io.wifi.starrailexpress.content.block.entity.SeatEntity;
import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.content.vote.VoteManager;
import io.wifi.starrailexpress.content.vote.VoteOption;
import io.wifi.starrailexpress.content.vote.VoteSession;
import io.wifi.starrailexpress.content.vote.VoteSession.VoteResultOption;
import io.wifi.starrailexpress.event.AllowPlayerDeath;
import io.wifi.starrailexpress.event.AllowPlayerDeathWithKiller;
import io.wifi.starrailexpress.event.MeetingEndEvent;
import io.wifi.starrailexpress.event.MeetingStartEvent;
import io.wifi.starrailexpress.event.MeetingVoteEndEvent;
import io.wifi.starrailexpress.event.MeetingVoteOutEvent;
import io.wifi.starrailexpress.event.OnGameEnd;
import io.wifi.starrailexpress.event.OnMeetingStart;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMEntities;
import io.wifi.starrailexpress.util.TrueFalseResult;
import net.exmo.sre.meeting.network.MeetingSkipStateS2CPayload;
import net.exmo.sre.meeting.network.MeetingStateS2CPayload;
import net.exmo.sre.meeting.network.MeetingVoteResultS2CPayload;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.init.ModEffects;
import org.jetbrains.annotations.Nullable;
import pro.fazeclan.river.stupid_express.modifier.refugee.cca.RefugeeComponent;

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
import java.util.function.Consumer;

/**
 * 紧急会议系统（Among Us / 鹅鸭杀式），服务端核心。
 *
 * 由地图配置（{@link AreasSettings#meetingEnabled} 等字段，可在地图配置 GUI 的
 * 「会议」标签页编辑）启用。存活玩家右键尸体即召开会议：
 * <ol>
 * <li>全体存活玩家被传送至会议地点，系统自动搜寻周围的椅子
 * （{@link MountableBlock}）并让玩家就座，多余的人围成一圈站立；</li>
 * <li>开场阶段客户端播放环绕运镜与标题动画（见 {@code MeetingClientHandler}）；</li>
 * <li>讨论阶段为狼人杀式发言：只有按发言键「举手」的玩家才持有发言权，镜头自动对准
 * 发言者，允许多人同时举手；未举手的参会者语音会被静音（见 {@link #isVoiceMuted}），
 * 否则所有人都能出声、镜头也会在人群间反复跳变；</li>
 * <li>讨论期间禁止移动 / 攻击 / 技能，任何死亡一律否决；</li>
 * <li>时间到后全员原路返回。</li>
 * </ol>
 * 对外 API 见 {@link MeetingApi}。
 */
public final class MeetingManager {
    public static ResourceLocation DATA_STORAGE_ID = SRE.id("meeting_vote_results");
    /** 开场运镜时长（tick）。 */
    public static final int INTRO_TICKS = 70;

    public static final int PHASE_NONE = 0;
    public static final int PHASE_INTRO = 1;
    public static final int PHASE_DISCUSS = 2;
    public static final int PHASE_VOTE = 3;
    /** 投票阶段默认时长（秒） */
    public static final int VOTE_DURATION_SECONDS = 30;

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
    /**
     * 被静音的参会者快照：讨论阶段中未举手的人。
     * 由主线程在 {@link #refreshVoiceMuted()} 里整体替换为新的不可变集合，
     * svc 的语音线程只读它（{@link #isVoiceMuted}）—— 绝不能让语音线程直接碰
     * {@link #participants} / {@link #manualSpeakers} 这两个主线程集合。
     */
    private static volatile Set<UUID> voiceMuted = Set.of();
    /** 举手发言冷却：玩家 UUID → 可再次举手发言的游戏刻。 */
    private static final Map<UUID, Long> speakCooldownUntil = new HashMap<>();
    private static List<UUID> lastSyncedSpeakers = List.of();
    private static long cooldownUntilTick;
    private static long bellCooldownUntilTick;
    private static final Set<UUID> reportedBodies = new HashSet<>();
    /** 投票跳过会议的存活玩家（讨论/开场阶段可投）。 */
    private static final Set<UUID> skipVoters = new HashSet<>();
    /** 投票权重：玩家 UUID → 其投票算几票 */
    private static final Map<UUID, Integer> voteWeightOverrides = new HashMap<>();
    /** 被投票倍率：玩家 UUID → 他人投给该玩家的每一票在实际计票中按此倍率计算（显示仍为原始票数）。 */
    private static final Map<UUID, Double> receivedVoteMultipliers = new HashMap<>();
    private static boolean registered;

    private MeetingManager() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;

        // 【已禁用】右键尸体 → 召开会议（改用分号键上报），始终返回 PASS 避免干扰其他交互
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
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
            // 始终返回 PASS，保证原版钟的正常响声和动画
            tryBellMeeting(serverPlayer);
            return InteractionResult.PASS;
        });

        ServerTickEvents.END_SERVER_TICK.register(MeetingManager::tick);
        OnGameEnd.EVENT.register((serverLevel, gameWorldComponent) -> {
            endMeeting(true);
            reportedBodies.clear();
            cooldownUntilTick = 0;
            bellCooldownUntilTick = 0;
            speakCooldownUntil.clear();
            resetAllVoteWeights();
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID uuid = handler.player.getUUID();
            participants.remove(uuid);
            manualSpeakers.remove(uuid);
            refreshVoiceMuted();
        });

        // 会议期间否决一切非投票死亡（forceKill 除外）
        AllowPlayerDeath.EVENT.register((player, deathReason) -> !isActive());
        AllowPlayerDeathWithKiller.EVENT.register((victim, killer, deathReason) -> !isActive());
    }

    public static boolean isActive() {
        return phase != PHASE_NONE;
    }

    public static boolean isParticipant(UUID uuid) {
        return participants.containsKey(uuid);
    }

    /** 会议间冷却结束的游戏刻（供客户端 HUD 冷却提示同步，见 MeetingReportServerHandler）。 */
    public static long getCooldownUntilTick() {
        return cooldownUntilTick;
    }

    /** 已上报过的尸体 UUID 快照（每具尸体只能召开一次会议）。 */
    public static Set<UUID> getReportedBodies() {
        return Set.copyOf(reportedBodies);
    }

    public static void addReportedBody(UUID bodyUid) {
        if (bodyUid == null)
            return;
        reportedBodies.add(bodyUid);
    }

    /** 尸体被右键：满足条件则召开会议。返回是否已消费该交互。 */
    public static boolean tryReportBody(ServerPlayer reporter, PlayerBodyEntity body) {
        // SRE.LOGGER.info("[MEETING] Try report body");

        ServerLevel serverLevel = reporter.serverLevel();
        AreasSettings settings = settings(serverLevel);
        if (settings == null || !settings.meetingEnabled) {
            return false;
        }
        if (!GameUtils.isPlayerAliveAndSurvival(reporter)) {
            return false;
        }
        if (reportedBodies.contains(body.getUUID())) {
            // SRE.LOGGER.info("[MEETING] Body has already reported");

            return false;
        }
        String victim = body.getComponent().getOwnerName();
        if (victim == null || victim.isBlank()) {
            victim = body.getName().getString();
        }
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
        if (!startMeeting(serverLevel, ringer, null, true)) {
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
        return startMeeting(serverLevel, reporter, victim, false);
    }

    /**
     * 召开会议（可指定为紧急会议）。
     *
     * @param victim    被发现的尸体主人名，紧急按钮式会议传 null
     * @param emergency 紧急会议（如加拿大鹅死亡触发）：绕过开局冷却与会议间冷却，
     *                  确保由死亡触发的会议必定能召开
     */
    public static boolean startMeeting(ServerLevel serverLevel, ServerPlayer reporter, @Nullable String victim,
            boolean emergency) {
        // 亡命徒期间（难民触发）：无论如何都无法启用/发起会议
        if (RefugeeComponent.KEY.get(serverLevel).isAnyRevivals
                || SREGameWorldComponent.getInstance(serverLevel).isPsychoActive()
                || !SREGameWorldComponent.getInstance(serverLevel).isSkillAvailable) {
            reporter.displayClientMessage(
                    Component.translatable("meeting.sre.report_failed").withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (!SREGameWorldComponent.KEY.get(serverLevel).getGameMode().canHaveMeeting()) {
            return false;
        }
        if (OnMeetingStart.ALLOW_MEETING.invoker().allowMeeting(serverLevel, reporter, victim,
                emergency) == TrueFalseResult.FALSE) {
            return false;
        }
        skipVoters.clear();
        AreasSettings settings = settings(serverLevel);
        if (settings == null || !settings.meetingEnabled || isActive()) {
            return false;
        }
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(serverLevel);
        if (game == null || !game.isRunning()) {
            return false;
        }
        long now = serverLevel.getGameTime();
        if (!emergency && now < cooldownUntilTick) {
            // SRE.LOGGER.info("[MEETING] Cooldown: {} < {}", now, cooldownUntilTick);
            return false;
        }
        // 开局冷却：游戏开始后一段时间内不能召开会议（紧急会议绕过）。
        if (settings.meetingStartCooldown > 0) {
            SREGameTimeComponent timeComponent = SREGameTimeComponent.KEY.get(serverLevel);
            if (timeComponent != null) {
                long elapsed = Math.max(0, serverLevel.getGameTime() - timeComponent.getStartWorldTick());
                if (!emergency && elapsed < settings.meetingStartCooldown * 20L) {
                    // SRE.LOGGER.info("[MEETING] Cooldown: elapsed{} <
                    // settings.meetingStartCooldown*20 {}", elapsed,
                    // settings.meetingStartCooldown);
                    return false;
                }
            }
        }

        level = serverLevel;
        phase = PHASE_INTRO;
        phaseEndTick = now + INTRO_TICKS;
        center = new Vec3(settings.meetingPosition.x, settings.meetingPosition.y, settings.meetingPosition.z);
        if (settings.meetingNoReporter) {
            reporterName = "meeting.sre.subtitle.a_player";
        } else {
            reporterName = reporter.getGameProfile().getName();
        }
        victimName = victim == null ? "" : victim;
        participants.clear();
        seatEntityIds.clear();
        manualSpeakers.clear();
        speakCooldownUntil.clear();
        lastSyncedSpeakers = List.of();

        List<ServerPlayer> alive = new ArrayList<>(serverLevel.getServer().getPlayerList().getPlayers()).stream()
                .filter(GameUtils::isPlayerAliveAndSurvival)
                .toList();
        List<BlockPos> chairs = scanChairs(serverLevel, settings);

        int index = 0;
        for (ServerPlayer participant : alive) {
            participants.put(participant.getUUID(), new ReturnPos(
                    participant.getX(), participant.getY(), participant.getZ(),
                    participant.getYRot(), participant.getXRot()));
            participant.stopSleeping();
            participant.stopRiding();

            if (index < chairs.size() && seatOnChair(serverLevel, participant, chairs.get(index))) {
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
            participant.removeEffect(MobEffects.INVISIBILITY);
            participant.removeEffect(MobEffects.GLOWING);
            participant.addEffect(new MobEffectInstance(ModEffects.MOVE_BANED, -1, 0, false, false, false));
            participant.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, -1, 0, false, false, false));
            participant.addEffect(new MobEffectInstance(ModEffects.USED_BANED, -1, 0, false, false, false));
            participant.addEffect(new MobEffectInstance(ModEffects.SKILL_BANED, -1, 0, false, false, false));
            participant
                    .addEffect(new MobEffectInstance(ModEffects.SKILL_FREEZED, -1, 0, false, false, false));
            participant.addEffect(new MobEffectInstance(ModEffects.CCA_FREEZED, -1, 0, false, false, false));

            index++;
        }

        MeetingVoice.joinAll(participants.keySet(), serverLevel.getServer());
        final var timecca = SREGameTimeComponent.KEY.get(serverLevel);
        timecca.setTimeFrozen(true, true);
        for (ServerPlayer player : serverLevel.getServer().getPlayerList().getPlayers()) {
            player.playNotifySound(SoundEvents.BELL_BLOCK, SoundSource.MASTER, 1.0F, 0.8F);
        }
        broadcastState(serverLevel);
        broadcastSkipState(serverLevel);
        MeetingStartEvent.EVENT.invoker().onMeetingStart(serverLevel, reporter);
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
            participant.removeEffect(ModEffects.SKILL_FREEZED);
            participant.removeEffect(ModEffects.CCA_FREEZED);
            participant.removeEffect(MobEffects.NIGHT_VISION);
            if (!participant.isSpectator()) {
                ReturnPos pos = entry.getValue();
                participant.teleportTo(serverLevel, pos.x(), pos.y(), pos.z(), Set.of(), pos.yaw(), pos.pitch());
                participant.setDeltaMovement(Vec3.ZERO);
                participant.fallDistance = 0.0F;
            }
        }
        final var timecca = SREGameTimeComponent.KEY.get(serverLevel);
        timecca.setTimeFrozen(false, true);
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
        skipVoters.clear();
        lastSyncedSpeakers = List.of();
        if (!silent) {
            for (ServerPlayer player : serverLevel.getServer().getPlayerList().getPlayers()) {
                player.playNotifySound(SoundEvents.BELL_BLOCK, SoundSource.MASTER, 0.8F, 1.2F);
            }
        }
        broadcastState(serverLevel);
        MeetingEndEvent.EVENT.invoker().onMeetingEnd(serverLevel);
        level = null;
    }

    // ==================== 跳过会议 ====================

    /**
     * 客户端「跳过会议」按钮点击（可再次点击取消）。
     * 仅会议进行中（开场 / 讨论阶段）且为参会者时生效；达到「超过半数存活玩家」阈值则跳过会议。
     */
    public static void setSkipVote(ServerPlayer player, boolean skip) {
        if (!isActive() || level == null) {
            return;
        }
        // 仅开场 / 讨论阶段可投跳过；投票阶段已不可跳过
        if (phase != PHASE_INTRO && phase != PHASE_DISCUSS) {
            return;
        }
        UUID uuid = player.getUUID();
        if (!participants.containsKey(uuid)) {
            return;
        }
        if (skip) {
            skipVoters.add(uuid);
        } else {
            skipVoters.remove(uuid);
        }
        ServerLevel serverLevel = level;
        broadcastSkipState(serverLevel);
        // 超过二分之一的存活玩家投了跳过 → 跳过会议（有投票则直接进入投票阶段）
        long alive = new ArrayList<>(serverLevel.getServer().getPlayerList().getPlayers()).stream()
                .filter(GameUtils::isPlayerAliveAndSurvival)
                .count();
        if (alive > 0 && skipVoters.size() > alive / 2) {
            skipMeeting(serverLevel);
        }
    }

    /** 跳过会议：直接进入投票阶段（若地图启用投票），否则直接结束会议。 */
    private static void skipMeeting(ServerLevel serverLevel) {
        skipVoters.clear();
        AreasSettings settings = settings(serverLevel);
        if (settings != null && settings.meetingVoteEnabled) {
            startVotingPhase(serverLevel);
        } else {
            endMeeting(false);
        }
    }

    /** 向全体玩家同步跳过计票状态。 */
    private static void broadcastSkipState(ServerLevel serverLevel) {
        long alive = new ArrayList<>(serverLevel.getServer().getPlayerList().getPlayers()).stream()
                .filter(GameUtils::isPlayerAliveAndSurvival)
                .count();
        MeetingSkipStateS2CPayload payload = new MeetingSkipStateS2CPayload(skipVoters.size(), (int) alive);
        for (ServerPlayer player : serverLevel.getServer().getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    // ==================== 发言 ====================

    /** 发言键 / GUI 触发。举手（开始发言）有冷却，放下发言不受限。 */
    public static void setManualSpeaking(ServerPlayer player, boolean speaking) {
        if (!isActive() || phase != PHASE_DISCUSS || !participants.containsKey(player.getUUID())) {
            return;
        }
        UUID uuid = player.getUUID();
        if (speaking) {
            // 仅在「举手」的上升沿判定冷却；持续按住不会重复触发。
            if (manualSpeakers.contains(uuid)) {
                return;
            }
            long now = player.level().getGameTime();
            long until = speakCooldownUntil.getOrDefault(uuid, 0L);
            if (now < until) {
                int remainSeconds = (int) Math.ceil((until - now) / 20.0);
                player.displayClientMessage(
                        Component.translatable("meeting.speak.cooldown", remainSeconds)
                                .withStyle(ChatFormatting.GRAY),
                        true);
                return;
            }
            manualSpeakers.add(uuid);
            AreasSettings settings = settings(player.serverLevel());
            int cooldownSeconds = settings != null ? settings.meetingSpeakCooldownSeconds : 5;
            if (cooldownSeconds > 0) {
                speakCooldownUntil.put(uuid, now + cooldownSeconds * 20L);
            }
        } else {
            manualSpeakers.remove(uuid);
        }
        refreshVoiceMuted();
    }

    /**
     * 重算 {@link #voiceMuted}。只在服务端主线程调用；发布的是不可变副本，
     * 语音线程读到的要么是旧集合要么是新集合，不会看到半个集合。
     */
    private static void refreshVoiceMuted() {
        if (phase != PHASE_DISCUSS) {
            voiceMuted = Set.of();
            return;
        }
        Set<UUID> muted = new HashSet<>(participants.keySet());
        muted.removeAll(manualSpeakers);
        voiceMuted = Set.copyOf(muted);
    }

    /**
     * 讨论阶段中该玩家是否应被静音（未举手发言）。
     * 由 svc 的 {@code MicrophonePacketEvent} 在语音线程调用（见 {@code TrainVoicePlugin}），
     * 因此只读 {@link #voiceMuted} 这份不可变快照。
     */
    public static boolean isVoiceMuted(UUID uuid) {
        return voiceMuted.contains(uuid);
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
            List<UUID> speakers = currentSpeakers();
            if (!speakers.equals(lastSyncedSpeakers) && now % 3 == 0) {
                lastSyncedSpeakers = speakers;
                broadcastState(serverLevel);
            }
            if (now >= phaseEndTick) {
                AreasSettings s = settings(serverLevel);
                if (s != null && s.meetingVoteEnabled) {
                    startVotingPhase(serverLevel);
                } else {
                    endMeeting(false);
                }
            }
        }
        if (phase == PHASE_VOTE) {
            if (VoteManager.getCurrentSession() == null && now >= phaseEndTick) {
                endMeeting(false);
            }
        }
    }

    /** 当前持有发言权的玩家（举手者）。顺序随 {@link #manualSpeakers} 的插入序稳定，镜头不会因排序抖动。 */
    private static List<UUID> currentSpeakers() {
        return List.copyOf(manualSpeakers);
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
            if ((state.getBlock()) instanceof MountableBlock) {
                chairs.add(pos.immutable());
            }
        }
        chairs.sort(Comparator.comparingDouble(pos -> pos.distSqr(centerPos)));
        return chairs;
    }

    /** 在椅子上生成临时座位实体并让玩家就座（复刻 MountableBlock 的坐下逻辑）。 */
    private static boolean seatOnChair(ServerLevel serverLevel, ServerPlayer participant, BlockPos chairPos) {
        BlockState state = serverLevel.getBlockState(chairPos);
        if (!(state.getBlock() instanceof MountableBlock mountable)) {
            return false;
        }
        // 传送到椅子旁再上座，避免跨房间 startRiding 失败
        Vec3 chairCenter = chairPos.getCenter();
        float yaw = (float) (Math.atan2(center.z - chairCenter.z, center.x - chairCenter.x) * 180.0 / Math.PI)
                - 90.0F;
        participant.teleportTo(serverLevel, chairCenter.x, chairCenter.y + 0.6, chairCenter.z, Set.of(), yaw, 10.0F);

        SeatEntity seat = TMMEntities.SEAT.create(serverLevel);
        if (seat == null) {
            return false;
        }
        Vec3 sitPos = mountable.getSitPos(serverLevel, state, chairPos);
        Vec3 vec3d = Vec3.atLowerCornerOf(chairPos).add(sitPos);

        seat.moveTo(vec3d.x, vec3d.y, vec3d.z, 0, 0);
        seat.setSeatPos(chairPos);
        serverLevel.addFreshEntity(seat);
        participant.startRiding(seat, true);
        seatEntityIds.add(seat.getId());
        return true;
    }

    // ==================== 同步 ====================

    private static void broadcastState(ServerLevel serverLevel) {
        // 每次状态变化（开会 / 换阶段 / 举手变动 / 散会）都同步刷新语音静音名单。
        refreshVoiceMuted();
        MeetingStateS2CPayload payload = new MeetingStateS2CPayload(
                phase, center.x, center.y, center.z, phaseEndTick,
                reporterName, victimName,
                List.copyOf(participants.keySet()),
                phase == PHASE_DISCUSS ? currentSpeakers() : List.of());
        for (ServerPlayer player : serverLevel.getServer().getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    @Nullable
    private static AreasSettings settings(ServerLevel serverLevel) {
        AreasWorldComponent component = AreasWorldComponent.KEY.get(serverLevel);
        return component == null ? null : component.areasSettings;
    }

    // ==================== 投票阶段 ====================

    /** "跳过"选项的 resultId 常量。 */
    private static final String SKIP_RESULT_ID = "meeting_skip";

    private static CompoundTag getVoteOptionCompoundTag(VoteResultOption optresult, ServerLevel serverWorld) {
        var ttag = new CompoundTag();
        var opt = optresult.option();
        ttag.putInt("id", optresult.id());
        ttag.putInt("count", optresult.count());
        ttag.putString("rid", opt.resultId());
        ttag.putString("display", Component.Serializer.toJson(opt.display(), serverWorld.registryAccess()));
        ttag.putString("type", opt.typeId().toString());
        if (opt.isItem() && opt instanceof VoteOption.ItemOption ito) {
            ttag.put("item", ito.stack().save(serverWorld.registryAccess()));
        } else if (opt.isPlayer() && opt instanceof VoteOption.PlayerOption ito) {
            var player_info_tag = new CompoundTag();
            player_info_tag.putUUID("id",
                    ito.uuid());
            player_info_tag.putString("display_name",
                    ito.display().getString());
            ttag.put("player", player_info_tag);
        }
        return ttag;
    }

    /** 开始投票阶段：创建玩家投票 Session，投票结束时按新规则处理出局。 */
    private static void startVotingPhase(ServerLevel serverLevel) {
        phase = PHASE_VOTE;
        phaseEndTick = serverLevel.getGameTime() + VOTE_DURATION_SECONDS * 20L;
        List<ServerPlayer> alive = new ArrayList<>(serverLevel.getServer().getPlayerList().getPlayers()).stream()
                .filter(GameUtils::isPlayerAliveAndSurvival)
                .toList();
        if (alive.size() <= 1) {
            endMeeting(false);
            return;
        }

        List<VoteOption> options = new ArrayList<>();
        // 添加"跳过"选项在最前面
        options.add(VoteOption.text(
                Component.translatable("meeting.vote.skip"), SKIP_RESULT_ID));

        for (ServerPlayer p : alive) {
            options.add(new VoteOption.PlayerOption(p.getName(), p.getUUID()));
        }
        Set<UUID> targetPlayers = new HashSet<>();
        for (ServerPlayer p : alive)
            targetPlayers.add(p.getUUID());

        // ==================== 投票结束时按新规则处理 ====================
        Consumer<VoteSession> callback = session -> {
            String expelledName = "";

            // 第一步：统计所有选项的票数（实际计票应用"被投票倍率"，如呆呆鸟每票按 1.5 计）
            var results = session.getResults();
            Map<String, Double> effectiveVotes = new HashMap<>();
            double maxVotes = 0;
            for (var entry : results.entrySet()) {
                double effective = entry.getValue().count();
                if (entry.getValue().option() instanceof VoteOption.PlayerOption po) {
                    effective *= getReceivedVoteMultiplier(po.uuid());
                }
                effectiveVotes.put(entry.getKey(), effective);
                maxVotes = Math.max(maxVotes, effective);
            }

            // 第二步：找出所有达到最高票的选项
            List<String> topResultIds = new ArrayList<>();
            for (var entry : effectiveVotes.entrySet()) {
                if (maxVotes > 1.0E-6 && Math.abs(entry.getValue() - maxVotes) < 1.0E-6) {
                    topResultIds.add(entry.getKey());
                }
            }
            // 结束会议
            endMeeting(false);
            // 第三步：判定出局者
            // 只有当最高票唯一、且不是"跳过"、且是玩家时，才驱逐
            if (topResultIds.size() == 1 && !topResultIds.get(0).equals(SKIP_RESULT_ID)) {
                String resultId = topResultIds.get(0);
                for (VoteOption opt : session.getOptions()) {
                    if (opt.resultId().equals(resultId) && opt instanceof VoteOption.PlayerOption po) {
                        UUID votedOut = po.uuid();
                        ServerPlayer target = serverLevel.getServer().getPlayerList().getPlayer(votedOut);
                        if (target != null && GameUtils.isPlayerAliveAndSurvival(target)) {
                            final var areaCCA = AreasWorldComponent.getInstance(serverLevel);
                            final AreasSettings areasSettings = areaCCA.areasSettings;
                            if (MeetingVoteOutEvent.EVENT.invoker().onVoteOut(serverLevel, target)) {
                                switch (areasSettings.meetingVoteProcessor) {
                                    case FUNCTION: {
                                        var tag = new CompoundTag();
                                        var tag_results = new CompoundTag();
                                        var tag_top_results = new CompoundTag();
                                        {
                                            // 存储所有results
                                            for (var entry : session.getResults().entrySet()) {
                                                tag_results.put(entry.getKey(),
                                                        getVoteOptionCompoundTag(entry.getValue(), serverLevel));
                                            }
                                        }
                                        {
                                            // 存储获胜者

                                            // 存储所有results
                                            var tag_top_result_entries = new ListTag();
                                            for (var entry : session.getTopResults()) {
                                                tag_top_result_entries
                                                        .add(getVoteOptionCompoundTag(entry.getValue(), serverLevel));
                                            }
                                            tag_top_results.put("entries", tag_top_result_entries);
                                        }
                                        {
                                            tag.put("results", tag_results);
                                            tag.put("tops", tag_top_results);
                                        }
                                        serverLevel.getServer().getCommandStorage().set(DATA_STORAGE_ID, tag);

                                        if (areasSettings.meetingVoteProcessorFunction != null
                                                && !areasSettings.meetingVoteProcessorFunction.isBlank())
                                            GameUtils.executeFunction(serverLevel.getServer(),
                                                    SREConfig.instance().meetingVoteProcessorFunctionPermission,
                                                    areasSettings.meetingVoteProcessorFunction);
                                    }
                                        break;
                                    case GLOWING:
                                        target.addEffect(ModEffects.of(MobEffects.GLOWING,
                                                areasSettings.meetingVoteProcessorGlowingTime * 20, 1, false, true,
                                                true));
                                        break;
                                    case KILL:
                                        GameUtils.killPlayer(target, false, null,
                                                GameConstants.DeathReasons.VOTED_OUT);
                                        break;
                                    case FORCE_KILL:
                                        GameUtils.forceKillPlayer(target, false, null,
                                                GameConstants.DeathReasons.VOTED_OUT);
                                        break;
                                    default:
                                        break;
                                }
                                expelledName = target.getGameProfile().getName();
                            }
                        }
                        break;
                    }
                }
            }

            // 收集投票结果（含跳过票数）
            List<MeetingVoteResultS2CPayload.VoteEntry> entries = new ArrayList<>();
            for (var entry : results.entrySet()) {
                String playerName = entry.getValue().option().display().getString();
                int count = entry.getValue().count();
                entries.add(new MeetingVoteResultS2CPayload.VoteEntry(playerName, count));
            }

            // 广播投票结果给所有玩家
            MeetingVoteResultS2CPayload resultPayload = new MeetingVoteResultS2CPayload(expelledName, entries);
            for (ServerPlayer player : serverLevel.getServer().getPlayerList().getPlayers()) {
                ServerPlayNetworking.send(player, resultPayload);
            }
            MeetingVoteEndEvent.EVENT.invoker().onVoteOver(serverLevel, session);
        };

        // ==================== 开始投票 ====================
        VoteManager.builder(Component.translatable("meeting.vote.title"))
                .options(options).duration(VOTE_DURATION_SECONDS * 20).allowReVote(true)
                .showResults(true).syncInterval(20).targetPlayerUUIDs(targetPlayers)
                .maxSelect(1).type("meeting").callback(callback).start();
        broadcastState(serverLevel);
    }

    // ==================== 投票权重 ====================

    /** 设置指定玩家的投票权重（覆盖）。默认权重为 1。 */
    public static void setVoteWeight(ServerPlayer player, int weight) {
        voteWeightOverrides.put(player.getUUID(), weight);
    }

    /** 为指定玩家增加投票权重（加算）。如传教士给政客 2 票加成 → 2+2=4。 */
    public static void addVoteWeight(ServerPlayer player, int addedWeight) {
        addVoterWeight(player.getUUID(), addedWeight);
    }

    /** UUID 版加算投票权重。传教士切换目标时用负数恢复旧目标权重。 */
    public static void addVoterWeight(UUID uuid, int addedWeight) {
        int current = voteWeightOverrides.getOrDefault(uuid, 1);
        voteWeightOverrides.put(uuid, current + addedWeight);
    }

    /** 获取指定玩家的投票权重（含存活人数规则）。无覆盖时返回 1。 */
    public static int getVoteWeight(ServerPlayer player) {
        return getVoterWeight(player.getUUID());
    }

    /** UUID 版：获取投票权重（无覆盖返回 1，含存活人数规则）。 */
    public static int getVoterWeight(UUID uuid) {
        int weight = voteWeightOverrides.getOrDefault(uuid, 1);
        if (weight >= 2 && level != null) {
            long alive = level.getServer().getPlayerList().getPlayers().stream()
                    .filter(GameUtils::isPlayerAliveAndSurvival).count();
            if (alive > 24)
                weight = Math.max(weight, 3);
        }
        return weight;
    }

    /** 重置指定玩家的投票权重。 */
    public static void resetVoteWeight(ServerPlayer player) {
        voteWeightOverrides.remove(player.getUUID());
    }

    /** UUID 版：重置投票权重。 */
    public static void resetVoterWeight(UUID uuid) {
        voteWeightOverrides.remove(uuid);
    }

    /** 重置所有投票权重（游戏结束时调用）。 */
    public static void resetAllVoteWeights() {
        voteWeightOverrides.clear();
        receivedVoteMultipliers.clear();
    }

    // ==================== 被投票倍率 ====================

    /**
     * 设置指定玩家的"被投票倍率"：他人投给该玩家的每一票，在实际计票中按此倍率计算。
     * 投票结果界面显示的仍是原始票数。默认倍率为 1.0。
     * 例如呆呆鸟为 1.5 → 投给呆呆鸟的 1 票实际算 1.5 票。
     */
    public static void setReceivedVoteMultiplier(ServerPlayer player, double multiplier) {
        receivedVoteMultipliers.put(player.getUUID(), multiplier);
    }

    /** UUID 版：获取"被投票倍率"（无覆盖返回 1.0）。 */
    public static double getReceivedVoteMultiplier(UUID uuid) {
        return receivedVoteMultipliers.getOrDefault(uuid, 1.0);
    }

    /** 重置指定玩家的"被投票倍率"。 */
    public static void resetReceivedVoteMultiplier(ServerPlayer player) {
        receivedVoteMultipliers.remove(player.getUUID());
    }
}
