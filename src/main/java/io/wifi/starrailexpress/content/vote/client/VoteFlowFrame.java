package io.wifi.starrailexpress.content.vote.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

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
        int w = Mth.clamp((int) (width * 0.88F), 430, 840);
        int h = Mth.clamp((int) (height * 0.80F), 250, 430);
        return new Bounds((width - w) / 2, (height - h) / 2, w, h);
    }

    public static void renderProgress(GuiGraphics g, Font font, Bounds b, int activeStep, String modeLabel,
            int seconds) {
        int y = b.y + 15;
        int left = b.x + 18;
        int right = b.x + b.w - 18;
        int segment = (right - left) / 2;
        float pulse = 0.5F + 0.5F * (float) Math.sin(System.currentTimeMillis() / 210.0F);
        g.fill(left, y + 7, right, y + 8, 0x665A4530);
        for (int i = 0; i < 3; i++) {
            int x = left + i * segment;
            boolean done = i < activeStep;
            boolean active = i == activeStep;
            int color = done || active ? GOLD : MUTED;
            int radius = active ? 4 + Math.round(pulse) : 3;
            if (active) {
                g.fill(x - 7, y, x + 8, y + 15, withAlpha(GOLD, 18 + Math.round(pulse * 18.0F)));
            }
            g.fill(x - radius, y + 7 - radius, x + radius + 1, y + 8 + radius, color);
            if (i < 2 && done) g.fill(x + 4, y + 6, x + segment, y + 9, 0xCCD4AF37);
            Component label = switch (i) {
                case 0 -> Component.translatable("gui.sre.vote_flow.mode");
                case 1 -> Component.translatable("gui.sre.vote_flow.map");
                default -> Component.translatable("gui.sre.vote_flow.start");
            };
            if (i == 0 && modeLabel != null && !modeLabel.isBlank() && activeStep > 0) {
                label = Component.literal(modeLabel);
            }
            int tx = i == 2 ? x - font.width(label) : x + 8;
            g.drawString(font, label, tx, y - 3, color, false);
        }
        if (activeStep < 2) {
            float travel = (System.currentTimeMillis() % 1_600L) / 1_600.0F;
            int start = left + activeStep * segment;
            int marker = Math.round(Mth.lerp(travel, start + 8.0F, start + segment - 8.0F));
            g.fill(marker - 2, y + 5, marker + 3, y + 10, withAlpha(GOLD, Math.round(190 * (1.0F - travel))));
        }
        if (seconds >= 0) {
            Component timer = Component.translatable("gui.sre.vote_flow.time", seconds)
                    .withStyle(ChatFormatting.BOLD);
            int color = seconds <= 10 ? RED : BLUE;
            g.drawString(font, timer, right - font.width(timer), b.y + 34, color, false);
        }
    }

    public static void panel(GuiGraphics g, int x, int y, int w, int h, int alpha) {
        g.fillGradient(x, y, x + w, y + h, withAlpha(PANEL_TOP, alpha), withAlpha(PANEL_BOTTOM, alpha));
        g.renderOutline(x, y, w, h, withAlpha(BORDER, alpha));
        g.fill(x + 1, y + 1, x + w - 1, y + 2, withAlpha(0x33FFE8C0, alpha));
    }

    public static void scaledCentered(GuiGraphics g, Font font, Component text, float centerX, float y,
            float scale, int color) {
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(centerX, y, 0.0F);
        pose.scale(scale, scale, 1.0F);
        g.drawString(font, text, -font.width(text) / 2, 0, color, false);
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
