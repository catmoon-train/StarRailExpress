package org.agmas.noellesroles.client;

import io.wifi.utils.client.betterrender.FakeGuiGraphics;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import org.agmas.noellesroles.game.roles.neutral.priest.PriestHeavenManager;
import org.agmas.noellesroles.init.NRSounds;
import org.agmas.noellesroles.packet.PriestHeavenStateS2CPacket;

/**
 * 神父天堂序列的客户端状态：滤镜强度、全屏时钟 HUD、预留咏诵/尾奏音效。
 */
public final class PriestHeavenClient {

    public static final int PHASE_IDLE = 0;
    public static final int PHASE_TRANSFORMED = 1;
    public static final int PHASE_CHANTING = 2;
    public static final int PHASE_ACCELERATING = 3;
    public static final int PHASE_FINALE = 4;

    private static int phase = PHASE_IDLE;
    private static int lyricIndex;
    private static int chantRemain;
    private static int accelElapsed;
    private static int visualTime;
    private static float localVisualTime;
    private static float clockAngle;
    private static boolean endingPlayed;
    private static boolean pendingEnding;
    private static float shaderStrength;

    private PriestHeavenClient() {
    }

    public static int phase() {
        return phase;
    }

    public static int lyricIndex() {
        return lyricIndex;
    }

    public static float shaderStrength() {
        return shaderStrength;
    }

    public static void reset() {
        phase = PHASE_IDLE;
        lyricIndex = 0;
        chantRemain = 0;
        accelElapsed = 0;
        visualTime = 0;
        localVisualTime = 0;
        clockAngle = 0;
        endingPlayed = false;
        pendingEnding = false;
        shaderStrength = 0;
    }

    public static boolean consumePendingEnding() {
        boolean play = pendingEnding || phase == PHASE_FINALE;
        pendingEnding = false;
        return play;
    }

    public static void playEnding() {
        if (endingPlayed) {
            return;
        }
        endingPlayed = true;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.playNotifySound(NRSounds.PRIEST_ENDING, SoundSource.MASTER, 1.0F, 1.0F);
        }
    }

    public static void apply(PriestHeavenStateS2CPacket packet) {
        phase = packet.phase();
        lyricIndex = packet.lyricIndex();
        chantRemain = packet.chantRemain();
        accelElapsed = packet.accelElapsed();
        visualTime = packet.visualTime();
        localVisualTime = packet.visualTime();
        if (phase == PHASE_IDLE) {
            shaderStrength = 0;
        }
        playReservedSound(packet.soundIndex());
    }

    public static void renderHud(FakeGuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || phase == PHASE_IDLE) {
            shaderStrength += (0.0F - shaderStrength) * 0.08F;
            return;
        }
        float target = switch (phase) {
            case PHASE_TRANSFORMED -> 0.18F;
            case PHASE_CHANTING -> 0.36F;
            case PHASE_ACCELERATING -> 0.42F + 0.58F * accelProgress();
            case PHASE_FINALE -> 1.0F;
            default -> 0.0F;
        };
        shaderStrength += (target - shaderStrength) * 0.12F;

        float partial = deltaTracker.getGameTimeDeltaPartialTick(false);
        if (phase == PHASE_ACCELERATING || phase == PHASE_FINALE) {
            clockAngle += 18.0F + accelProgress() * 42.0F;
            if (phase == PHASE_ACCELERATING) {
                localVisualTime = Math.max(0.0F, localVisualTime - (1.0F + accelProgress() * 8.0F));
            }
            renderClockBackground(graphics, mc, partial);
        }
        if (phase == PHASE_CHANTING) {
            int seconds = Math.max(0, chantRemain / 20);
            graphics.drawCenteredString(mc.font,
                    Component.translatable("hud.noellesroles.priest.chanting", String.format("%d", seconds)),
                    graphics.guiWidth() / 2, 18, 0xFFF4E4A6);
        }
        if (phase == PHASE_ACCELERATING && inLast15Seconds()) {
            renderDramaticTime(graphics, mc);
        } else if (phase == PHASE_FINALE) {
            graphics.drawCenteredString(mc.font,
                    Component.translatable("hud.noellesroles.priest.time_stop"),
                    graphics.guiWidth() / 2, graphics.guiHeight() / 2 - 20, 0xFFFFFFFF);
        }
    }

    private static void renderClockBackground(FakeGuiGraphics graphics, Minecraft mc, float partial) {
        int cx = graphics.guiWidth() / 2;
        int cy = graphics.guiHeight() / 2;
        int radius = Math.min(cx, cy) - 28;
        int ticks = 12;
        for (int i = 0; i < ticks; i++) {
            double a = Math.toRadians(clockAngle + i * 30.0);
            int x1 = cx + (int) (Math.sin(a) * (radius - 18));
            int y1 = cy - (int) (Math.cos(a) * (radius - 18));
            int x2 = cx + (int) (Math.sin(a) * radius);
            int y2 = cy - (int) (Math.cos(a) * radius);
            graphics.fill(Math.min(x1, x2), Math.min(y1, y2), Math.max(x1, x2) + 2, Math.max(y1, y2) + 2, 0x33F4E4A6);
        }
        double hand = Math.toRadians(clockAngle * 3.0);
        int hx = cx + (int) (Math.sin(hand) * (radius * 0.72));
        int hy = cy - (int) (Math.cos(hand) * (radius * 0.72));
        graphics.fill(Math.min(cx, hx), Math.min(cy, hy), Math.max(cx, hx) + 2, Math.max(cy, hy) + 2, 0x66FFFFFF);
        if (!inLast15Seconds() && phase == PHASE_ACCELERATING) {
            graphics.drawCenteredString(mc.font, formatTime((int) localVisualTime), cx, 16, 0xAAF4E4A6);
        }
    }

    private static void renderDramaticTime(FakeGuiGraphics graphics, Minecraft mc) {
        float remain = (PriestHeavenManager.ACCEL_TICKS - accelElapsed) / (float) PriestHeavenManager.LAST_DRAMATIC_TICKS;
        remain = Mth.clamp(remain, 0.0F, 1.0F);
        float scale = 2.2F + (1.0F - remain) * 4.4F;
        int jitterX = (int) ((Math.sin(clockAngle * 0.37) + Math.sin(clockAngle * 0.11)) * (2.0 + (1.0F - remain) * 6.0));
        int jitterY = (int) (Math.cos(clockAngle * 0.29) * (1.0 + (1.0F - remain) * 4.0));
        String time = formatTime((int) localVisualTime);
        Font font = mc.font;
        int cx = graphics.guiWidth() / 2 + jitterX;
        int cy = graphics.guiHeight() / 2 - 10 + jitterY;
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(cx, cy, 0);
        pose.scale(scale, scale, 1.0F);
        graphics.drawCenteredString(font, time, 0, -4, 0xFFFFF4C8);
        pose.popPose();
        graphics.drawCenteredString(font, Component.translatable("hud.noellesroles.priest.accel"),
                graphics.guiWidth() / 2, cy + (int) (18 * scale), 0xFFE8C86A);
    }

    private static boolean inLast15Seconds() {
        return phase == PHASE_ACCELERATING
                && accelElapsed >= PriestHeavenManager.ACCEL_TICKS - PriestHeavenManager.LAST_DRAMATIC_TICKS;
    }

    private static float accelProgress() {
        return Mth.clamp(accelElapsed / (float) PriestHeavenManager.ACCEL_TICKS, 0.0F, 1.0F);
    }

    private static String formatTime(int ticks) {
        int seconds = Math.max(0, ticks / 20);
        return String.format("%02d:%02d", seconds / 60, seconds % 60);
    }

    private static void playReservedSound(int soundIndex) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || soundIndex == PriestHeavenStateS2CPacket.SOUND_NONE) {
            return;
        }
        if (soundIndex == PriestHeavenStateS2CPacket.SOUND_ENDING) {
            pendingEnding = true;
            return;
        }
        SoundEvent sound = soundForIndex(soundIndex);
        if (sound != null) {
            mc.player.playNotifySound(sound, SoundSource.MASTER, 1.0F, 1.0F);
        }
    }

    private static SoundEvent soundForIndex(int soundIndex) {
        if (soundIndex >= 0 && soundIndex < NRSounds.PRIEST_CHANT.length) {
            return NRSounds.PRIEST_CHANT[soundIndex];
        }
        if (soundIndex == PriestHeavenStateS2CPacket.SOUND_CHANTING) {
            return NRSounds.PRIEST_CHANTING;
        }
        return null;
    }
}
