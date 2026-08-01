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

package org.agmas.noellesroles.mixin.client.roles.vulture;

import com.mojang.blaze3d.vertex.PoseStack;

import io.wifi.starrailexpress.cca.PlayerBodyEntityComponent;
import io.wifi.starrailexpress.client.render.entity.PlayerBodyEntityRenderer;
import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import net.minecraft.client.renderer.MultiBufferSource;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerBodyEntityRenderer.class)
public abstract class VultureEatenBodyMixin {

    @Inject(method = "renderBody", at = @At("TAIL"), cancellable = true)
    public void vultureSkeletonOnly(PlayerBodyEntity livingEntity, float f, float g, PoseStack matrixStack, MultiBufferSource vertexConsumerProvider, int light, float alpha, CallbackInfo ci) {
        PlayerBodyEntityComponent bodyDeathReasonComponent = PlayerBodyEntityComponent.KEY.get(livingEntity);
        if (bodyDeathReasonComponent.vultured) {
            ci.cancel();
        }
    }
}
