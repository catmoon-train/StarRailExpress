package org.agmas.noellesroles.mixin.halloween;

import io.wifi.starrailexpress.util.SREItemUtils;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.game.halloween.HalloweenSoulLinks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 原地合堆的发物工具同样拒绝向借用背包插入奖励，不能修改发起者的栈。 */
@Mixin(SREItemUtils.class)
public abstract class HalloweenLinkGiveMixin {
    @Inject(method = "insertStackInFreeSlot", at = @At("HEAD"), cancellable = true)
    private static void halloween$linkGive(Player player, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (HalloweenSoulLinks.isItemBorrower(player)) cir.setReturnValue(false);
    }
}
