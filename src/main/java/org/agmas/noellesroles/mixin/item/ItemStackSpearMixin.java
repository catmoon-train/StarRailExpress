package org.agmas.noellesroles.mixin.item;

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

/** Fabric 1.21.1 矛右键蓄力结算，对应 Backported-Spears 的 ItemStack usageTick mixin。 */
@Mixin(ItemStack.class)
public abstract class ItemStackSpearMixin {

    @Inject(method = "onUseTick", at = @At("HEAD"), cancellable = true)
    private void spear$usageTick(Level level, LivingEntity user, int remainingUseTicks, CallbackInfo ci) {
        if (level.isClientSide()) {
            return;
        }
        ItemStack stack = (ItemStack) (Object) this;
        if (!SpearConfig.isSpear(stack)) {
            return;
        }
        EquipmentSlot slot = user.getUsedItemHand() == InteractionHand.MAIN_HAND
                ? EquipmentSlot.MAINHAND
                : EquipmentSlot.OFFHAND;
        SpearCombat.usageTick(stack, remainingUseTicks, user, slot);
        ci.cancel();
    }
}
