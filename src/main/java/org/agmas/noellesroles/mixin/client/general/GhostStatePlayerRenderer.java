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

package org.agmas.noellesroles.mixin.client.general;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.component.GhostStateComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public class GhostStatePlayerRenderer {
    @Inject(method = "isInvisible", at = @At("RETURN"), cancellable = true)
    public void render$1(CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue()) {
            Entity entity = (Entity) (Object) this;
            if (entity instanceof Player player) {
                GhostStateComponent ghostStateComponent = GhostStateComponent.KEY.get(player);
                if (ghostStateComponent.isGhost) {
                    cir.setReturnValue(true);
                }
            }
        }

    }
    @Inject(method = "isInvisibleTo", at = @At("RETURN"), cancellable = true)
    public void render(Player player, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) {
            GhostStateComponent ghostStateComponent = GhostStateComponent.KEY.get(player);
            if (ghostStateComponent.isGhost) {
                cir.setReturnValue(false);
            }
        }

    }
//    @Inject(method = "render(Lnet/minecraft/client/player/AbstractClientPlayer;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("TAIL"))
//    public void render$1(AbstractClientPlayer abstractClientPlayer, float f, float g, PoseStack poseStack, MultiBufferSource multiBufferSource, int i, CallbackInfo ci) {
//        GhostStateComponent ghostStateComponent = GhostStateComponent.KEY.get(abstractClientPlayer);
//        if (ghostStateComponent.isGhost){
//            RenderSystem.disableBlend();
//            RenderSystem.setShaderColor(1, 1, 1, 1);
//
//
//        }
//
//    }

}
