package org.agmas.noellesroles.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import io.wifi.utils.client.betterrender.FakeGuiGraphics;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.game.roles.neutral.priest.PriestHeavenManager;
import org.agmas.noellesroles.packet.PriestHeavenStateS2CPacket;
import org.joml.Matrix4f;

/**
 * 神父天堂序列的客户端状态：滤镜强度、全屏时钟 HUD、预留咏诵/尾奏音效。
 */
public final class PriestHeavenClient {

    public static final int PHASE_IDLE = 0;
    public static final int PHASE_TRANSFORMED = 1;
    public static final int PHASE_CHANTING = 2;
    public static final int PHASE_ACCELERATING = 3;
    public static final int PHASE_FINALE = 4;
    private static final float OVERHEAD_BLEND_TICKS = 40.0F;
    private static final float OVERHEAD_HEIGHT = 16.0F;

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
    private static float cameraLift;
    private static Vec3 lockEye;
    private static Vec3 lockAbove;
    private static float lockYaw;
    private static float lockPitch;

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

    public static boolean isOverheadCamera() {
        return phase == PHASE_ACCELERATING || phase == PHASE_FINALE;
    }

    public static boolean isMovementLocked() {
        return isOverheadCamera();
    }

    public static float overheadBlend(float tickDelta) {
        if (!isOverheadCamera()) {
            cameraLift = 0.0F;
            clearCameraAnchor();
            return 0.0F;
        }
        float elapsed = phase == PHASE_FINALE ? OVERHEAD_BLEND_TICKS : accelElapsed + tickDelta;
        float t = Mth.clamp(elapsed / OVERHEAD_BLEND_TICKS, 0.0F, 1.0F);
        cameraLift = t;
        return t * t * (3.0F - 2.0F * t);
    }

    public static Vec3 overheadCameraPos(Entity entity, float tickDelta, float blend) {
        ensureCameraAnchor(entity, tickDelta);
        if (lockEye == null || lockAbove == null) {
            return entity.getEyePosition(tickDelta);
        }
        return lockEye.lerp(lockAbove, blend);
    }

    public static float overheadYaw(Entity entity, float tickDelta) {
        ensureCameraAnchor(entity, tickDelta);
        return lockYaw;
    }

    public static float overheadPitch(Entity entity, float tickDelta, float blend) {
        ensureCameraAnchor(entity, tickDelta);
        return Mth.lerp(blend, lockPitch, 90.0F);
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
        cameraLift = 0;
        clearCameraAnchor();
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
            mc.player.playNotifySound(SoundEvents.END_PORTAL_SPAWN, SoundSource.MASTER, 1.0F, 0.55F);
        }
    }

    public static void apply(PriestHeavenStateS2CPacket packet) {
        int previous = phase;
        phase = packet.phase();
        lyricIndex = packet.lyricIndex();
        chantRemain = packet.chantRemain();
        accelElapsed = packet.accelElapsed();
        visualTime = packet.visualTime();
        if (phase == PHASE_IDLE) {
            shaderStrength = 0;
            localVisualTime = 0;
            cameraLift = 0;
            clearCameraAnchor();
        } else if (Math.abs(localVisualTime - packet.visualTime()) > 40) {
            localVisualTime = packet.visualTime();
        }
        if (previous != PHASE_ACCELERATING && previous != PHASE_FINALE && isOverheadCamera()) {
            clearCameraAnchor();
        }
        playReservedSound(packet.soundIndex());
    }

    public static void renderHud(FakeGuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || phase == PHASE_IDLE) {
            shaderStrength += (0.0F - shaderStrength) * 0.12F;
            return;
        }
        float last15 = inLast15Seconds() ? 1.0F : 0.0F;
        float target = switch (phase) {
            case PHASE_TRANSFORMED -> 0.45F;
            case PHASE_CHANTING -> 0.68F;
            case PHASE_ACCELERATING -> 0.86F + 0.14F * accelProgress();
            case PHASE_FINALE -> 1.0F;
            default -> 0.0F;
        };
        shaderStrength += (target - shaderStrength) * 0.14F;

        float dt = Math.max(0.01F, deltaTracker.getRealtimeDeltaTicks());
        localVisualTime += (visualTime - localVisualTime) * 0.35F;
        if (phase == PHASE_ACCELERATING || phase == PHASE_FINALE) {
            if (phase != PHASE_FINALE) {
                float dps = 3.2F + accelProgress() * 9.5F + last15 * 6.0F;
                clockAngle += dps * dt;
            }
            renderClockBackground(graphics, mc);
            renderFlashOverlay(graphics);
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

    private static void ensureCameraAnchor(Entity entity, float tickDelta) {
        if (lockAbove != null || entity == null || !isOverheadCamera()) {
            return;
        }
        lockEye = entity.getEyePosition(tickDelta);
        lockAbove = new Vec3(entity.getX(), entity.getY() + OVERHEAD_HEIGHT, entity.getZ());
        lockYaw = entity.getViewYRot(tickDelta);
        lockPitch = entity.getViewXRot(tickDelta);
    }

    private static void clearCameraAnchor() {
        lockEye = null;
        lockAbove = null;
        lockYaw = 0.0F;
        lockPitch = 0.0F;
    }

    private static void renderFlashOverlay(FakeGuiGraphics graphics) {
        int alpha;
        if (phase == PHASE_FINALE) {
            alpha = 0x55;
        } else if (inLast15Seconds()) {
            alpha = 0x30 + (int) (accelProgress() * 0x20);
        } else {
            alpha = 0x10 + (int) (accelProgress() * 0x18);
        }
        int color = (alpha << 24) | 0xF4E4A6;
        graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), color);
    }

    private static void renderClockBackground(FakeGuiGraphics graphics, Minecraft mc) {
        GuiGraphics real = graphics.getDefaultGuiGraphics();
        float cx = graphics.guiWidth() / 2.0F;
        float cy = graphics.guiHeight() / 2.0F;
        float radius = Math.min(cx, cy) - 22.0F;
        float angle = clockAngle;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        Matrix4f matrix = real.pose().last().pose();

        drawDisc(matrix, cx, cy, radius + 10.0F, 0x22000000);
        drawRing(matrix, cx, cy, radius - 8.0F, radius, 0xE6F4E4A6);
        drawRing(matrix, cx, cy, radius - 20.0F, radius - 16.0F, 0x88E8D48B);

        BufferBuilder ticks = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i < 60; i++) {
            double a = Math.toRadians(angle + i * 6.0);
            boolean major = i % 5 == 0;
            float inner = major ? radius - 30.0F : radius - 14.0F;
            float width = major ? 5.0F : 2.2F;
            int color = major ? 0xF0F4E4A6 : 0x99E8D48B;
            spoke(ticks, matrix, cx, cy, a, inner, radius - 2.0F, width, color);
        }

        double second = Math.toRadians(angle);
        double minute = Math.toRadians(angle * 0.2);
        double hour = Math.toRadians(angle * 0.03);
        if (phase == PHASE_FINALE) {
            second = Math.toRadians(clockAngle);
            minute = Math.toRadians(clockAngle * 0.2);
            hour = Math.toRadians(clockAngle * 0.03);
        }
        spoke(ticks, matrix, cx, cy, hour, 0.0F, radius * 0.46F, 7.0F, 0xF0FFFFFF);
        spoke(ticks, matrix, cx, cy, minute, 0.0F, radius * 0.68F, 4.4F, 0xF5F4E4A6);
        spoke(ticks, matrix, cx, cy, second, -14.0F, radius * 0.88F, 2.4F, 0xFFFFE08A);
        BufferUploader.drawWithShader(ticks.buildOrThrow());

        drawDisc(matrix, cx, cy, 6.0F, 0xFFFFF4C8);
        RenderSystem.disableBlend();

        if (!inLast15Seconds() && phase == PHASE_ACCELERATING) {
            graphics.drawCenteredString(mc.font, formatTime((int) localVisualTime), (int) cx, 16, 0xEEF4E4A6);
        }
    }

    private static void drawDisc(Matrix4f matrix, float cx, float cy, float radius, int color) {
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);
        buffer.addVertex(matrix, cx, cy, 0.0F).setColor(color);
        int segments = 64;
        for (int i = 0; i <= segments; i++) {
            double a = (Math.PI * 2.0 * i) / segments;
            buffer.addVertex(matrix, cx + (float) Math.sin(a) * radius, cy - (float) Math.cos(a) * radius, 0.0F).setColor(color);
        }
        BufferUploader.drawWithShader(buffer.buildOrThrow());
    }

    private static void drawRing(Matrix4f matrix, float cx, float cy, float inner, float outer, int color) {
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        int segments = 96;
        for (int i = 0; i <= segments; i++) {
            double a = (Math.PI * 2.0 * i) / segments;
            float sin = (float) Math.sin(a);
            float cos = (float) Math.cos(a);
            buffer.addVertex(matrix, cx + sin * inner, cy - cos * inner, 0.0F).setColor(color);
            buffer.addVertex(matrix, cx + sin * outer, cy - cos * outer, 0.0F).setColor(color);
        }
        BufferUploader.drawWithShader(buffer.buildOrThrow());
    }

    private static void spoke(BufferBuilder buffer, Matrix4f matrix, float cx, float cy, double angle,
            float inner, float outer, float width, int color) {
        float nx = (float) Math.sin(angle);
        float ny = (float) -Math.cos(angle);
        float px = -ny * width * 0.5F;
        float py = nx * width * 0.5F;
        float ix = cx + nx * inner;
        float iy = cy + ny * inner;
        float ox = cx + nx * outer;
        float oy = cy + ny * outer;
        buffer.addVertex(matrix, ix - px, iy - py, 0.0F).setColor(color);
        buffer.addVertex(matrix, ix + px, iy + py, 0.0F).setColor(color);
        buffer.addVertex(matrix, ox + px, oy + py, 0.0F).setColor(color);
        buffer.addVertex(matrix, ox - px, oy - py, 0.0F).setColor(color);
    }

    private static void renderDramaticTime(FakeGuiGraphics graphics, Minecraft mc) {
        float remain = (PriestHeavenManager.ACCEL_TICKS - accelElapsed) / (float) PriestHeavenManager.LAST_DRAMATIC_TICKS;
        remain = Mth.clamp(remain, 0.0F, 1.0F);
        float intensity = 1.0F - remain;
        float scale = 2.6F + intensity * 6.2F;
        int jitterX = (int) ((Math.sin(clockAngle * 0.08) + Math.sin(clockAngle * 0.03)) * (2.0 + intensity * 6.0));
        int jitterY = (int) (Math.cos(clockAngle * 0.05) * (1.5 + intensity * 4.0));
        String time = formatTime((int) Math.max(0, localVisualTime));
        Font font = mc.font;
        int cx = graphics.guiWidth() / 2 + jitterX;
        int cy = graphics.guiHeight() / 2 - 10 + jitterY;
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(cx, cy, 0);
        pose.scale(scale, scale, 1.0F);
        graphics.drawCenteredString(font, time, 1, -3, 0x88FF3030);
        graphics.drawCenteredString(font, time, -1, -5, 0x8830E0FF);
        graphics.drawCenteredString(font, time, 0, -4, 0xFFFFF4C8);
        pose.popPose();
        graphics.drawCenteredString(font, Component.translatable("hud.noellesroles.priest.accel"),
                graphics.guiWidth() / 2, cy + (int) (18 * Math.min(scale, 5.5F)), 0xFFFFE08A);
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
            mc.player.playNotifySound(sound, SoundSource.MASTER, 1.0F, pitchForIndex(soundIndex));
        }
    }

    private static SoundEvent soundForIndex(int soundIndex) {
        if (soundIndex >= 0 && soundIndex < 15) {
            return SoundEvents.END_PORTAL_FRAME_FILL;
        }
        return switch (soundIndex) {
            case PriestHeavenStateS2CPacket.SOUND_CHANTING -> SoundEvents.BEACON_ACTIVATE;
            case PriestHeavenStateS2CPacket.SOUND_TRANSFORM -> SoundEvents.END_PORTAL_SPAWN;
            case PriestHeavenStateS2CPacket.SOUND_ACCEL -> SoundEvents.ENDER_DRAGON_GROWL;
            case PriestHeavenStateS2CPacket.SOUND_FINALE -> SoundEvents.WITHER_SPAWN;
            default -> null;
        };
    }

    private static float pitchForIndex(int soundIndex) {
        if (soundIndex >= 0 && soundIndex < 15) {
            return 0.82F + soundIndex * 0.03F;
        }
        if (soundIndex == PriestHeavenStateS2CPacket.SOUND_ACCEL) {
            return 0.7F;
        }
        if (soundIndex == PriestHeavenStateS2CPacket.SOUND_FINALE) {
            return 0.8F;
        }
        return 1.0F;
    }
}
