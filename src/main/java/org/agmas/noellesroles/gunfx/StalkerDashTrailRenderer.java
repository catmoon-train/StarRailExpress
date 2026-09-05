/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.agmas.noellesroles.gunfx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 刺客形态冲刺的双刀轨迹：左右刀位各一条类似枪弹道的掠过残影，并沿真实位移拼接。
 */
public final class StalkerDashTrailRenderer {
    private static final double LIFE_TICKS = 9.0D;
    private static final int GRADIENT_SEGMENTS = 8;
    private static final List<BladeStreak> STREAKS = new ArrayList<>();

    private record BladeStreak(Vec3 from, Vec3 to, boolean attack,
            double travelTicks, long bornGameTime) {
    }

    private StalkerDashTrailRenderer() {
    }

    public static void onPacket(StalkerDashTrailS2CPacket packet) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) {
            return;
        }
        Vec3 from = new Vec3(packet.fromX(), packet.fromY(), packet.fromZ());
        Vec3 to = new Vec3(packet.toX(), packet.toY(), packet.toZ());
        Vec3 movement = to.subtract(from);
        if (movement.lengthSqr() < 1.0E-4D) {
            return;
        }
        Vec3 direction = movement.normalize();
        Vec3 leftFrom = WeaponTrailGeometry.bladePoint(from, direction, -1.0D);
        Vec3 leftTo = WeaponTrailGeometry.bladePoint(to, direction, -1.15D);
        Vec3 rightFrom = WeaponTrailGeometry.bladePoint(from, direction, 1.0D);
        Vec3 rightTo = WeaponTrailGeometry.bladePoint(to, direction, 1.15D);
        long born = client.level.getGameTime();
        double travel = Mth.clamp(0.55D + movement.length() / 6.0D, 0.7D, 1.8D);
        STREAKS.add(new BladeStreak(leftFrom, leftTo, packet.attackDash(), travel, born));
        STREAKS.add(new BladeStreak(rightFrom, rightTo, packet.attackDash(), travel, born));

        client.level.addParticle(packet.attackDash() ? ParticleTypes.CRIT : ParticleTypes.ENCHANTED_HIT,
                leftTo.x, leftTo.y, leftTo.z, 0, 0, 0);
        client.level.addParticle(packet.attackDash() ? ParticleTypes.CRIT : ParticleTypes.ENCHANTED_HIT,
                rightTo.x, rightTo.y, rightTo.z, 0, 0, 0);
    }

    public static void render(WorldRenderContext context) {
        if (STREAKS.isEmpty()) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) {
            STREAKS.clear();
            return;
        }
        double now = client.level.getGameTime()
                + client.getTimer().getGameTimeDeltaPartialTick(false);
        pruneExpired(now);
        if (STREAKS.isEmpty()) {
            return;
        }
        Vec3 camera = context.camera().getPosition();
        PoseStack matrices = context.matrixStack();
        WeaponTrailImmediateDraw.drawLines(3.8F,
                consumer -> drawPass(matrices, camera, now, consumer, true));
        WeaponTrailImmediateDraw.drawLines(1.15F,
                consumer -> drawPass(matrices, camera, now, consumer, false));
    }

    private static void pruneExpired(double now) {
        for (Iterator<BladeStreak> iterator = STREAKS.iterator(); iterator.hasNext();) {
            BladeStreak streak = iterator.next();
            if (now - streak.bornGameTime() >= LIFE_TICKS
                    || streak.from().distanceToSqr(streak.to()) < 1.0E-6D) {
                iterator.remove();
            }
        }
    }

    private static void drawPass(PoseStack matrices, Vec3 camera, double now,
            VertexConsumer consumer, boolean glow) {
        for (BladeStreak streak : STREAKS) {
            double age = now - streak.bornGameTime();
            double distance = streak.from().distanceTo(streak.to());
            double linearHead = clamp(age / streak.travelTicks());
            double head = 1.0D - Math.pow(1.0D - linearHead, 2.2D);
            double streakLength = Math.max(0.85D, Math.min(4.2D, distance * 0.70D));
            double tail = Math.max(0.0D, head - streakLength / distance);
            if (age > streak.travelTicks()) {
                double shrink = clamp((age - streak.travelTicks()) / (LIFE_TICKS - streak.travelTicks()));
                tail += (head - tail) * (0.25D + 0.75D * shrink);
            }
            float fade = (float) Math.pow(1.0D - clamp(age / LIFE_TICKS), 0.62D);
            float r = glow ? (streak.attack() ? 1.00F : 0.28F) : 1.00F;
            float g = glow ? (streak.attack() ? 0.22F : 0.78F) : (streak.attack() ? 0.62F : 0.94F);
            float b = glow ? (streak.attack() ? 0.08F : 1.00F) : (streak.attack() ? 0.32F : 1.00F);
            float alpha = fade * (glow ? 0.26F : 0.92F);

            matrices.pushPose();
            matrices.translate(streak.from().x - camera.x, streak.from().y - camera.y,
                    streak.from().z - camera.z);
            gradientLine(matrices.last(), consumer, streak.to().subtract(streak.from()),
                    tail, head, r, g, b, alpha);
            matrices.popPose();
        }
    }

    private static void gradientLine(PoseStack.Pose pose, VertexConsumer consumer, Vec3 delta,
            double tail, double head, float r, float g, float b, float alpha) {
        Vec3 normal = delta.normalize();
        for (int segment = 0; segment < GRADIENT_SEGMENTS; segment++) {
            double startFactor = segment / (double) GRADIENT_SEGMENTS;
            double endFactor = (segment + 1.0D) / GRADIENT_SEGMENTS;
            double start = tail + (head - tail) * startFactor;
            double end = tail + (head - tail) * endFactor;
            float startAlpha = alpha * (0.05F + 0.95F * (float) Math.pow(startFactor, 1.25D));
            float endAlpha = alpha * (0.05F + 0.95F * (float) Math.pow(endFactor, 1.25D));
            consumer.addVertex(pose, (float) (delta.x * start), (float) (delta.y * start),
                    (float) (delta.z * start)).setColor(r, g, b, startAlpha)
                    .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
            consumer.addVertex(pose, (float) (delta.x * end), (float) (delta.y * end),
                    (float) (delta.z * end)).setColor(r, g, b, endAlpha)
                    .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
        }
    }

    private static double clamp(double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
    }
}
