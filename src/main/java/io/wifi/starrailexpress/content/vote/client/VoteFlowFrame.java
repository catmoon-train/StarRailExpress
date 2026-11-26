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
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import io.wifi.starrailexpress.client.gui.screen.mapui.MapUiGraphics;

/** Shared visual frame for the mode/map voting journey. */
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

    private VoteFlowFrame() {}

    public static void renderBackground(GuiGraphics g, int width, int height) {
        g.fillGradient(0, 0, width, height, BG_TOP, BG_BOTTOM);
        int horizon = Math.max(44, height / 5);
        // sre:allow-literal-color 地平线遮罩：通体透明到半透明黑，与 Background 渐变叠色用
        g.fillGradient(0, horizon, width, height, SRETheme.withAlpha(SRETheme.SURFACE_TOP, 0x00), 0x55000000);
        float time = (System.currentTimeMillis() % 30_000L) / 1000.0F;
        int drift = Math.round((time * 7.0F) % 28.0F);
        for (int i = 0; i < 5; i++) {
            int y = horizon + i * Math.max(22, (height - horizon) / 5) + drift;
            g.fill(0, y, width, y + 1, SRETheme.withAlpha(SRETheme.DECOR_LINE, 0x0C));
        }
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(width / 2.0F, height / 2.0F, 0.0F);
        pose.mulPose(Axis.ZP.rotationDegrees(-7.0F));
        pose.translate(-width / 2.0F, -height / 2.0F, 0.0F);
        int diagonalOffset = Math.round((time * 18.0F) % 116.0F);
        for (int x = -height; x < width + height; x += 116) {
            int lineX = x + diagonalOffset;
            // sre:allow-literal-color 比 DECOR_LINE 更暖的浅金斜向纹，是独立装饰元素
            g.fill(lineX, -80, lineX + 1, height + 80, 0x0EFFE1A1);
        }
        pose.popPose();

        int signalX = Math.round((time * 32.0F) % Math.max(1, width + 120)) - 60;
        for (int radius = 30; radius >= 6; radius -= 6) {
            int alpha = Math.max(2, 15 - radius / 3);
            g.fill(signalX - radius, horizon - radius / 3, signalX + radius, horizon + radius / 3,
                    withAlpha(GOLD, alpha));
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
        int segment = (right - left) / 3;
        g.fill(left, y + 23, right, y + 24, SRETheme.withAlpha(SRETheme.CARD_BORDER, 0x40));
        for (int i = 0; i < 3; i++) {
            int x = left + i * segment;
            boolean done = i < activeStep;
            boolean active = i == activeStep;
            int color = done || active ? GOLD : MUTED;
            g.drawString(font, "0" + (i + 1), x, y + 3, done || active ? GOLD_DIM : MUTED, false);
            if (done || active) g.fill(x, y + 23, x + segment - 10, y + 24, color);
            if (active) g.fill(x, y + 22, x + 4, y + 26, GOLD);
            Component label = switch (i) {
                case 0 -> Component.translatable("gui.sre.vote_flow.mode");
                case 1 -> Component.translatable("gui.sre.vote_flow.map");
                default -> Component.translatable("gui.sre.vote_flow.start");
            };
            if (i == 0 && modeLabel != null && !modeLabel.isBlank() && activeStep > 0) {
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

    public static void panel(GuiGraphics g, int x, int y, int w, int h, int alpha) {
        g.fillGradient(x, y, x + w, y + h, withAlpha(PANEL_TOP, alpha), withAlpha(PANEL_BOTTOM, alpha));
        g.renderOutline(x, y, w, h, withAlpha(BORDER, alpha));
        g.fill(x + 1, y + 1, x + w - 1, y + 2, withAlpha(SRETheme.DECOR_LINE, alpha / 5));
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
