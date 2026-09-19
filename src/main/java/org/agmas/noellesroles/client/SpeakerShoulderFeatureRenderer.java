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
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.client.sound.SpeakerClientSounds;
import org.agmas.noellesroles.content.item.SpeakerItem;
import org.agmas.noellesroles.init.ModItems;

/**
 * 物品栏里的音响处于开启状态时，把 3D 音响模型扛在右肩上。
 */
public class SpeakerShoulderFeatureRenderer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    // ===== 微调参数 =====
    // 模型空间：+Y 向下、-X 为右肩、单位为方块。
    // 参考几何：右臂 x ∈ [-0.50, -0.25]，头部 x ∈ [-0.25, 0.25]，肩平面 y = 0。
    // 音响本体（含 fixed 的 0.85 与下面的 SCALE）底部在锚点下方约 0.253 处，
    // 所以 OFFSET_Y 取 -0.25 时底部正好坐在肩平面上，而不是插进手臂。
    private static final float OFFSET_X = -0.40F; // 右肩上方偏外，内缘刚好不蹭到头
    private static final float OFFSET_Y = -0.25F; // 负值 = 抬高到肩平面之上
    private static final float OFFSET_Z = 0.00F; // 前后居中
    private static final float LEAN_DEG = 0.0F; // 外倾角，0 = 端正立在肩上；调大需同步下调 OFFSET_Y
    private static final float SCALE = 0.48F; // 整体大小（还会再乘模型 fixed 的 0.85）

    private final ItemStack speakerStack;

    public SpeakerShoulderFeatureRenderer(
            RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> renderer) {
        super(renderer);
        this.speakerStack = new ItemStack(ModItems.SPEAKER);
    }

    @Override
    public void render(PoseStack matrices, MultiBufferSource vertexConsumers, int light,
            AbstractClientPlayer entity, float limbAngle, float limbDistance,
            float tickDelta, float animationProgress, float headYaw, float headPitch) {
        if (entity.isInvisible() || entity.isSpectator()) {
            return;
        }
        if (!SpeakerClientSounds.isPlaying(entity.getUUID()) && !SpeakerItem.hasPlayingSpeaker(entity)) {
            return;
        }

        matrices.pushPose();
        this.getParentModel().body.translateAndRotate(matrices);
        // 抬到肩头（y=0 之上），让底部坐在肩膀顶面而不是埋进手臂
        matrices.translate(OFFSET_X, OFFSET_Y, OFFSET_Z);
        // 音响模型的 fixed 变换自带 Y 轴 180°，这里再翻一次 X 轴 180° 抵消：
        // 底部朝下、喇叭/LCD 那一面朝向玩家正前方。
        matrices.mulPose(Axis.XP.rotationDegrees(180.0F));
        if (LEAN_DEG != 0.0F) {
            matrices.mulPose(Axis.ZP.rotationDegrees(LEAN_DEG));
        }
        matrices.scale(SCALE, SCALE, SCALE);

        Minecraft.getInstance().getItemRenderer().renderStatic(
                this.speakerStack, ItemDisplayContext.FIXED, light,
                OverlayTexture.NO_OVERLAY, matrices, vertexConsumers, entity.level(), entity.getId());
        matrices.popPose();
    }
}
