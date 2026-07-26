package org.agmas.noellesroles.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;

import org.spongepowered.asm.mixin.injection.At;
import net.minecraft.world.level.block.entity.SignBlockEntity;

@Mixin(SignBlockEntity.class)
public class SignWaxedMixin {
    @Inject(method = "isWaxed", cancellable = true, at = @At("HEAD"))
    public void alwaylsWaxedWhenGameStarted(CallbackInfoReturnable<Boolean> cir) {
        var sb = (SignBlockEntity) (Object) this;
        if (SREGameWorldComponent.getInstance(sb.getLevel()).isRunning()) {
            cir.setReturnValue(true);
        }
    }
}
