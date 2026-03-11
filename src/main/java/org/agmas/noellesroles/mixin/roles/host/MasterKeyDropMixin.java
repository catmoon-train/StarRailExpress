package org.agmas.noellesroles.mixin.roles.host;

import io.wifi.starrailexpress.game.GameFunctions;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.agmas.noellesroles.init.ModItems;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameFunctions.class)
public abstract class MasterKeyDropMixin {

    @Inject(method = "shouldDropOnDeath", at = @At("HEAD"), cancellable = true)
    private static void dropMasterKeyOnDeath(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (stack.is(ModItems.MASTER_KEY) || 
            stack.is(Items.WRITABLE_BOOK) || 
            stack.is(Items.WRITTEN_BOOK)) {
            cir.setReturnValue(true);
            cir.cancel();
        }
    }

}
