package org.agmas.noellesroles.client.hud.roles;

import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role_data.neutral.DaywalkerRoleData;

import java.util.Locale;

/**
 * 逐日者 HUD（右下角自绘）：
 * <li>逐日时刻储存数值</li>
 * <li>任务完成进度</li>
 * <li>快进时间（每完成一个任务使局内计时每秒快进的数值）</li>
 * <li>技能冷却时间（客户端倒计时）</li>
 */
public final class DaywalkerHud {
    /** 角色主题色（橙金色） */
    private static final int THEME_COLOR = 0xFFFF9C00;
    private static final int DIM_COLOR = 0xFFCC8800;
    private static final int COOLDOWN_COLOR = 0xFFAAAAAA;
    private static final int READY_COLOR = 0xFF7CFC00;

    private DaywalkerHud() {
    }

    public static void register() {
        RoleHudRenderCallback.EVENT.register(ModRoles.DAYWALKER_ID, (context, tickCounter) -> {
            if (SREClient.isPlayerSpectator())
                return;
            var player = Minecraft.getInstance().player;
            if (player == null)
                return;
            var dataOpt = RoleData.getOptional(DaywalkerRoleData.class, player);
            if (dataOpt.isEmpty())
                return;
            DaywalkerRoleData data = dataOpt.get();

            int x = context.guiWidth() - 180;
            int y = context.guiHeight() - 55;
            var font = Minecraft.getInstance().font;

            // 逐日时刻储存数值
            int momentCap = data.hasLeader ? DaywalkerRoleData.MAX_MOMENTS_WITH_LEADER
                    : DaywalkerRoleData.MAX_MOMENTS;
            context.drawString(font,
                    Component.translatable("hud.noellesroles.daywalker.moments",
                            data.storedMoments, momentCap),
                    x, y, THEME_COLOR);

            // 任务完成进度
            context.drawString(font,
                    Component.translatable("hud.noellesroles.daywalker.tasks",
                            data.completedTasks, data.requiredTasks),
                    x, y + 11, DIM_COLOR);

            // 快进时间（每完成一个任务使局内计时器 1 秒的现实流逝时间减少 0.075 秒，累计当前速率与倍速）
            context.drawString(font,
                    Component.translatable("hud.noellesroles.daywalker.fast_forward",
                            formatRate(data.getFastForwardRate()),
                            formatMultiplier(data.getFastForwardRate())),
                    x, y + 22, DIM_COLOR);

            // 技能冷却时间（客户端倒计时）
            if (data.skillCooldownTicks > 0) {
                context.drawString(font,
                        Component.translatable("hud.noellesroles.daywalker.cooldown",
                                (data.skillCooldownTicks + 19) / 20),
                        x, y + 33, COOLDOWN_COLOR);
            } else if (data.storedMoments > 0) {
                context.drawString(font,
                        Component.translatable("hud.noellesroles.daywalker.skill_ready"),
                        x, y + 33, READY_COLOR);
            } else {
                context.drawString(font,
                        Component.translatable("hud.noellesroles.daywalker.skill_no_moments"),
                        x, y + 33, COOLDOWN_COLOR);
            }

            // 终末追逐提示
            if (data.finalChaseActive) {
                context.drawString(font,
                        Component.translatable("hud.noellesroles.daywalker.final_chase"),
                        x, y - 11, THEME_COLOR);
            }
        });
    }

    /** 快进速率格式化：最多 3 位小数并去掉多余的 0（0.075 / 0.15 / 0.6） */
    private static String formatRate(double rate) {
        if (rate <= 0.0) {
            return "0";
        }
        String text = String.format(Locale.ROOT, "%.3f", rate);
        if (text.contains(".")) {
            while (text.endsWith("0")) {
                text = text.substring(0, text.length() - 1);
            }
            if (text.endsWith(".")) {
                text = text.substring(0, text.length() - 1);
            }
        }
        return text;
    }

    /**
     * 当前倍速：局内 1 秒 = 现实 (1 - 速率) 秒，故倍速 = 1 / (1 - 速率)。
     * 速率 0 时为 1 倍速；0.6 时为 2.5 倍速。
     */
    private static String formatMultiplier(double rate) {
        if (rate <= 0.0 || rate >= 1.0) {
            return "1";
        }
        double multiplier = 1.0 / (1.0 - rate);
        String text = String.format(Locale.ROOT, "%.2f", multiplier);
        if (text.contains(".")) {
            while (text.endsWith("0")) {
                text = text.substring(0, text.length() - 1);
            }
            if (text.endsWith(".")) {
                text = text.substring(0, text.length() - 1);
            }
        }
        return text;
    }
}
