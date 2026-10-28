package io.wifi.starrailexpress.settlement.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EnderDragonRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** 末影龙结算皮肤的纯客户端布景；龙不加入世界、不 tick，不产生 AI、伤害或网络实体。 */
final class SettlementDragonScene {
    private static final ResourceLocation SKY = ResourceLocation.withDefaultNamespace("textures/environment/end_sky.png");
    private static EnderDragon dragon;

    private SettlementDragonScene() { }
    static void clear() { dragon = null; }

    /** 坐标已转换到舞台原点；背景位于黑箱内侧，隔绝原维度的天空与墙面。 */
    static void render(PoseStack pose, MultiBufferSource buffers, double time) {
        var mc = Minecraft.getInstance();
        sky(pose, buffers);
        if (mc.level == null) return;
        if (dragon == null || dragon.level() != mc.level) dragon = new EnderDragon(EntityType.ENDER_DRAGON, mc.level);
        double angle = time * .018;
        float yaw = (float) (90 - Math.toDegrees(angle));
        dragon.setYRot(yaw);
        dragon.flapTime = dragon.oFlapTime = (float) (time * .035);
        dragon.posPointer = 0;
        for (double[] position : dragon.positions) { position[0] = yaw; position[1] = 0; }
        pose.pushPose();
        pose.translate(Math.sin(angle) * 6, 6.2 + Math.sin(angle * 2) * .45, 4 + Math.cos(angle) * 3);
        pose.scale(.42f, .42f, .42f);
        // 直接调用原版渲染器，不经过被结算隔离的真实实体分发器。
        if (mc.getEntityRenderDispatcher().getRenderer(dragon) instanceof EnderDragonRenderer renderer)
            renderer.render(dragon, yaw, 0, pose, buffers, LightTexture.FULL_BRIGHT);
        pose.popPose();
    }

    private static void sky(PoseStack pose, MultiBufferSource buffers) {
        VertexConsumer out = buffers.getBuffer(RenderType.entitySolid(SKY));
        // 六面末地天空纹理；箱体在墙体之前，地面保留真实末地石和黑曜石柱。
        pose.pushPose();
        pose.translate(0, 6, 0);
        for (int side = 0; side < 6; side++) {
            pose.pushPose();
            if (side < 4) pose.mulPose(Axis.YP.rotationDegrees(side * 90));
            else pose.mulPose(Axis.XP.rotationDegrees(side == 4 ? 90 : -90));
            // 深度为 14.4，所有面都在黑箱内，最上面不超过顶盖。
            float height = side < 4 ? 7.6f : 14.4f;
            float depth = side == 4 ? 6.8f : side == 5 ? 6.9f : 14.4f;
            vertex(out, pose, -15.4f, -height, depth, 0, 0);
            vertex(out, pose, -15.4f, height, depth, 0, 4);
            vertex(out, pose, 15.4f, height, depth, 4, 4);
            vertex(out, pose, 15.4f, -height, depth, 4, 0);
            pose.popPose();
        }
        pose.popPose();
    }

    private static void vertex(VertexConsumer out, PoseStack pose, float x, float y, float z, float u, float v) {
        out.addVertex(pose.last(), x, y, z).setColor(0xFF30243F).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose.last(), 0, 0, -1);
    }

    /** 原版死亡光束的双层三角锥，缩小到角色周围；与相机和服务端粒子完全无关。 */
    static void winnerRays(PoseStack pose, MultiBufferSource buffers, double x, double y, double z,
                           double time, int order) {
        var snapshot = SettlementClient.snapshot();
        double fade = SettlementClient.smooth((time - 140) / 45)
                * (1 - SettlementClient.smooth((time - snapshot.durationTicks() + 80) / 55));
        if (fade <= .001) return;
        pose.pushPose();
        pose.translate(x, y + .9, z);
        pose.scale((float) fade * .15f, (float) fade * .15f, (float) fade * .15f);
        pose.mulPose(Axis.YP.rotationDegrees((float) time * .6f + order * 37));
        rays(pose, buffers.getBuffer(RenderType.dragonRays()));
        rays(pose, buffers.getBuffer(RenderType.dragonRaysDepth()));
        pose.popPose();
    }

    private static void rays(PoseStack pose, VertexConsumer out) {
        pose.pushPose();
        RandomSource random = RandomSource.create(432);
        Quaternionf rotation = new Quaternionf();
        Vector3f origin = new Vector3f();
        for (int i = 0; i < 32; i++) {
            rotation.rotationXYZ(random.nextFloat() * (float) (Math.PI * 2), random.nextFloat() * (float) (Math.PI * 2),
                    random.nextFloat() * (float) (Math.PI * 2));
            pose.mulPose(rotation);
            float length = 5 + random.nextFloat() * 20, width = 1 + random.nextFloat() * 2;
            Vector3f left = new Vector3f(-.8660254f * width, length, -.5f * width);
            Vector3f right = new Vector3f(.8660254f * width, length, -.5f * width);
            Vector3f tip = new Vector3f(0, length, width);
            triangle(out, pose, origin, left, right);
            triangle(out, pose, origin, right, tip);
            triangle(out, pose, origin, tip, left);
        }
        pose.popPose();
    }

    private static void triangle(VertexConsumer out, PoseStack pose, Vector3f origin, Vector3f a, Vector3f b) {
        out.addVertex(pose.last(), origin).setColor(0xC0ECD6FF);
        out.addVertex(pose.last(), a).setColor(0x00B43FFF);
        out.addVertex(pose.last(), b).setColor(0x00B43FFF);
    }
}
