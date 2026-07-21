package io.wifi.starrailexpress.game;

import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.OnGameEnd;
import net.exmo.sre.subtitle.SubtitleCommand;
import net.exmo.sre.subtitle.SubtitleS2CPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.init.XiaoNaoHandler;

import java.util.*;

/**
 * 队友击杀违规检测处理器。
 * <p>
 * 在时间窗口内追踪每个玩家的队友击杀次数，
 * 当超过可配置的阈值时执行指定的 mcfunction。
 * 中立职业和手雷击杀不计入，OP 可豁免。
 */
public class TeamKillViolationHandler {

    /** 警告字幕的默认颜色（橙红） */
    private static final int WARN_COLOR = 0xFFFF6B35;
    /** 违规触发字幕的颜色（深红） */
    private static final int VIOLATION_COLOR = 0xFFFF3333;
    /** 字幕持续时间（tick，约 3 秒） */
    private static final int SUBTITLE_DURATION = 60;

    /** 玩家 UUID -> 时间窗口内的队友击杀时间戳队列（毫秒） */
    private static final Map<UUID, Deque<Long>> teamKillRecords = new HashMap<>();

    /**
     * 从XiaonaoHandler中调用避免误判
     * 
     * @param deathReason
     * @param isInnocent
     * @param killer
     * @param victim
     */
    public static void handle(ServerPlayer victim, ServerPlayer killer, boolean isInnocent,
            ResourceLocation deathReason) {

        SREConfig config = SREConfig.instance();

        // 未启用则跳过
        if (!config.teamKillViolationEnabled) {
            return;
        }

        // OP 豁免：拥有 OP 权限的玩家跳过违规检测
        if (killer.hasPermissions(1)) {
            return;
        }
        // 白名单检测小脑
        if (!XiaoNaoHandler.isXiaoNaoReason(deathReason))
            return;
        // 排除中立职业：击杀者或受害者任意一方为中立则不计入
        SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(victim.level());
        SRERole killerRole = gameWorldComponent.getRole(killer);
        SRERole victimRole = gameWorldComponent.getRole(victim);

        if (killerRole != null && killerRole.isNeutrals()) {
            return;
        }
        if (victimRole != null && victimRole.isNeutrals()) {
            return;
        }

        // 记录本次队友击杀
        long now = System.currentTimeMillis();
        UUID killerUuid = killer.getUUID();

        Deque<Long> records = teamKillRecords.computeIfAbsent(killerUuid, k -> new ArrayDeque<>());
        records.addLast(now);

        // 清理时间窗口之外的旧记录
        long windowMs = config.teamKillViolationWindowSeconds * 1000L;
        while (!records.isEmpty() && records.peekFirst() < now - windowMs) {
            records.pollFirst();
        }

        int currentCount = records.size();
        int threshold = config.teamKillViolationThreshold;

        // 达到阈值则触发
        if (currentCount >= threshold) {
            // 发送违规触发字幕
            sendSubtitle(killer,
                    Component.translatable("subtitle.sre.teamkill_violation")
                            .withStyle(ChatFormatting.BOLD),
                    Component.translatable("subtitle.sre.teamkill_punished", currentCount, threshold)
                            .withStyle(ChatFormatting.RED),
                    VIOLATION_COLOR);

            String mcFunction = config.teamKillViolationMcFunction;
            if (mcFunction != null && !mcFunction.isEmpty()) {
                GameUtils.executeFunction(
                        killer.createCommandSourceStack().withEntity(killer).withPermission(4),
                        mcFunction);
            }

            // 触发后清空记录，避免同一批次重复触发
            records.clear();
        } else {
            // 未达阈值：发送警告字幕，提示剩余次数
            int remaining = threshold - currentCount;
            sendSubtitle(killer,
                    Component.translatable("subtitle.sre.teamkill_warning")
                            .withStyle(ChatFormatting.BOLD),
                    Component.translatable("subtitle.sre.teamkill_remaining", currentCount, threshold, remaining)
                            .withStyle(ChatFormatting.GOLD),
                    WARN_COLOR);
        }

    }

    public static void registerEvent() {

        // 游戏结束时清空所有记录，保证每局独立统计
        OnGameEnd.EVENT.register((serverLevel, gameWorldComponent) -> {
            teamKillRecords.clear();
        });
    }

    /**
     * 向玩家发送底部字幕警告。
     */
    private static void sendSubtitle(ServerPlayer player, Component mainText, Component subText, int color) {
        SubtitleCommand.sendToPlayer(
                player, mainText, subText, SUBTITLE_DURATION, color, false,
                SubtitleS2CPayload.POS_BOTTOM);
    }

    /**
     * 清空所有玩家的队友击杀记录。
     * 可在游戏结束时调用，确保每局独立统计。
     */
    public static void clearAllRecords() {
        teamKillRecords.clear();
    }
}
