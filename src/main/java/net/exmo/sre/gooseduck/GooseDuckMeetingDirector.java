package net.exmo.sre.gooseduck;

import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.content.vote.VoteManager;
import io.wifi.starrailexpress.content.vote.VoteOption;
import io.wifi.starrailexpress.content.vote.VoteSession;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import net.exmo.sre.meeting.MeetingApi;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import org.agmas.noellesroles.init.ModEffects;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 鹅鸭杀「会议 / 投票」编排器。
 * <p>
 * 会议本身由既有的 {@link net.exmo.sre.meeting 会议系统} 负责（右键尸体上报即召开），
 * 本类不改动会议内核，仅在游戏模式的服务端 tick 中<b>轮询</b>会议状态
 * （{@link MeetingApi#isMeetingActive()}）来做两件事：
 * <ol>
 *   <li><b>会议现场效果</b>：给全体参会者叠加 {@code ModEffects} 中的
 *       <b>2D 俯视视角</b>（{@link ModEffects#TWO_DIMENSIONAL_CAMERA} 等级 4 + 视距）
 *       以及若干辅助效果——<b>冻结技能</b>（{@link ModEffects#SKILL_FREEZED}，暂停技能 / 冷却 tick）、
 *       安全时间（{@link ModEffects#SAFE_TIME}）、心情免疫（{@link ModEffects#MOOD_DRAIN_IMMUNITY}）；</li>
 *   <li><b>放逐投票</b>：用既有的 {@link VoteManager 投票系统} 发起放逐投票，会议结束（或投票倒计时到）时
 *       结算票数，放逐得票唯一最高者，并公布其身份（鹅 / 鸭）。</li>
 * </ol>
 * 所有状态为静态、随每局 {@link #reset()} 归零。
 */
public final class GooseDuckMeetingDirector {
    private static final int EFFECT_DURATION_TICKS = 60;
    /** 2D 视角：4 = 上方俯视（2.5D），最适合围坐讨论的会议现场。 */
    private static final int TWO_D_OVERHEAD_AMPLIFIER = 4;
    /** 2D 视距：等级越高相机拉得越远，看清全场。 */
    private static final int TWO_D_DISTANCE_AMPLIFIER = 2;
    private static final String EJECT_PREFIX = "eject:";

    private static boolean meetingWasActive = false;
    private static boolean voteStarted = false;

    private GooseDuckMeetingDirector() {
    }

    /** 每局开始时归零。 */
    public static void reset() {
        meetingWasActive = false;
        voteStarted = false;
    }

    /** 由游戏模式服务端主循环每 tick 调用。 */
    public static void tick(ServerLevel level) {
        boolean active = MeetingApi.isMeetingActive();
        if (active) {
            boolean refresh = !voteStarted || level.getGameTime() % 20L == 0L;
            if (refresh) {
                for (ServerPlayer player : level.players()) {
                    if (MeetingApi.isParticipant(player.getUUID()) && GameUtils.isPlayerAliveAndSurvival(player)) {
                        applyMeetingEffects(player);
                    }
                }
            }
            if (!voteStarted) {
                startEjectVote(level);
                voteStarted = true;
            }
        } else if (meetingWasActive) {
            // 会议刚结束：清理现场效果，并结算尚未结束的投票（回调里放逐）。
            clearMeetingEffects(level);
            if (VoteManager.getCurrentSession() != null) {
                VoteManager.stopCurrentVote();
            }
            voteStarted = false;
        }
        meetingWasActive = active;
    }

    /** 游戏停止时兜底清理，避免效果 / 投票残留。 */
    public static void onGameStop(ServerLevel level) {
        clearMeetingEffects(level);
        if (VoteManager.getCurrentSession() != null) {
            VoteManager.stopCurrentVote();
        }
        reset();
    }

    private static void applyMeetingEffects(ServerPlayer player) {
        addEffect(player, ModEffects.TWO_DIMENSIONAL_CAMERA, TWO_D_OVERHEAD_AMPLIFIER);
        addEffect(player, ModEffects.TWO_DIMENSIONAL_CAMERA_DISTANCE, TWO_D_DISTANCE_AMPLIFIER);
        // 箱庭视野：以区块网格级的方块剔除精确移除玩家所在房间的屋顶 / 近墙，
        // 让 2D 俯视能直接俯瞰全场。它是 2D 视角遮挡剔除的正解——永不产生「一片虚空」
        // （未封闭房间会被判为 outside 从而完整渲染），取代早期脆弱的近裁剪面平面裁剪。
        addEffect(player, ModEffects.HAKONIWA_VISION, 0);
        addEffect(player, ModEffects.SKILL_FREEZED, 0);
        addEffect(player, ModEffects.SAFE_TIME, 0);
        addEffect(player, ModEffects.MOOD_DRAIN_IMMUNITY, 0);
    }

    private static void addEffect(ServerPlayer player, Holder<MobEffect> effect, int amplifier) {
        player.addEffect(new MobEffectInstance(effect, EFFECT_DURATION_TICKS, amplifier, false, false, false));
    }

    private static void clearMeetingEffects(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            player.removeEffect(ModEffects.TWO_DIMENSIONAL_CAMERA);
            player.removeEffect(ModEffects.TWO_DIMENSIONAL_CAMERA_DISTANCE);
            player.removeEffect(ModEffects.HAKONIWA_VISION);
            player.removeEffect(ModEffects.SKILL_FREEZED);
            player.removeEffect(ModEffects.SAFE_TIME);
            player.removeEffect(ModEffects.MOOD_DRAIN_IMMUNITY);
        }
    }

    private static void startEjectVote(ServerLevel level) {
        List<ServerPlayer> voters = new ArrayList<>();
        for (ServerPlayer player : level.players()) {
            if (MeetingApi.isParticipant(player.getUUID()) && GameUtils.isPlayerAliveAndSurvival(player)) {
                voters.add(player);
            }
        }
        if (voters.isEmpty()) {
            return;
        }
        VoteManager.VoteBuilder builder = VoteManager.builder(Component.translatable("vote.gooseduck.title"));
        for (ServerPlayer player : voters) {
            builder.addOption(VoteOption.player(player), EJECT_PREFIX + player.getUUID());
        }
        builder.addOption(VoteOption.text(Component.translatable("vote.gooseduck.skip")), "skip");
        int durationTicks = Math.max(200,
                AreasWorldComponent.KEY.get(level).areasSettings.meetingDiscussSeconds * 20);
        builder.duration(durationTicks)
                .allowReVote(true)
                .showResults(false)
                .targetPlayers(voters)
                .callback(session -> resolveVote(level, session))
                .start();
    }

    private static void resolveVote(ServerLevel level, VoteSession session) {
        List<Map.Entry<String, VoteSession.VoteResultOption>> tops = session.getTopResults();
        Component announcement;
        // 无票 / 平票 / 跳过 → 无人被放逐（getTopResults 在无票时会返回全部选项，size>1 视为平票）。
        if (tops.isEmpty() || tops.size() > 1) {
            announcement = Component.translatable("message.gooseduck.vote.tie").withStyle(ChatFormatting.GRAY);
        } else {
            String winnerId = tops.get(0).getKey();
            if (winnerId != null && winnerId.startsWith(EJECT_PREFIX)) {
                announcement = ejectPlayer(level, winnerId.substring(EJECT_PREFIX.length()));
            } else {
                announcement = Component.translatable("message.gooseduck.vote.skip").withStyle(ChatFormatting.GRAY);
            }
        }
        for (ServerPlayer player : level.players()) {
            player.sendSystemMessage(announcement);
        }
        // 结算后立即结束会议，把全员送回原位。
        if (MeetingApi.isMeetingActive()) {
            MeetingApi.endMeeting();
        }
    }

    private static Component ejectPlayer(ServerLevel level, String uuidString) {
        UUID uuid;
        try {
            uuid = UUID.fromString(uuidString);
        } catch (IllegalArgumentException e) {
            return Component.translatable("message.gooseduck.vote.skip").withStyle(ChatFormatting.GRAY);
        }
        ServerPlayer victim = level.getServer().getPlayerList().getPlayer(uuid);
        if (victim == null || !GameUtils.isPlayerAliveAndSurvival(victim)) {
            return Component.translatable("message.gooseduck.vote.skip").withStyle(ChatFormatting.GRAY);
        }
        var role = SREGameWorldComponent.KEY.get(level).getRole(victim);
        boolean wasDuck = role != null && role.canUseKiller();
        // 先摘掉会议期的安全时间，再强制放逐（forceKill 绕过会议死亡否决 / 护盾）。
        victim.removeEffect(ModEffects.SAFE_TIME);
        GameUtils.forceKillPlayer(victim, true, null, GameConstants.DeathReasons.GENERIC);
        String key = wasDuck ? "message.gooseduck.vote.ejected_duck" : "message.gooseduck.vote.ejected_goose";
        return Component.translatable(key, victim.getDisplayName())
                .withStyle(wasDuck ? ChatFormatting.GOLD : ChatFormatting.AQUA);
    }
}
