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

package org.agmas.noellesroles.mixin.client.roles.jojo;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.agmas.noellesroles.client.OraGoldArmRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 第一人称手臂：在原版手臂绘制后再铺一层金色渐变包裹。
 */
@Mixin(PlayerRenderer.class)
public class OraFistHandMixin {

    @Inject(method = "renderHand", at = @At("RETURN"))
    private void noellesroles$oraGoldFirstPerson(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
            AbstractClientPlayer player, ModelPart arm, ModelPart sleeve, CallbackInfo ci) {
        if (!OraGoldArmRenderer.shouldWrap(player)) {
            return;
        }
        float age = player.tickCount + Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
        OraGoldArmRenderer.renderSingleArm(arm, sleeve, player, poseStack, buffer, packedLight, age);
    }
}
