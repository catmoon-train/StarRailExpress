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
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** 命中后从目标身上穿过的多条红色斩击线。 */
public final class StalkerPierceFxRenderer {
    private static final double LIFE_TICKS = 7.5D;
    private static final int LINE_COUNT = 6;
    private static final List<Slash> SLASHES = new ArrayList<>();

    private record Slash(Vec3 from, Vec3 to, long bornGameTime) {
    }

    private StalkerPierceFxRenderer() {
    }

    public static void onPacket(StalkerPierceFxS2CPacket packet) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) {
            return;
        }
        Vec3 center = new Vec3(packet.x(), packet.y(), packet.z());
        Vec3 dir = new Vec3(packet.dirX(), packet.dirY(), packet.dirZ());
        if (dir.lengthSqr() < 1.0E-4D) {
            dir = new Vec3(0.0D, 0.0D, 1.0D);
        }
        dir = dir.normalize();
        Vec3 side = WeaponTrailGeometry.sideways(dir);
        Vec3 up = dir.cross(side);
        if (up.lengthSqr() < 1.0E-4D) {
            up = new Vec3(0.0D, 1.0D, 0.0D);
        } else {
            up = up.normalize();
        }
        Random random = new Random(Double.doubleToLongBits(center.x + center.z) ^ client.level.getGameTime());
        long born = client.level.getGameTime();
        for (int i = 0; i < LINE_COUNT; i++) {
            double yaw = (i / (double) LINE_COUNT) * Math.PI * 2.0D + random.nextDouble() * 0.35D;
            Vec3 offset = side.scale(Math.cos(yaw) * (0.12D + random.nextDouble() * 0.28D))
                    .add(up.scale(Math.sin(yaw) * (0.10D + random.nextDouble() * 0.22D)));
            double back = 1.35D + random.nextDouble() * 0.55D;
            double front = 2.15D + random.nextDouble() * 0.85D;
            SLASHES.add(new Slash(center.add(dir.scale(-back)).add(offset),
                    center.add(dir.scale(front)).add(offset.scale(1.15D)), born));
        }
    }

    public static void render(WorldRenderContext context) {
        if (SLASHES.isEmpty()) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) {
            SLASHES.clear();
            return;
        }
        double now = client.level.getGameTime()
                + client.getTimer().getGameTimeDeltaPartialTick(false);
        SLASHES.removeIf(slash -> now - slash.bornGameTime() >= LIFE_TICKS);
        if (SLASHES.isEmpty()) {
            return;
        }
        Vec3 camera = context.camera().getPosition();
        PoseStack matrices = context.matrixStack();
        WeaponTrailImmediateDraw.drawLines(3.4F, consumer -> drawPass(matrices, camera, now, consumer, true));
        WeaponTrailImmediateDraw.drawLines(1.1F, consumer -> drawPass(matrices, camera, now, consumer, false));
    }

    private static void drawPass(PoseStack matrices, Vec3 camera, double now,
            VertexConsumer consumer, boolean glow) {
        for (Slash slash : SLASHES) {
            double age = now - slash.bornGameTime();
            double linear = Mth.clamp(age / 1.6D, 0.0D, 1.0D);
            double head = 1.0D - Math.pow(1.0D - linear, 2.0D);
            double tail = Math.max(0.0D, head - 0.55D);
            if (age > 1.6D) {
                tail += (head - tail) * Mth.clamp((age - 1.6D) / (LIFE_TICKS - 1.6D), 0.0D, 1.0D);
            }
            float fade = (float) Math.pow(1.0D - Mth.clamp(age / LIFE_TICKS, 0.0D, 1.0D), 0.55D);
            matrices.pushPose();
            matrices.translate(slash.from().x - camera.x, slash.from().y - camera.y, slash.from().z - camera.z);
            Vec3 delta = slash.to().subtract(slash.from());
            Vec3 normal = delta.normalize();
            PoseStack.Pose pose = matrices.last();
            float r = 1.00F;
            float g = glow ? 0.12F : 0.45F;
            float b = glow ? 0.06F : 0.18F;
            float alpha = fade * (glow ? 0.28F : 0.95F);
            for (int segment = 0; segment < 6; segment++) {
                double startFactor = segment / 6.0D;
                double endFactor = (segment + 1) / 6.0D;
                double start = tail + (head - tail) * startFactor;
                double end = tail + (head - tail) * endFactor;
                float startAlpha = alpha * (0.08F + 0.92F * (float) startFactor);
                float endAlpha = alpha * (0.08F + 0.92F * (float) endFactor);
                consumer.addVertex(pose, (float) (delta.x * start), (float) (delta.y * start),
                        (float) (delta.z * start)).setColor(r, g, b, startAlpha)
                        .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
                consumer.addVertex(pose, (float) (delta.x * end), (float) (delta.y * end),
                        (float) (delta.z * end)).setColor(r, g, b, endAlpha)
                        .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
            }
            matrices.popPose();
        }
    }
}
