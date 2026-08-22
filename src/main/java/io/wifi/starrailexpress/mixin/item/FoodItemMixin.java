/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */


package io.wifi.starrailexpress.mixin.item;

import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import io.wifi.starrailexpress.content.item.CocktailItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.HoneyBottleItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.component.FoodDrinkGlowComponent;
import org.agmas.noellesroles.scene.MapStatusBarRuntime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Item.class)
public class FoodItemMixin {

    @Inject(method = "finishUsingItem", at = @At("HEAD"))
    public void bartenderVision(ItemStack stack, Level world, LivingEntity user,
            CallbackInfoReturnable<ItemStack> cir) {
        if (user instanceof ServerPlayer p) {
            MapStatusBarRuntime.onFinishUsingItem(stack, world, user);
            if (stack.getItem() instanceof CocktailItem) {
                return;
            }
            if (stack.getItem() instanceof PotionItem || stack.getItem() instanceof HoneyBottleItem) {
                SREPlayerMoodComponent.KEY.get(p).drinkCocktail();
                FoodDrinkGlowComponent.playerDrink(p, stack);
                return;
            }
            if (stack.get(DataComponents.FOOD) != null) {
                FoodDrinkGlowComponent.playerEat(p, stack);
                return;
            }
        }

    }
}
