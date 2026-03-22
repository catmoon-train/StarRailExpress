
package io.wifi.starrailexpress.mixin.item;

import io.wifi.starrailexpress.cca.SREArmorPlayerComponent;
import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import io.wifi.starrailexpress.item.CocktailItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.HoneyBottleItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Item.class)
public class FoodItemMixin {

    @Inject(method = "finishUsingItem", at = @At("HEAD"))
    public void bartenderVision(ItemStack stack, Level world, LivingEntity user,
            CallbackInfoReturnable<ItemStack> cir) {
        if (user instanceof ServerPlayer) {
            if (stack.getItem() instanceof CocktailItem) {
                return;
            }
            if (stack.getItem() instanceof PotionItem || stack.getItem() instanceof HoneyBottleItem) {
                SREPlayerMoodComponent.KEY.get(user).drinkCocktail();
                return;
            }
            if (stack.get(DataComponents.FOOD) != null) {
                ((SREArmorPlayerComponent) SREArmorPlayerComponent.KEY.get(user)).startGlow(1);
                return;
            }
        }

    }
}
