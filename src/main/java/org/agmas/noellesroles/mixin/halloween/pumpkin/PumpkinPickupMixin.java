package org.agmas.noellesroles.mixin.halloween.pumpkin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.game.halloween.HalloweenItems;
import org.agmas.noellesroles.game.halloween.HalloweenRuntime;
import org.agmas.noellesroles.game.halloween.pumpkin.PumpkinSpiritHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** 在实际成功插入处结算，触碰但背包满不算获得；复制到背包的栈先携带全局已结算标记。 */
@Mixin(ItemEntity.class)
public abstract class PumpkinPickupMixin {
    @WrapOperation(method = "playerTouch", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Inventory;add(Lnet/minecraft/world/item/ItemStack;)Z"))
    private boolean pumpkin$pickup(Inventory inventory, ItemStack stack, Operation<Boolean> original) {
        if (!(inventory.player instanceof ServerPlayer p) || !HalloweenRuntime.isEnabled(p.level())
                || !stack.is(HalloweenItems.PUMPKIN) || PumpkinSpiritHandler.settled(stack)) return original.call(inventory, stack);
        int before = stack.getCount();
        PumpkinSpiritHandler.markSettled(stack);
        boolean result = original.call(inventory, stack);
        int acquired = org.agmas.noellesroles.game.halloween.pumpkin.PumpkinAcquisition.creditable(false, before, stack.getCount());
        // 未拾取的余数仍是全新地上奖励；只有实际插入的分栈带 settled。
        if (!stack.isEmpty()) PumpkinSpiritHandler.unmarkSettled(stack);
        PumpkinSpiritHandler.onFreshPickup(p, acquired);
        return result;
    }
}
