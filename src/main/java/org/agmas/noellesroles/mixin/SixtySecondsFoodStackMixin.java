package org.agmas.noellesroles.mixin;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 末日60秒模式进行中：所有<b>食物</b>（含 FOOD 组件的物品，含水/药水/蜂蜜等）不可堆叠（maxStackSize=1），
 * 让家庭携带上限与物资管理有意义。仅在本模式对局中生效（{@code SixtySecondsMod.RUNNING}）。
 */
@Mixin(ItemStack.class)
public class SixtySecondsFoodStackMixin {
    @Inject(method = "getMaxStackSize", at = @At("RETURN"), cancellable = true)
    private void sixtySeconds$noStack(CallbackInfoReturnable<Integer> cir) {
        if (net.exmo.sre.sixtyseconds.SixtySecondsMod.RUNNING && cir.getReturnValueI() > 1) {
            ItemStack self = (ItemStack) (Object) this;
            if (self.has(DataComponents.FOOD)) {
                cir.setReturnValue(1);
            }
        }
    }
}
