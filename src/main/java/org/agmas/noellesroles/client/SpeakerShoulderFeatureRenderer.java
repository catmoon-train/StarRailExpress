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
 * 物品栏里的音响处于开启状态时，把音响画在右肩上。
 */
public class SpeakerShoulderFeatureRenderer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
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
        // 右肩：身体坐标系 X 为正（玩家自身右侧）
        matrices.translate(-0.28F, 0.18F, 0.02F);
        matrices.mulPose(Axis.ZP.rotationDegrees(18.0F));
        matrices.mulPose(Axis.YP.rotationDegrees(200.0F));
        matrices.mulPose(Axis.XP.rotationDegrees(-8.0F));
        matrices.scale(0.72F, 0.72F, 0.72F);

        Minecraft.getInstance().getItemRenderer().renderStatic(
                this.speakerStack, ItemDisplayContext.FIXED, light,
                OverlayTexture.NO_OVERLAY, matrices, vertexConsumers, entity.level(), entity.getId());
        matrices.popPose();
    }
}
