package org.agmas.noellesroles.mixin.roles.scout;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.role.bouns.roles.ScoutCaptainRole;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 童子军队长：吃东西恢复体力条。
 *
 * <p>原版没有「吃完食物」的事件，这里和仓库里的 {@code PlayerEntityMixin.tmm$eat} /
 * {@code SealedArtifactEatMixin} 一样挂在 {@code Player#eat(Level, ItemStack, FoodProperties)} 上。
 *
 * <p>注意不加 {@code isClientSide} 判断：{@code sprintingTicks} 是两端各自模拟的
 * （原版那套体力没有常规同步包），所以恢复必须两端都做，否则 HUD 体力条不会动。
 */
@Mixin(Player.class)
public class ScoutCaptainEatMixin {

    @Inject(method = "eat(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/food/FoodProperties;)Lnet/minecraft/world/item/ItemStack;", at = @At("HEAD"))
    private void noellesroles$captainEatRestoresStamina(Level level, ItemStack stack, FoodProperties food,
            CallbackInfoReturnable<ItemStack> cir) {
        ScoutCaptainRole.onEat((Player) (Object) this);
    }
}
