package org.agmas.noellesroles.mixin.roles.jester;

import io.wifi.starrailexpress.item.RevolverItem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.HitResult;
import org.agmas.noellesroles.init.ModItems;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RevolverItem.class)
public abstract class FakeRevolverMixin {

    @Inject(method = "getGunTarget", at = @At("HEAD"), cancellable = true)
    private static void jesterFakeGun(Player user, CallbackInfoReturnable<HitResult> cir) {
        if (user.getMainHandItem().is(ModItems.FAKE_REVOLVER)) {
            cir.setReturnValue(null);
        }
    }

}
