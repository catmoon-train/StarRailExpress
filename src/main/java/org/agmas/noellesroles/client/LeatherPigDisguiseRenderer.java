package org.agmas.noellesroles.client;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import org.agmas.noellesroles.game.roles.innocence.leather_pig.LeatherPigPlayerComponent;

/**
 * 皮革噶的：把玩家渲染成一头猪。
 * 每个伪装玩家持有一只不入世界的客户端猪实体，逐帧复制玩家的位置与姿态后交给猪渲染器绘制。
 */
public class LeatherPigDisguiseRenderer {
    private static final Map<UUID, Pig> PIGS = new HashMap<>();

    // ==== 玩家脸部（贴在猪头上）几何参数（格；可按需微调） ====
    /** 脸部锚点相对脚底的高度（猪头高度） */
    private static final float FACE_HEIGHT = 0.62F;
    /** 脸部相对身体中心向前（猪吻方向）的偏移 */
    private static final float FACE_FORWARD = 0.46F;
    /** 脸部半宽/半高 */
    private static final float FACE_HALF = 0.26F;
    /** 皮肤第二层（帽层）相对第一层向前的偏移，形成双层皮肤 */
    private static final float FACE_HAT_OFFSET = 0.03F;

    // 皮肤贴图中脸部的 UV（64x64 皮肤，归一化）
    private static final float BASE_U0 = 8F / 64F, BASE_U1 = 16F / 64F;
    private static final float BASE_V0 = 8F / 64F, BASE_V1 = 16F / 64F;
    private static final float HAT_U0 = 40F / 64F, HAT_U1 = 48F / 64F;
    private static final float HAT_V0 = 8F / 64F, HAT_V1 = 16F / 64F;

    public static boolean shouldDisguise(AbstractClientPlayer player) {
        LeatherPigPlayerComponent component = LeatherPigPlayerComponent.KEY.maybeGet(player).orElse(null);
        return component != null && component.isDisguised();
    }

    public static boolean render(AbstractClientPlayer player, float yaw, float tickDelta,
            PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        Pig pig = getPig(player);
        if (pig == null) {
            return false;
        }
        // 行走动画每 tick 只推进一次，其余状态逐帧复制
        if (pig.tickCount != player.tickCount) {
            pig.walkAnimation.update(player.walkAnimation.speed(), 1.0f);
            pig.tickCount = player.tickCount;
        }
        pig.setPos(player.getX(), player.getY(), player.getZ());
        pig.xo = player.xo;
        pig.yo = player.yo;
        pig.zo = player.zo;
        pig.yBodyRot = player.yBodyRot;
        pig.yBodyRotO = player.yBodyRotO;
        pig.yHeadRot = player.yHeadRot;
        pig.yHeadRotO = player.yHeadRotO;
        pig.setXRot(player.getXRot());
        pig.xRotO = player.xRotO;
        pig.setInvisible(player.isInvisible());
        pig.hurtTime = player.hurtTime;
        pig.setCustomName(player.getDisplayName());
        pig.setCustomNameVisible(true);

        EntityRenderer<? super Pig> renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(pig);
        renderer.render(pig, yaw, tickDelta, poseStack, bufferSource, packedLight);

        // 在猪头上叠加玩家自己皮肤的脸部（含双层皮肤）
        if (!player.isInvisible()) {
            renderPlayerFace(player, tickDelta, poseStack, bufferSource, packedLight);
        }
        return true;
    }

    /**
     * 在猪头位置渲染玩家自身皮肤的脸部（第一层 + 帽层，即双层皮肤），朝向跟随玩家头部。
     */
    private static void renderPlayerFace(AbstractClientPlayer player, float tickDelta,
            PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        ResourceLocation skin = player.getSkin().texture();
        float headYaw = Mth.rotLerp(tickDelta, player.yHeadRotO, player.yHeadRot);
        float pitch = Mth.lerp(tickDelta, player.xRotO, player.getXRot());

        poseStack.pushPose();
        // 抬到猪头高度（世界竖直方向）
        poseStack.translate(0.0F, FACE_HEIGHT, 0.0F);
        // 朝向玩家头部：Axis.YP.rotationDegrees(-yaw) 使局部 +Z 指向玩家朝向
        poseStack.mulPose(Axis.YP.rotationDegrees(-headYaw));
        // 俯仰
        poseStack.mulPose(Axis.XP.rotationDegrees(pitch));

        // 无剔除渲染，避免正反面被裁剪；帽层透明像素由 cutout 丢弃露出第一层
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.entityCutoutNoCull(skin));
        addFaceQuad(consumer, poseStack, FACE_HALF, FACE_FORWARD, BASE_U0, BASE_V0, BASE_U1, BASE_V1, packedLight);
        addFaceQuad(consumer, poseStack, FACE_HALF + FACE_HAT_OFFSET, FACE_FORWARD + FACE_HAT_OFFSET,
                HAT_U0, HAT_V0, HAT_U1, HAT_V1, packedLight);

        poseStack.popPose();
    }

    /**
     * 在局部 XY 平面、z = zFront 处绘制一张朝向 +Z 的脸部四边形。
     */
    private static void addFaceQuad(VertexConsumer consumer, PoseStack poseStack, float half, float zFront,
            float u0, float v0, float u1, float v1, int packedLight) {
        PoseStack.Pose pose = poseStack.last();
        var matrix = pose.pose();
        // 顶部 = 局部 +Y；纹理 v 向下增大
        addVertex(consumer, pose, matrix, -half, half, zFront, u0, v0, packedLight);
        addVertex(consumer, pose, matrix, -half, -half, zFront, u0, v1, packedLight);
        addVertex(consumer, pose, matrix, half, -half, zFront, u1, v1, packedLight);
        addVertex(consumer, pose, matrix, half, half, zFront, u1, v0, packedLight);
    }

    private static void addVertex(VertexConsumer consumer, PoseStack.Pose pose, org.joml.Matrix4f matrix,
            float x, float y, float z, float u, float v, int packedLight) {
        consumer.addVertex(matrix, x, y, z)
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(packedLight)
                .setNormal(pose, 0.0F, 0.0F, 1.0F);
    }

    private static Pig getPig(AbstractClientPlayer player) {
        Pig pig = PIGS.get(player.getUUID());
        if (pig == null || pig.level() != player.level()) {
            pig = EntityType.PIG.create(player.level());
            if (pig != null) {
                PIGS.put(player.getUUID(), pig);
            }
        }
        return pig;
    }
}
