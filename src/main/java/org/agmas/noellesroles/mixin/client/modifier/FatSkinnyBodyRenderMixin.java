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
import io.wifi.starrailexpress.client.render.entity.PlayerBodyEntityRenderer;
import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import org.agmas.noellesroles.game.modifier.fatskinny.FatSkinnyVisual;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 尸体渲染走独立 scale，不经过 LivingEntityRenderer.scale。
 */
@Mixin(PlayerBodyEntityRenderer.class)
public class FatSkinnyBodyRenderMixin {
    @Inject(method = "scale", at = @At("RETURN"))
    private void noellesroles$fatSkinnyBodyScale(PlayerBodyEntity entity, PoseStack poseStack, float amount,
            CallbackInfo ci) {
        float scale = FatSkinnyVisual.horizontalScale(entity);
        if (scale != 1.0F) {
            poseStack.scale(scale, 1.0F, scale);
        }
    }
}
