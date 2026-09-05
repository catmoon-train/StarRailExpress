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

package org.agmas.noellesroles.gunfx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 枪械射击轨迹：弹头沿弹道快速掠过，尾迹渐隐，并参与深度测试。
 */
public final class GunTracerRenderer {
    private static final double LIFE_TICKS = 8.0D;
    private static final int GRADIENT_SEGMENTS = 10;
    private static final List<Tracer> TRACERS = new ArrayList<>();

    private record Tracer(Vec3 from, Vec3 to, double travelTicks, long bornGameTime) {
    }

    private GunTracerRenderer() {
    }

    /** 客户端收包：本地第一人称用镜头侧枪口，避免弹道从脸旁飞出。 */
    public static void onPacket(GunTracerS2CPacket packet) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) {
            return;
        }
        Vec3 from = new Vec3(packet.fromX(), packet.fromY(), packet.fromZ());
        Vec3 to = new Vec3(packet.toX(), packet.toY(), packet.toZ());
        if (client.player != null && client.player.getId() == packet.shooterId()
                && client.options.getCameraType().isFirstPerson()) {
            Vec3 eye = client.player.getEyePosition(1.0F);
            Vec3 view = client.player.getViewVector(1.0F).normalize();
            from = WeaponTrailGeometry.firstPersonMuzzle(eye, view, client.player.getMainArm());
        }
        if (to.distanceToSqr(from) < 0.04D) {
            return;
        }
        double distance = from.distanceTo(to);
        double travelTicks = Mth.clamp(0.85D + distance / 38.0D, 1.15D, 3.4D);
        TRACERS.add(new Tracer(from, to, travelTicks, client.level.getGameTime()));
    }

    public static void render(WorldRenderContext context) {
        if (TRACERS.isEmpty()) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) {
            TRACERS.clear();
            return;
        }
        double now = client.level.getGameTime()
                + client.getTimer().getGameTimeDeltaPartialTick(false);
        pruneExpired(now);
        if (TRACERS.isEmpty()) {
            return;
        }
        Vec3 cameraPos = context.camera().getPosition();
        PoseStack matrices = context.matrixStack();
        WeaponTrailImmediateDraw.drawLines(3.6F,
                consumer -> drawPass(matrices, cameraPos, now, consumer, true));
        WeaponTrailImmediateDraw.drawLines(1.05F,
                consumer -> drawPass(matrices, cameraPos, now, consumer, false));
    }

    private static void pruneExpired(double now) {
        for (Iterator<Tracer> it = TRACERS.iterator(); it.hasNext();) {
            if (now - it.next().bornGameTime() >= LIFE_TICKS) {
                it.remove();
            }
        }
    }

    private static void drawPass(PoseStack matrices, Vec3 cameraPos, double now,
            VertexConsumer consumer, boolean glow) {
        for (Tracer tracer : TRACERS) {
            double age = now - tracer.bornGameTime();
            double distance = tracer.from().distanceTo(tracer.to());
            double linearHead = clamp(age / tracer.travelTicks());
            double head = 1.0D - Math.pow(1.0D - linearHead, 2.4D);
            double streakLength = Math.max(1.05D, Math.min(5.8D, distance * 0.22D));
            double tail = Math.max(0.0D, head - streakLength / distance);
            if (age > tracer.travelTicks()) {
                double shrink = clamp((age - tracer.travelTicks()) / (LIFE_TICKS - tracer.travelTicks()));
                tail += (head - tail) * (0.35D + 0.65D * shrink);
            }
            float fade = (float) Math.pow(1.0D - clamp(age / LIFE_TICKS), 0.55D);
            matrices.pushPose();
            matrices.translate(tracer.from().x - cameraPos.x, tracer.from().y - cameraPos.y,
                    tracer.from().z - cameraPos.z);
            gradientLine(matrices.last(), consumer, tracer.to().subtract(tracer.from()),
                    tail, head, 1.00F, glow ? 0.78F : 0.96F, glow ? 0.38F : 0.82F,
                    fade * (glow ? 0.20F : 0.90F));
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
            float startAlpha = alpha * (0.04F + 0.96F * (float) Math.pow(startFactor, 1.35D));
            float endAlpha = alpha * (0.04F + 0.96F * (float) Math.pow(endFactor, 1.35D));
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
