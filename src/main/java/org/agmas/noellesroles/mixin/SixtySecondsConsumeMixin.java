package org.agmas.noellesroles.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 末日60秒模式：任何物品「吃/喝」完成时（{@code Item.finishUsingItem}，食物与药水都走这里），
 * 按 foodData/水等级恢复饱食度/口渴值。仅本模式生效，其它模式零影响。
 * 逻辑在 {@code net.exmo.sre.sixtyseconds.logic.SixtySecondsConsumables}。
 */
@Mixin(Item.class)
public class SixtySecondsConsumeMixin {
    @Inject(method = "finishUsingItem", at = @At("HEAD"))
    public void sixtySeconds$restore(ItemStack stack, Level world, LivingEntity user,
            CallbackInfoReturnable<ItemStack> cir) {
        if (user instanceof ServerPlayer player
                && net.exmo.sre.sixtyseconds.SixtySecondsMod.isActive(world)) {
            net.exmo.sre.sixtyseconds.logic.SixtySecondsConsumables.onConsume(player, stack);
        }
    }
}
