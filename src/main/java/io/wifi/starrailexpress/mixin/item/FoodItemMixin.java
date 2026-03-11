
package io.wifi.starrailexpress.mixin.item;

import io.wifi.starrailexpress.cca.BartenderPlayerComponent;
import io.wifi.starrailexpress.item.CocktailItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
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
        if (stack.getItem() instanceof CocktailItem) {
            return;
        }
        if (stack.get(DataComponents.FOOD) != null) {
            ((BartenderPlayerComponent) BartenderPlayerComponent.KEY.get(user)).startGlow(1);
        }
    }
}
