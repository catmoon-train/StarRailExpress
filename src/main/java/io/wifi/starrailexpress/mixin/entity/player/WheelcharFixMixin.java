package io.wifi.starrailexpress.mixin.entity.player;

import io.wifi.starrailexpress.index.TMMBlocks;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public class WheelcharFixMixin {
@Inject(method = "wantsToStopRiding", at = @At("HEAD"), cancellable = true)
    private void onWantsToStopRiding(CallbackInfoReturnable<Boolean> cir) {
        Player self = (Player) (Object) this;
        // 仅在服务端且玩家正在骑乘时阻止主动下座位
        if (!self.level().isClientSide && self.isPassenger() && self.getCooldowns().isOnCooldown(TMMBlocks.ACACIA_BRANCH.asItem())) {
            cir.setReturnValue(false);
        }
    }
}
