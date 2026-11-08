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

package org.agmas.noellesroles.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.UUID;

import org.agmas.noellesroles.content.effects.TimeStopEffect;
import org.agmas.noellesroles.content.entity.BambooSpearEntity;
import org.agmas.noellesroles.init.ModEffects;

/**
 * 竹枪直刺：固定长度的枪身整体向前移动，不能再从眼睛处逐段长出竹杆。
 * 枪尖跟随原有同步距离，和服务端命中射线重合；枪尾向持枪手一侧倾斜，
 * 避免第一人称只看到正对相机的截面。攻击期间的手持物品模型由 spear mixin 隐藏。
 * 本地第一人称使用相机视线，第三人称与远端使用持有者视线，
 * 持有者不在追踪范围内时仍退回服务端同步的眼睛和方向。
 */
public class BambooSpearRenderer extends EntityRenderer<BambooSpearEntity> {

    private static final float RADIUS = 0.08F;
    private static final float TIP_LENGTH = 0.38F;

    public BambooSpearRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0f;
    }

    @Override
    public void render(BambooSpearEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
            MultiBufferSource bufferSource, int packedLight) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer viewer = minecraft.player;
        if (viewer != null && viewer.hasEffect(ModEffects.TIME_STOP)
                && !TimeStopEffect.clientCanMovePlayers.contains(viewer.getUUID())) {
            return;
        }

        float length = entity.getInterpolatedLength(partialTick);
        UUID ownerUuid = entity.getOwnerUuid();
        Player owner = entity.getOwner();

        Vec3 eye = null;
        Vec3 view = null;
        if (viewer != null && ownerUuid != null && ownerUuid.equals(viewer.getUUID())) {
            eye = viewer.getEyePosition(partialTick);
            if (minecraft.options.getCameraType().isFirstPerson()) {
                Vector3f look = minecraft.gameRenderer.getMainCamera().getLookVector();
                view = new Vec3(look.x(), look.y(), look.z());
            } else {
                // 第三人称前视角的相机朝向是反的，只能用玩家本体的视线。
                view = viewer.getViewVector(partialTick);
            }
        } else if (owner != null) {
            // 别的玩家在用竹枪：竹杆必须从「他自己的眼睛 → 他自己的视线」这条射线长出来，
            // 也就是画面上这个玩家此时此刻准星所指的方向——两边看到的是同一根杆。
            eye = owner.getEyePosition(partialTick);
            view = owner.getViewVector(partialTick);
        }
        if (eye == null || view == null || view.lengthSqr() < 1.0e-6) {
            // 持有者不在这边的客户端里：退回服务端每 tick 同步的权威眼睛 / 视线（已按 partialTick 插值）。
            eye = entity.getInterpolatedEye(partialTick);
            view = entity.getInterpolatedDir(partialTick);
        }
        if (eye == null || view == null || view.lengthSqr() < 1.0e-6) {
            float yaw = Mth.lerp(partialTick, entity.yRotO, entity.getYRot());
            float pitch = Mth.lerp(partialTick, entity.xRotO, entity.getXRot());
            eye = entity.getPosition(partialTick);
            view = Vec3.directionFromRotation(pitch, yaw);
        }
        view = view.normalize();

        float yaw = owner != null ? Mth.rotLerp(partialTick, owner.yRotO, owner.getYRot())
                : (float) Math.toDegrees(Math.atan2(-view.x, view.z));
        HumanoidArm mainArm = owner != null ? owner.getMainArm() : HumanoidArm.RIGHT;
        HumanoidArm attackArm = entity.getAttackHand() == InteractionHand.MAIN_HAND
                ? mainArm : mainArm.getOpposite();
        BambooSpearVisualPose pose = BambooSpearVisualPose.at(BambooSpearEntity.poleBase(eye, view), view,
                length, yaw, attackArm == HumanoidArm.RIGHT);
        Vec3 origin = pose.tail();

        Vec3 entityPos = entity.getPosition(partialTick);
        poseStack.pushPose();
        poseStack.translate(origin.x - entityPos.x, origin.y - entityPos.y, origin.z - entityPos.z);
        BambooPoleGeometry.orient(poseStack, pose.direction());
        BambooPoleGeometry.render(poseStack, bufferSource, packedLight, 0.0F,
                BambooSpearVisualPose.POLE_LENGTH, RADIUS, TIP_LENGTH);
        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    @SuppressWarnings("deprecation")
    @Override
    public ResourceLocation getTextureLocation(BambooSpearEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
