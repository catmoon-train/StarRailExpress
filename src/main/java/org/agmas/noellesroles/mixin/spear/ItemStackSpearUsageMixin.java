package org.agmas.noellesroles.mixin.spear;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.spear.SpearCombat;
import org.agmas.noellesroles.spear.SpearConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 举矛蓄力时每 tick 触发一次冲锋结算（取代原版的蓄力进度逻辑）。
 */
@Mixin(ItemStack.class)
public class ItemStackSpearUsageMixin {

    @Inject(method = "usageTick", at = @At("HEAD"), cancellable = true)
    private void spear$kineticUsageTick(Level level, LivingEntity user, int remainingUseTicks, CallbackInfo ci) {
        ItemStack self = (ItemStack) (Object) this;
        if (level.isClientSide() || !SpearConfig.isSpear(self)) {
            return;
        }
        EquipmentSlot slot = user.getUsedItemHand() == InteractionHand.MAIN_HAND
                ? EquipmentSlot.MAINHAND
                : EquipmentSlot.OFFHAND;
        SpearCombat.usageTick(self, remainingUseTicks, user, slot);
        ci.cancel();
    }
}
