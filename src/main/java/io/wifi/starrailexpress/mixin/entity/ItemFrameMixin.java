package io.wifi.starrailexpress.mixin.entity;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemFrame.class)
public class ItemFrameMixin {


    @Inject(method = "interact", at = @At("HEAD"), cancellable = true)
    public void interact(Player player, InteractionHand interactionHand, CallbackInfoReturnable<InteractionResult> cir) {
        if (SRE.isLobby) {
            return;
        }
        SREGameWorldComponent sreGameWorldComponent = SREGameWorldComponent.KEY.get(player.level());
        if (sreGameWorldComponent !=null&& sreGameWorldComponent.isRunning()) {
            if (GameUtils.isPlayerAliveAndSurvival(player)) {
                cir.setReturnValue(InteractionResult.FAIL);
                cir.cancel();
            }
        }
    }
}
