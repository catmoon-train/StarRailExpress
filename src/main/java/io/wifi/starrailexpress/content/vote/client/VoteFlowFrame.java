package io.wifi.starrailexpress.content.vote.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import io.wifi.starrailexpress.client.gui.screen.mapui.MapUiGraphics;

/** Shared visual frame for the mode/map voting journey. */
public final class VoteFlowFrame {
    public static final int BG_TOP = 0xF018120A;
    public static final int BG_BOTTOM = 0xF0061018;
    public static final int PANEL_TOP = 0xB81A1008;
    public static final int PANEL_BOTTOM = 0xD820140A;
    public static final int BORDER = 0xFF8B6914;
    public static final int GOLD = 0xFFD4AF37;
    public static final int GOLD_DIM = 0xFFC9A84C;
    public static final int TEXT = 0xFFFFF4DC;
    public static final int MUTED = 0xFF9E8B6E;
    public static final int BLUE = 0xFF5EB7D8;
    public static final int RED = 0xFFE06B65;

    private VoteFlowFrame() {}

    public static void renderBackground(GuiGraphics g, int width, int height) {
        g.fillGradient(0, 0, width, height, BG_TOP, BG_BOTTOM);
        int horizon = Math.max(44, height / 5);
        g.fillGradient(0, horizon, width, height, 0x001A1008, 0x55000000);
        float time = (System.currentTimeMillis() % 30_000L) / 1000.0F;
        int drift = Math.round((time * 7.0F) % 28.0F);
        for (int i = 0; i < 5; i++) {
            int y = horizon + i * Math.max(22, (height - horizon) / 5) + drift;
            g.fill(0, y, width, y + 1, 0x0CFFE8C0);
        }
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(width / 2.0F, height / 2.0F, 0.0F);
        pose.mulPose(Axis.ZP.rotationDegrees(-7.0F));
        pose.translate(-width / 2.0F, -height / 2.0F, 0.0F);
        int diagonalOffset = Math.round((time * 18.0F) % 116.0F);
        for (int x = -height; x < width + height; x += 116) {
            int lineX = x + diagonalOffset;
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
        g.fill(left, y + 23, right, y + 24, 0x405A4530);
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
        g.fill(x + 1, y + 1, x + w - 1, y + 2, withAlpha(0xFFFFE8C0, alpha / 5));
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
        return (Mth.clamp(alpha, 0, 255) << 24) | (color & 0x00FFFFFF);
    }

    public static float ease(float value) {
        float t = Mth.clamp(value, 0.0F, 1.0F);
        float f = 1.0F - t;
        return 1.0F - f * f * f;
    }

    public record Bounds(int x, int y, int w, int h) {}
}
