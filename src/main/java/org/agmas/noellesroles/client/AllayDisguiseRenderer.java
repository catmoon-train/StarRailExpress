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
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

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
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.role_data.neutral.PhantomSpiritRoleData;

/**
 * 幻灵：把玩家渲染成一只悦灵。
 * 每个伪装玩家持有一只不入世界的客户端悦灵实体，逐帧复制玩家的位置与姿态后交给悦灵渲染器绘制。
 * 走与皮革噶相同的 PlayerRenderer 替换路径（见 LeatherPigPlayerRenderMixin）。
 */
public class AllayDisguiseRenderer {
    private static final Map<UUID, Allay> ALLAYS = new HashMap<>();

    /**
     * 第一人称下把悦灵沿身体朝向往后挪，避免模型糊在相机上挡住视野。
     * 相机本身不动，准星射线仍从实体原点出发。
     */
    private static final float SELF_VIEW_BACK = 0.45F;

    // ==== 悦灵头几何（AllayModel：root (0,23.5,0)，head 相对 root (0,-3.99,0)，5×5×5） ====
    // 模型坐标 -> 实体根坐标：y = 1.501 - my/16，向前 = -mz/16。
    /** 头枢轴距脚底：1.501 - 19.51/16 */
    private static final float HEAD_PIVOT_Y = 1.501F - 19.51F / 16F;
    /** 头枢轴在身体中心，无前后偏移 */
    private static final float HEAD_PIVOT_FORWARD = 0.0F;
    /** 枢轴在头底，脸中心再抬 2.5/16 */
    private static final float FACE_CENTER_Y = 2.5F / 16F;
    /** 枢轴到头正面：2.5/16 */
    private static final float FACE_FORWARD = 2.5F / 16F;
    /** 脸部半宽/半高（悦灵头正面 5×5 像素） */
    private static final float FACE_HALF = 2.5F / 16F;
    private static final float FACE_EPSILON = 0.005F;
    private static final float FACE_HAT_OFFSET = 0.012F;

    private static final float BASE_U0 = 8F / 64F, BASE_U1 = 16F / 64F;
    private static final float BASE_V0 = 8F / 64F, BASE_V1 = 16F / 64F;
    private static final float HAT_U0 = 40F / 64F, HAT_U1 = 48F / 64F;
    private static final float HAT_V0 = 8F / 64F, HAT_V1 = 16F / 64F;

    public static boolean shouldDisguise(AbstractClientPlayer player) {
        return PhantomSpiritRoleData.isDisguised(player);
    }

    public static boolean render(AbstractClientPlayer player, float yaw, float tickDelta,
            PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        Allay allay = getAllay(player);
        if (allay == null) {
            return false;
        }
        Minecraft minecraft = Minecraft.getInstance();
        boolean firstPersonSelf = minecraft.getCameraEntity() == player
                && minecraft.options.getCameraType().isFirstPerson();
        if (allay.tickCount != player.tickCount) {
            allay.walkAnimation.update(player.walkAnimation.speed(), 1.0f);
            allay.tickCount = player.tickCount;
        }
        allay.setPos(player.getX(), player.getY(), player.getZ());
        allay.xo = player.xo;
        allay.yo = player.yo;
        allay.zo = player.zo;
        allay.yBodyRot = player.yBodyRot;
        allay.yBodyRotO = player.yBodyRotO;
        allay.yHeadRot = player.yHeadRot;
        allay.yHeadRotO = player.yHeadRotO;
        allay.setYRot(player.getYRot());
        allay.yRotO = player.yRotO;
        allay.setXRot(player.getXRot());
        allay.xRotO = player.xRotO;
        allay.setOnGround(false);
        allay.setInvisible(player.isInvisible());
        allay.hurtTime = player.hurtTime;
        allay.setCustomName(null);
        allay.setCustomNameVisible(false);

        poseStack.pushPose();
        if (firstPersonSelf) {
            Vec3 offset = allayBackOffset(player, tickDelta);
            poseStack.translate(-offset.x, 0.0, -offset.z);
        }

        EntityRenderer<? super Allay> renderer = minecraft.getEntityRenderDispatcher().getRenderer(allay);
        renderer.render(allay, yaw, tickDelta, poseStack, bufferSource, packedLight);
        if (!player.isInvisible() && !firstPersonSelf) {
            renderPlayerFace(player, tickDelta, poseStack, bufferSource, packedLight);
        }
        poseStack.popPose();
        return true;
    }

    private static void renderPlayerFace(AbstractClientPlayer player, float tickDelta,
            PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        ResourceLocation skin = player.getSkin().texture();
        float bodyYaw = Mth.rotLerp(tickDelta, player.yBodyRotO, player.yBodyRot);
        float headYaw = Mth.rotLerp(tickDelta, player.yHeadRotO, player.yHeadRot);
        float headPitch = Mth.lerp(tickDelta, player.xRotO, player.getXRot());
        float netHeadYaw = Mth.clamp(Mth.wrapDegrees(headYaw - bodyYaw), -85.0F, 85.0F);
        bodyYaw = headYaw - netHeadYaw;
        if (netHeadYaw * netHeadYaw > 2500.0F) {
            bodyYaw += netHeadYaw * 0.2F;
        }
        netHeadYaw = headYaw - bodyYaw;

        poseStack.pushPose();
        poseStack.translate(0.0F, HEAD_PIVOT_Y, 0.0F);
        poseStack.mulPose(Axis.YP.rotationDegrees(-bodyYaw));
        poseStack.translate(0.0F, 0.0F, HEAD_PIVOT_FORWARD);
        poseStack.mulPose(Axis.YP.rotationDegrees(-netHeadYaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(headPitch));
        poseStack.translate(0.0F, FACE_CENTER_Y, 0.0F);

        VertexConsumer consumer = bufferSource.getBuffer(RenderType.entityCutoutNoCull(skin));
        addFaceQuad(consumer, poseStack, FACE_HALF, FACE_FORWARD + FACE_EPSILON,
                BASE_U0, BASE_V0, BASE_U1, BASE_V1, packedLight);
        if (player.isModelPartShown(PlayerModelPart.HAT)) {
            addFaceQuad(consumer, poseStack, FACE_HALF + FACE_HAT_OFFSET, FACE_FORWARD + FACE_HAT_OFFSET,
                    HAT_U0, HAT_V0, HAT_U1, HAT_V1, packedLight);
        }
        poseStack.popPose();
    }

    private static void addFaceQuad(VertexConsumer consumer, PoseStack poseStack, float half, float zFront,
            float u0, float v0, float u1, float v1, int packedLight) {
        PoseStack.Pose pose = poseStack.last();
        var matrix = pose.pose();
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

    private static Vec3 allayBackOffset(AbstractClientPlayer player, float tickDelta) {
        float bodyYaw = Mth.rotLerp(tickDelta, player.yBodyRotO, player.yBodyRot);
        float radians = bodyYaw * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(radians), 0.0, Mth.cos(radians)).scale(SELF_VIEW_BACK);
    }

    private static Allay getAllay(AbstractClientPlayer player) {
        Allay allay = ALLAYS.get(player.getUUID());
        if (allay == null || allay.level() != player.level()) {
            allay = EntityType.ALLAY.create(player.level());
            if (allay != null) {
                allay.setNoAi(true);
                allay.setSilent(true);
                ALLAYS.put(player.getUUID(), allay);
            }
        }
        return allay;
    }
}
