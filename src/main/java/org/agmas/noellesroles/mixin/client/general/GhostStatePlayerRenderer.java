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
