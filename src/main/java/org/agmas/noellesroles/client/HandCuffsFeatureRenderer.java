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

package org.agmas.noellesroles.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.content.item.HandCuffsItem;
import org.agmas.noellesroles.init.ModItems;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * 手铐渲染层：动态计算两手腕中点并在该处渲染手铐，
 * X 轴自动对齐两腕连线，不依赖副手渲染和 JSON display 变换。
 */
public class HandCuffsFeatureRenderer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    // ===== 微调参数 =====
    private static final float TILT_X = 0F;    // 绕两腕连线轴旋转（度）
    private static final float TILT_Y = 0F;    // 绕垂直轴旋转（翻转/转正贴图朝向用）
    private static final float TILT_Z = 0F;    // 平面内旋转
    private static final float OFFSET_X = 0F;  // 位置微调（单位：方块）
    private static final float OFFSET_Y = 0F;
    private static final float OFFSET_Z = 0F;
    private static final float SCALE = 0.9F;   // 整体大小
    private static final float WRIST_Y = 11.0F; // 手腕在手臂局部空间的Y偏移（像素），0=肩膀，12=手臂末端，可调

    private final ItemStack handcuffStack = new ItemStack(ModItems.HANDCUFFS);

    public HandCuffsFeatureRenderer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> renderer) {
        super(renderer);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int light,
                       AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (player.isInvisible()) return;
        if (!HandCuffsItem.hasHandCuff(player)) return;

        var model = this.getParentModel();
        Vector3f left = wristPos(model.leftArm);
        Vector3f right = wristPos(model.rightArm);
        Vector3f span = new Vector3f(right).sub(left);
        Vector3f mid = new Vector3f(left).add(right).mul(0.5F);

        Quaternionf quat = null;
        if (span.lengthSquared() > 1.0E-6F) {
            Vector3f xAxis = new Vector3f(span).normalize();
            // 固定垂直地面：手铐平面的"上"直接取模型空间正上方（模型Y向下，取-Y），不跟随手臂倾斜
            Vector3f yAxis = new Vector3f(0.0F, -1.0F, 0.0F);
            Vector3f zAxis = new Vector3f(xAxis).cross(yAxis);
            if (zAxis.lengthSquared() < 1.0E-6F) {
                // 两腕连线接近竖直时退化，改用手臂方向兜底
                Vector3f down = new Vector3f(left).sub(pivot(model.leftArm))
                        .add(new Vector3f(right).sub(pivot(model.rightArm)));
                if (down.lengthSquared() > 1.0E-6F) {
                    yAxis = down.normalize().negate();
                    zAxis = new Vector3f(xAxis).cross(yAxis);
                }
            }
            if (zAxis.lengthSquared() > 1.0E-6F) {
                zAxis.normalize();
                yAxis = new Vector3f(zAxis).cross(xAxis);
                Matrix3f basis = new Matrix3f();
                basis.setColumn(0, xAxis);
                basis.setColumn(1, yAxis);
                basis.setColumn(2, zAxis);
                quat = new Quaternionf().setFromNormalized(basis);
                if (!Float.isFinite(quat.x) || !Float.isFinite(quat.y)
                        || !Float.isFinite(quat.z) || !Float.isFinite(quat.w)) {
                    quat = null;
                }
            }
        }

        poseStack.pushPose();
        if (quat == null) {
            // 兜底：手腕姿态算不出来时渲染在胸口，方便识别"计算失败"
            model.body.translateAndRotate(poseStack);
            poseStack.translate(0.0F, 0.0F, -0.4F);
        } else {
            poseStack.translate(mid.x / 16.0F, mid.y / 16.0F, mid.z / 16.0F);
            poseStack.mulPose(quat);
            if (TILT_X != 0F) poseStack.mulPose(Axis.XP.rotationDegrees(TILT_X));
            if (TILT_Y != 0F) poseStack.mulPose(Axis.YP.rotationDegrees(TILT_Y));
            if (TILT_Z != 0F) poseStack.mulPose(Axis.ZP.rotationDegrees(TILT_Z));
            poseStack.translate(OFFSET_X, OFFSET_Y, OFFSET_Z);
            poseStack.scale(SCALE, SCALE, SCALE);
        }

        ItemRenderer ir = Minecraft.getInstance().getItemRenderer();
        ir.renderStatic(this.handcuffStack, ItemDisplayContext.FIXED, light,
                OverlayTexture.NO_OVERLAY, poseStack, buffer, player.level(), 0);
        poseStack.popPose();
    }

    private static Vector3f wristPos(ModelPart arm) {
        PoseStack ps = new PoseStack();
        ps.pushPose();
        arm.translateAndRotate(ps);
        // 注意：translateAndRotate 后的原点是肩膀轴心，手腕在局部空间 (0, WRIST_Y, 0)
        Vector4f v = ps.last().pose().transform(new Vector4f(0.0F, WRIST_Y, 0.0F, 1.0F));
        ps.popPose();
        return new Vector3f(v.x, v.y, v.z);
    }

    private static Vector3f pivot(ModelPart arm) {
        return new Vector3f(arm.x / 16.0F, arm.y / 16.0F, arm.z / 16.0F);
    }

    @Override
    protected ResourceLocation getTextureLocation(AbstractClientPlayer entity) {
        return null;
    }
}
