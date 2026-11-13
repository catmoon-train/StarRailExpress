package org.agmas.noellesroles.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** 每帧临时构造烟团，不创建实体或遗留粒子；撤回感知权限的当帧立即消失。 */
public final class RouletteDevilSmokeRenderer {
    // 复用原版柔边圆形贴图，黑色叠片形成没有肢体轮廓的浓烟体积。
    private static final RenderType SMOKE = RenderType.entityTranslucent(
            ResourceLocation.withDefaultNamespace("textures/misc/shadow.png"), false);

    private RouletteDevilSmokeRenderer() {}

    public static void render(WorldRenderContext context) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null || context.matrixStack() == null
                || context.consumers() == null) return;
        Vec3 camera = context.camera().getPosition();
        float partial = context.tickCounter().getGameTimeDeltaPartialTick(false);
        double time = client.level.getGameTime() + partial;
        VertexConsumer out = null;
        for (var id : RouletteDevilClient.visibleDemons()) {
            if (!RouletteDevilClient.canSeeSmoke(id)) continue;
            Player demon = client.level.getPlayerByUUID(id);
            if (demon == null || !demon.isAlive() || demon.isSpectator()) continue;
            if (demon == context.camera().getEntity() && !context.camera().isDetached()) continue;
            if (context.frustum() != null && !context.frustum().isVisible(demon.getBoundingBox().inflate(0.7))) continue;
            Vec3 position = new Vec3(Mth.lerp(partial, demon.xOld, demon.getX()),
                    Mth.lerp(partial, demon.yOld, demon.getY()), Mth.lerp(partial, demon.zOld, demon.getZ()));
            Vec3 center = position.add(0, 0.9, 0);
            // 同时遵守观察者视线和实际相机视线；材质保留正常深度测试，绝不穿墙。
            if (!client.player.hasLineOfSight(demon) || client.level.clip(new ClipContext(camera, center,
                    ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, client.player)).getType() != HitResult.Type.MISS) continue;
            if (out == null) out = context.consumers().getBuffer(SMOKE);
            double phase = (id.hashCode() & 1023) * 0.017;
            PoseStack poses = context.matrixStack();
            for (int i = 0; i < 56; i++) {
                int layer = i / 7;
                double angle = phase + i * 2.39996 + time * (0.025 + layer * 0.002);
                double radius = 0.18 + 0.13 * Math.sin(i * 1.7 + time * 0.035);
                double height = 0.12 + layer * 0.235 + 0.07 * Math.sin(time * 0.08 + i);
                float size = 0.40F + 0.10F * (float) Math.sin(time * 0.055 + i * 1.3);
                poses.pushPose();
                poses.translate(position.x + Math.cos(angle) * radius - camera.x,
                        position.y + height - camera.y, position.z + Math.sin(angle) * radius - camera.z);
                poses.mulPose(context.camera().rotation());
                PoseStack.Pose pose = poses.last();
                int alpha = 180 + (int) (40 * Math.sin(i + time * 0.045));
                vertex(out, pose, -size, -size, 0, 1, alpha);
                vertex(out, pose, size, -size, 1, 1, alpha);
                vertex(out, pose, size, size, 1, 0, alpha);
                vertex(out, pose, -size, size, 0, 0, alpha);
                poses.popPose();
            }
        }
    }

    private static void vertex(VertexConsumer out, PoseStack.Pose pose, float x, float y,
            float u, float v, int alpha) {
        out.addVertex(pose, x, y, 0).setColor(0, 0, 0, alpha).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(pose, 0, 0, 1);
    }
}
