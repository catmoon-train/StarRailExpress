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
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.game.roles.innocence.leather_pig.LeatherPigPlayerComponent;

/**
 * 皮革噶的：把玩家渲染成一头猪。
 * 每个伪装玩家持有一只不入世界的客户端猪实体，逐帧复制玩家的位置与姿态后交给猪渲染器绘制。
 */
public class LeatherPigDisguiseRenderer {
    private static final Map<UUID, Pig> PIGS = new HashMap<>();

    // ==== 猪头几何（PigModel：head 枢轴在模型 (0,12,-6)，8×8×8 的头，前方再伸出 1px 猪吻） ====
    // 模型坐标 -> 实体根坐标：y = 1.501 - my/16，向前 = -mz/16。
    /** 猪头旋转枢轴距脚底的高度：1.501 - 12/16 */
    private static final float HEAD_PIVOT_Y = 0.751F;
    /** 猪头旋转枢轴相对身体中心向前的偏移：6/16 */
    private static final float HEAD_PIVOT_FORWARD = 0.375F;
    /** 枢轴到猪头正面的距离：8/16 */
    private static final float FACE_FORWARD = 0.5F;
    /** 脸部半宽/半高（猪头正面恰好 8×8 像素） */
    private static final float FACE_HALF = 0.25F;
    /** 贴面外移量，避免与猪头正面 z-fighting */
    private static final float FACE_EPSILON = 0.005F;
    /** 皮肤第二层（帽层）相对第一层向外的偏移，形成双层皮肤 */
    private static final float FACE_HAT_OFFSET = 0.02F;

    // 皮肤贴图中脸部的 UV（64x64 皮肤，归一化）
    private static final float BASE_U0 = 8F / 64F, BASE_U1 = 16F / 64F;
    private static final float BASE_V0 = 8F / 64F, BASE_V1 = 16F / 64F;
    private static final float HAT_U0 = 40F / 64F, HAT_U1 = 48F / 64F;
    private static final float HAT_V0 = 8F / 64F, HAT_V1 = 16F / 64F;

    // ==== 第一人称的自己 ====
    // 相机在实体原点上方（眼高已由 LeatherPigEyeHeightMixin 降到猪的眼高），而猪眼在实体原点
    // 【前方】0.875 格（枢轴 0.375 + 枢轴到脸 0.5）。原样画的话，正前方 0.375 格处就是后脑勺的
    // 背面，整个视野一片粉。所以只对自己把整只猪往后挪，让猪眼落到相机上：猪头退到相机之后，
    // 低头就能看见自己的猪吻。相机本身不动——它一动，准星射线（仍从实体原点出发）就会和画面
    // 产生俯仰视差，枪打不准。
    /** 猪脸再退到相机之后的余量，需大于 FACE_HAT_OFFSET，免得猪脸和相机共面 */
    private static final float SELF_VIEW_CLEARANCE = 0.02F;

    public static boolean shouldDisguise(AbstractClientPlayer player) {
        LeatherPigPlayerComponent component = LeatherPigPlayerComponent.KEY.maybeGet(player).orElse(null);
        return component != null && component.isDisguised();
    }

    /**
     * 猪眼相对实体原点的水平偏移。沿 PigModel 的骨骼层级拆成两段：枢轴段跟随身体偏航，
     * 脸部段跟随头部偏航。俯仰不参与——渲染时猪头的 xRot 恒为 0。
     */
    private static Vec3 pigEyeOffset(AbstractClientPlayer player, float tickDelta) {
        float bodyYaw = Mth.rotLerp(tickDelta, player.yBodyRotO, player.yBodyRot);
        float headYaw = Mth.rotLerp(tickDelta, player.yHeadRotO, player.yHeadRot);
        return forward(bodyYaw).scale(HEAD_PIVOT_FORWARD)
                .add(forward(headYaw).scale(FACE_FORWARD + SELF_VIEW_CLEARANCE));
    }

    /** yaw 对应的水平前方向量（yaw=0 指向 +Z）。 */
    private static Vec3 forward(float yawDegrees) {
        float radians = yawDegrees * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(radians), 0.0, Mth.cos(radians));
    }

    public static boolean render(AbstractClientPlayer player, float yaw, float tickDelta,
            PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        Pig pig = getPig(player);
        if (pig == null) {
            return false;
        }
        Minecraft minecraft = Minecraft.getInstance();
        // 第一人称下的自己：猪照画，但要整体后移，让猪眼落到相机上
        boolean firstPersonSelf = minecraft.getCameraEntity() == player
                && minecraft.options.getCameraType().isFirstPerson();
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
        // 猪头的俯仰角直接取自实体 xRot（QuadrupedModel 把 headPitch 原样写进 head.xRot），
        // 而猪头枢轴在颈后、整头只有 0.9 格高：玩家稍一低头，猪头就整个扎进地里，看上去一直低着头。
        // 伪装只跟随偏航即可，俯仰恒为 0，脸部贴图也随之保持水平。
        pig.setXRot(0.0F);
        pig.xRotO = 0.0F;
        pig.setInvisible(player.isInvisible());
        pig.hurtTime = player.hurtTime;
        // 伪装期间不显示名字；玩家本体的名牌已随 PlayerRenderer.render 一起被取消。
        pig.setCustomName(null);
        pig.setCustomNameVisible(false);

        poseStack.pushPose();
        if (firstPersonSelf) {
            Vec3 offset = pigEyeOffset(player, tickDelta);
            poseStack.translate(-offset.x, 0.0, -offset.z);
        }

        EntityRenderer<? super Pig> renderer = minecraft.getEntityRenderDispatcher().getRenderer(pig);
        renderer.render(pig, yaw, tickDelta, poseStack, bufferSource, packedLight);

        // 在猪头上叠加玩家自己皮肤的脸部（含双层皮肤）。
        // 第一人称的自己不画：脸部是无剔除四边形，此时正贴在相机上，会从背面糊住整个视野。
        if (!player.isInvisible() && !firstPersonSelf) {
            renderPlayerFace(player, tickDelta, poseStack, bufferSource, packedLight);
        }
        poseStack.popPose();
        return true;
    }

    /**
     * 在猪头正面渲染玩家自身皮肤的脸部（第一层 + 帽层，即双层皮肤），朝向跟随玩家头部。
     *
     * <p>脸部必须贴在猪头这块骨骼上，因此要沿着 PigModel 的层级走一遍：抬到枢轴高度 →
     * 转到身体坐标系 → 前移到枢轴 → 施加头部相对身体的偏航 → 在枢轴前方 8/16 处画脸。
     * 直接在实体根坐标系里"抬高 + 前移"会把脸埋进猪头内部，且转头时绕错枢轴摆动。
     */
    private static void renderPlayerFace(AbstractClientPlayer player, float tickDelta,
            PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        ResourceLocation skin = player.getSkin().texture();
        float bodyYaw = Mth.rotLerp(tickDelta, player.yBodyRotO, player.yBodyRot);
        float headYaw = Mth.rotLerp(tickDelta, player.yHeadRotO, player.yHeadRot);
        // 与 LivingEntityRenderer 保持一致：净偏航钳制到 ±85°，超过 50° 后身体被头带着转一点
        float netHeadYaw = Mth.clamp(Mth.wrapDegrees(headYaw - bodyYaw), -85.0F, 85.0F);
        bodyYaw = headYaw - netHeadYaw;
        if (netHeadYaw * netHeadYaw > 2500.0F) {
            bodyYaw += netHeadYaw * 0.2F;
        }
        netHeadYaw = headYaw - bodyYaw;

        poseStack.pushPose();
        // Axis.YP.rotationDegrees(-yaw) 使局部 +Z 指向该 yaw 的正前方
        poseStack.translate(0.0F, HEAD_PIVOT_Y, 0.0F);
        poseStack.mulPose(Axis.YP.rotationDegrees(-bodyYaw));
        poseStack.translate(0.0F, 0.0F, HEAD_PIVOT_FORWARD);
        poseStack.mulPose(Axis.YP.rotationDegrees(-netHeadYaw));

        // 无剔除渲染，避免正反面被裁剪；帽层透明像素由 cutout 丢弃露出第一层
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.entityCutoutNoCull(skin));
        addFaceQuad(consumer, poseStack, FACE_HALF, FACE_FORWARD + FACE_EPSILON,
                BASE_U0, BASE_V0, BASE_U1, BASE_V1, packedLight);
        if (player.isModelPartShown(PlayerModelPart.HAT)) {
            addFaceQuad(consumer, poseStack, FACE_HALF + FACE_HAT_OFFSET, FACE_FORWARD + FACE_HAT_OFFSET,
                    HAT_U0, HAT_V0, HAT_U1, HAT_V1, packedLight);
        }

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
