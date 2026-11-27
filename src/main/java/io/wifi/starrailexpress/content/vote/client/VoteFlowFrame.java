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

package io.wifi.starrailexpress.content.vote.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.wifi.starrailexpress.client.ui.SRETheme;
import io.wifi.starrailexpress.client.ui.UiSurface;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import io.wifi.starrailexpress.client.gui.screen.mapui.MapUiGraphics;

/**
 * 模式 / 地图投票旅程共用的视觉框架（黄铜 HUD：深灰黑底 + 深炭灰卡片 + 暗铜描边 + 黄铜文字）。
 *
 * <p>
 * 形状一律走 {@link UiSurface}；本类只管这几步共用的背景、进度条与面板。
 */
public final class VoteFlowFrame {
    // 色值统一在 SRETheme（docs/ui_unified_style_plan.md §3）；这里只保留转发。
    // 注意 PANEL_TOP 是有意保留的「更薄一档」，对应 SRETheme.SURFACE_TOP_SOFT（0xB8…）。
    public static final int BG_TOP = SRETheme.SCREEN_TOP;
    public static final int BG_BOTTOM = SRETheme.SCREEN_BOTTOM;
    public static final int PANEL_TOP = SRETheme.SURFACE_TOP_SOFT;
    public static final int PANEL_BOTTOM = SRETheme.SURFACE_BOTTOM;
    public static final int BORDER = SRETheme.BORDER;
    public static final int GOLD = SRETheme.GOLD;
    public static final int GOLD_DIM = SRETheme.GOLD_DIM;
    public static final int TEXT = SRETheme.TEXT;
    public static final int MUTED = SRETheme.MUTED;
    public static final int BLUE = SRETheme.BLUE;
    public static final int RED = SRETheme.RED;

    /** 投票流程共几步：选择模式 → 选择职业池 → 选择地图 → 进入游戏。 */
    public static final int STEP_COUNT = 4;
    /** 模式投票。 */
    public static final int STEP_MODE = 0;
    /** 职业池投票（地图投票之前的一步）。 */
    public static final int STEP_ROLE_POOL = 1;
    /** 地图投票。 */
    public static final int STEP_MAP = 2;
    /** 结果 / 发车。 */
    public static final int STEP_START = 3;

    private VoteFlowFrame() {}

    /**
     * 全屏底：极简深灰黑（扁平，无斜向纹 / 漂移横线）+ 地平线以下的收暗 + 一个缓慢扫过的黄铜光点。
     */
    public static void renderBackground(GuiGraphics g, int width, int height) {
        UiSurface.backdrop(g, width, height);
        int horizon = Math.max(44, height / 5);
        g.fillGradient(0, horizon, width, height,
                SRETheme.withAlpha(SRETheme.SURFACE_TOP, 0x00), SRETheme.withAlpha(SRETheme.BLACK, 0x55));
        // 整屏只留这一处运动：沿地平线扫过的黄铜光点（同屏脉冲不超过 2 处）
        float time = (System.currentTimeMillis() % 30_000L) / 1000.0F;
        int signalX = Math.round((time * 32.0F) % Math.max(1, width + 120)) - 60;
        for (int radius = 30; radius >= 6; radius -= 6) {
            int glow = Math.max(2, 15 - radius / 3);
            g.fill(signalX - radius, horizon - radius / 3, signalX + radius, horizon + radius / 3,
                    withAlpha(GOLD, glow));
        }
    }

    public static Bounds layout(int width, int height) {
        int w = Math.min(Math.max(1, width - 24), Mth.clamp((int) (width * 0.90F), 430, 960));
        int h = Math.min(Math.max(1, height - 24), Mth.clamp((int) (height * 0.86F), 250, 520));
        return new Bounds((width - w) / 2, (height - h) / 2, w, h);
    }

    public static void renderProgress(GuiGraphics g, Font font, Bounds b, int activeStep, String modeLabel,
            int seconds) {
        int y = b.y + 8;
        int left = b.x + 8;
        int right = b.x + b.w - 8;
        int segment = Math.max(1, (right - left) / STEP_COUNT);
        g.fill(left, y + 23, right, y + 24, SRETheme.withAlpha(SRETheme.CARD_BORDER, 0x40));
        for (int i = 0; i < STEP_COUNT; i++) {
            int x = left + i * segment;
            boolean done = i < activeStep;
            boolean active = i == activeStep;
            int color = done || active ? GOLD : MUTED;
            g.drawString(font, "0" + (i + 1), x, y + 3, done || active ? GOLD_DIM : MUTED, false);
            if (done || active) g.fill(x, y + 23, x + segment - 10, y + 24, color);
            if (active) g.fill(x, y + 22, x + 4, y + 26, GOLD);
            Component label = switch (i) {
                case STEP_ROLE_POOL -> Component.translatable("gui.sre.vote_flow.role_pool");
                case STEP_MAP -> Component.translatable("gui.sre.vote_flow.map");
                case STEP_START -> Component.translatable("gui.sre.vote_flow.start");
                default -> Component.translatable("gui.sre.vote_flow.mode");
            };
            if (i == STEP_MODE && modeLabel != null && !modeLabel.isBlank() && activeStep > STEP_MODE) {
                label = Component.literal(modeLabel);
            }
            g.drawString(font, MapUiGraphics.clip(font, label.getString(), segment - 30), x + 20, y + 3,
                    active ? TEXT : color, false);
        }
        if (seconds >= 0) {
            Component timer = Component.translatable("gui.sre.vote_flow.time", seconds)
                    .withStyle(ChatFormatting.BOLD);
            int color = seconds <= 10 ? RED : BLUE;
            g.drawString(font, timer, right - font.width(timer), b.y + 40, color, false);
        }
    }

    /** 面板：纯色底 + 1px 圆角暗铜描边（扁平），按 {@code alpha} 淡入。 */
    public static void panel(GuiGraphics g, int x, int y, int w, int h, int alpha) {
        UiSurface.card(g, x, y, w, h, withAlpha(PANEL_TOP, alpha), withAlpha(BORDER, alpha));
    }

    public static void scaledCentered(GuiGraphics g, Font font, Component text, float centerX, float y,
            float scale, int color) {
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(centerX, y, 0.0F);
        pose.scale(scale, scale, 1.0F);
        pose.translate(-font.width(text) / 2.0F, 0.0F, 0.0F);
        g.drawString(font, text, 0, 0, color, false);
        pose.popPose();
    }

    public static int withAlpha(int color, int alpha) {
        return (Mth.clamp(alpha, 0, 255) << 24) | (color & SRETheme.RGB);
    }

    public static float ease(float value) {
        float t = Mth.clamp(value, 0.0F, 1.0F);
        float f = 1.0F - t;
        return 1.0F - f * f * f;
    }

    public record Bounds(int x, int y, int w, int h) {}
}
