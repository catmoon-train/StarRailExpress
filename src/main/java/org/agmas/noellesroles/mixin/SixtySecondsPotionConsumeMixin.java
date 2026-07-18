package org.agmas.noellesroles.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 药水（{@link PotionItem}）重写了 {@code finishUsingItem}，导致基类
 * {@code Item} 的 Mixin 注入对它不生效。这里单独补一针。
 */
@Mixin(PotionItem.class)
public class SixtySecondsPotionConsumeMixin {
    @Inject(method = "finishUsingItem", at = @At("HEAD"))
    public void sixtySeconds$potionRestore(ItemStack stack, Level world, LivingEntity user,
            CallbackInfoReturnable<ItemStack> cir) {
        if (user instanceof ServerPlayer player
                && net.exmo.sre.sixtyseconds.SixtySecondsMod.isActive(world)) {
            net.exmo.sre.sixtyseconds.logic.SixtySecondsConsumables.onConsume(player, stack);
        }
    }
}
