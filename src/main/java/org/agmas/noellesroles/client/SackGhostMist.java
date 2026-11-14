package org.agmas.noellesroles.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** 有轮廓的黑雾剪影：主体稳定、边缘缓慢逸散，不用绕圈烟球堆出人物。 */
public final class SackGhostMist {
    private static final RenderType MIST = RenderType.entityTranslucent(SackGhostRenderer.TEXTURE);
    private static final RenderType OUTLINE = RenderType.outline(SackGhostRenderer.TEXTURE);
    private static final RenderType EYES = RenderType.entityTranslucentEmissive(SackGhostRenderer.TEXTURE);
    private static final int MAX_TRAILS = 128;
    private static final int TRAIL_LENGTH = 5;
    private static final int SEGMENTS = 16;
    private static final float WHITE_UV = 63.5F / 64;
    private static final Map<UUID, Trail> TRAILS = new HashMap<>();

    private static final class Trail {
        final AbstractClientPlayer player;
        final ArrayDeque<Vec3> points = new ArrayDeque<>();

        Trail(AbstractClientPlayer player) { this.player = player; }
    }

    private SackGhostMist() {}

    /** 暂停时仅清理；静止的历史点会自然替换掉移动尾迹。 */
    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        TRAILS.values().removeIf(trail -> trail.player.level() != mc.level || trail.player.isRemoved()
                || !trail.player.isAlive() || (SackGhostClient.formFlags(trail.player) & 3) == 0
                || (SackGhostClient.formFlags(trail.player) & 4) != 0
                || mc.player == null || mc.player.distanceToSqr(trail.player) > 64 * 64);
        if (!SackGhostClient.clockRunning()) return;
        for (Trail trail : TRAILS.values()) {
            Vec3 position = trail.player.position();
            if (!trail.points.isEmpty() && trail.points.getFirst().distanceToSqr(position) > 9) trail.points.clear();
            trail.points.addFirst(position);
            if (trail.points.size() > TRAIL_LENGTH) trail.points.removeLast();
        }
    }

    static void clear() { TRAILS.clear(); }

    /** 原点在脚底；低档也保留完整剪影，隐身照诡只提交轮廓而不泄露眼睛和尾迹。 */
    static void body(AbstractClientPlayer player, PoseStack poses, MultiBufferSource buffers,
                     float partial, float time, int detail, boolean outlineOnly) {
        float phase = time + player.getId() * 0.73F;
        cloud(poses, buffers, phase, 1, 1, detail, outlineOnly ? OUTLINE : MIST);
        if (outlineOnly) return;
        eyes(player, poses, buffers, partial, phase);
        Trail trail = TRAILS.get(player.getUUID());
        if (trail == null || trail.player != player) {
            if (TRAILS.size() >= MAX_TRAILS) return;
            trail = new Trail(player);
            TRAILS.put(player.getUUID(), trail);
        }
        Vec3 current = new Vec3(Mth.lerp(partial, player.xo, player.getX()),
                Mth.lerp(partial, player.yo, player.getY()), Mth.lerp(partial, player.zo, player.getZ()));
        int step = 0;
        for (Vec3 point : trail.points) {
            step++;
            Vec3 offset = point.subtract(current);
            if (offset.lengthSqr() < 0.02 || offset.lengthSqr() > 9 || step % 2 == 0) continue;
            float fade = (1 - step / (TRAIL_LENGTH + 1F)) * 0.18F;
            int layers = detail > 0 ? 2 : 1;
            for (int i = 0; i < layers; i++) {
                float drift = phase * 0.018F + step * 1.7F + i;
                puff(poses, buffers, (float) offset.x + Mth.sin(drift) * 0.025F,
                        (float) offset.y + 0.55F + i * 0.42F,
                        (float) offset.z + Mth.cos(drift) * 0.025F,
                        0.15F + step * 0.012F, 0.36F, drift, fade, 0x0D0C11);
            }
        }
    }

    /** 短暂离体与回归复用同一轮廓，避免过场与主体出现两种不同的烟球形状。 */
    static void cloud(PoseStack poses, MultiBufferSource buffers, float time, float scale, float alpha, int detail) {
        cloud(poses, buffers, time, scale, alpha, detail, MIST);
    }

    private static void cloud(PoseStack poses, MultiBufferSource buffers, float time, float scale,
                              float alpha, int detail, RenderType layer) {
        float breath = Mth.sin(time * 0.025F) * 0.012F;
        float sway = Mth.sin(time * 0.018F) * 0.018F;
        poses.pushPose();
        poses.scale(scale, scale, scale);
        // 六片互相衔接的雾体形成头、肩、躯干和渐收下摆，不向四周公转。
        puff(poses, buffers, sway, 1.5F + breath, 0.025F, 0.225F, 0.265F, 0.04F, alpha * 0.82F, 0x0B0A0F, layer, true);
        puff(poses, buffers, sway * 0.6F, 1.02F + breath, 0, 0.295F, 0.53F, -0.03F, alpha * 0.68F, 0x08080B, layer, true);
        puff(poses, buffers, -0.18F + sway, 1.21F + breath, 0.015F, 0.21F, 0.32F, -0.12F, alpha * 0.4F, 0x100F14, layer, true);
        puff(poses, buffers, 0.18F + sway, 1.19F + breath, 0.02F, 0.21F, 0.32F, 0.12F, alpha * 0.4F, 0x100F14, layer, true);
        puff(poses, buffers, 0.01F - sway * 0.5F, 0.53F + breath, -0.015F, 0.185F, 0.46F, sway, alpha * 0.58F, 0x0B0A10, layer, true);
        puff(poses, buffers, -sway, 0.26F + breath, -0.025F, 0.095F, 0.22F, -sway, alpha * 0.36F, 0x121017, layer, true);
        // 细节档只增加内部层次与细长逸散，不把主体变成更多独立大圆团。
        for (int i = 0; i < detail * 2; i++) {
            float phase = time * 0.018F + i * 1.7F;
            puff(poses, buffers, Mth.sin(phase) * 0.035F, 0.55F + i * 0.24F + breath,
                    Mth.cos(phase) * 0.025F, 0.13F, 0.36F, phase * 0.05F,
                    alpha * 0.12F, 0x17161C, layer, true);
        }
        int wisps = 2 + detail * 2;
        for (int i = 0; i < wisps; i++) {
            float phase = (time * 0.0035F + i / (float) wisps) % 1;
            float side = i % 2 == 0 ? -1 : 1;
            float drift = Mth.sin(time * 0.015F + i * 1.7F) * 0.035F;
            puff(poses, buffers, side * (0.22F + phase * 0.1F) + drift, 0.4F + phase * 1.1F,
                    Mth.cos(i * 1.7F) * 0.06F, 0.045F + phase * 0.018F, 0.22F + phase * 0.17F,
                    side * 0.13F, alpha * (1 - phase) * 0.17F, 0x151419, layer, true);
        }
        poses.popPose();
    }

    /** 低亮细缝只朝向本人面朝方向，不做总是看向观察者的卡通大眼。 */
    private static void eyes(AbstractClientPlayer player, PoseStack poses, MultiBufferSource buffers, float partial, float time) {
        float yaw = Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot);
        float height = 1.505F + Mth.sin(time * 0.025F) * 0.012F;
        float intensity = 0.44F + Mth.sin(time * 0.02F) * 0.025F;
        poses.pushPose();
        poses.mulPose(Axis.YP.rotationDegrees(-yaw));
        for (int side : new int[]{-1, 1}) {
            puff(poses, buffers, side * 0.08F, height, 0.245F, 0.06F, 0.025F,
                    side * 0.1F, 0.1F, 0x5E1621, EYES, false);
            puff(poses, buffers, side * 0.08F, height, 0.247F, 0.038F, 0.009F,
                    side * 0.1F, intensity, 0x9D2532, EYES, false);
        }
        poses.popPose();
    }

    static void puff(PoseStack poses, MultiBufferSource buffers, float x, float y, float z,
                     float width, float height, float spin, float alpha, int rgb) {
        puff(poses, buffers, x, y, z, width, height, spin, alpha, rgb, MIST, true);
    }

    static void viewPuff(PoseStack poses, MultiBufferSource buffers, float x, float y, float z,
                         float width, float height, float spin, float alpha) {
        puff(poses, buffers, x, y, z, width, height, spin, alpha, 0x0E0D12, MIST, false);
    }

    /** 两段径向衰减与更细边缘，消除十边形烟球和过硬的中心亮斑。 */
    private static void puff(PoseStack poses, MultiBufferSource buffers, float x, float y, float z,
                             float width, float height, float spin, float alpha, int rgb,
                             RenderType layer, boolean billboard) {
        if (alpha <= 0.01F) return;
        poses.pushPose();
        poses.translate(x, y, z);
        if (billboard) poses.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
        VertexConsumer out = buffers.getBuffer(layer);
        PoseStack.Pose pose = poses.last();
        int center = Mth.clamp((int) (alpha * 255), 0, 255);
        int middle = (int) (center * 0.52F);
        float cosine = Mth.cos(spin), sine = Mth.sin(spin);
        for (int i = 0; i < SEGMENTS; i++) {
            float a = i * Mth.TWO_PI / SEGMENTS;
            float b = (i + 1) * Mth.TWO_PI / SEGMENTS;
            float x1 = Mth.cos(a) * width, y1 = Mth.sin(a) * height;
            float x2 = Mth.cos(b) * width, y2 = Mth.sin(b) * height;
            float ax = x1 * cosine - y1 * sine, ay = x1 * sine + y1 * cosine;
            float bx = x2 * cosine - y2 * sine, by = x2 * sine + y2 * cosine;
            vertex(out, pose, 0, 0, rgb, center);
            vertex(out, pose, ax * 0.42F, ay * 0.42F, rgb, middle);
            vertex(out, pose, bx * 0.42F, by * 0.42F, rgb, middle);
            vertex(out, pose, 0, 0, rgb, center);
            vertex(out, pose, ax * 0.42F, ay * 0.42F, rgb, middle);
            vertex(out, pose, ax, ay, rgb, 0);
            vertex(out, pose, bx, by, rgb, 0);
            vertex(out, pose, bx * 0.42F, by * 0.42F, rgb, middle);
        }
        poses.popPose();
    }

    private static void vertex(VertexConsumer out, PoseStack.Pose pose, float x, float y, int rgb, int alpha) {
        // 对准白色纹素中心，避免过滤时采到邻格颜色。
        out.addVertex(pose, x, y, 0).setColor((alpha << 24) | rgb).setUv(WHITE_UV, WHITE_UV)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(pose, 0, 0, 1);
    }
}
