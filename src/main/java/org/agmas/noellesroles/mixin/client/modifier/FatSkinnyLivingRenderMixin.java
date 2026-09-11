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

package org.agmas.noellesroles.mixin.client.modifier;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.agmas.noellesroles.game.modifier.fatskinny.FatSkinnyVisual;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 玩家第三人称/背包纸人在 {@link PlayerRenderer#scale} 里做左右拉伸。
 *
 * <p>不能注入 {@code LivingEntityRenderer.scale}：1.21.1 的 {@link PlayerRenderer}
 * 重写了该方法（固定 0.9375）且不调用 {@code super}，父类注入永远不会跑到。
 * 名牌在 scale 的 push/pop 之外，比例保持正常。
 */
@Mixin(PlayerRenderer.class)
public class FatSkinnyLivingRenderMixin {
    @Inject(
            method = "scale(Lnet/minecraft/client/player/AbstractClientPlayer;Lcom/mojang/blaze3d/vertex/PoseStack;F)V",
            at = @At("RETURN"))
    private void noellesroles$fatSkinnyScale(AbstractClientPlayer entity, PoseStack poseStack, float partialTick,
            CallbackInfo ci) {
        float scale = FatSkinnyVisual.horizontalScale(entity);
        if (scale != 1.0F) {
            poseStack.scale(scale, 1.0F, scale);
        }
    }
}
