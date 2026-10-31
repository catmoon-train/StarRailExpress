package org.agmas.noellesroles.mixin.client;

import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.client.KnifeGlintContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 原版和兼容3D渲染器均通过 ItemStack.hasFoil 取得相同的刀光泽判定。 */
@Mixin(ItemStack.class)
public abstract class KnifeGlintStackMixin {
    @Inject(method = "hasFoil", at = @At("HEAD"), cancellable = true)
    private void sre$knifeFoil(CallbackInfoReturnable<Boolean> cir) {
        Boolean foil = KnifeGlintContext.foil((ItemStack) (Object) this);
        if (foil != null) cir.setReturnValue(foil);
    }
}
