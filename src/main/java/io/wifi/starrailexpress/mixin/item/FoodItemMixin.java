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
import io.wifi.starrailexpress.customitem.CustomItem;
import io.wifi.starrailexpress.customitem.CustomItemLoader;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.component.FoodDrinkGlowComponent;
import org.agmas.noellesroles.client.scene.MapStatusBarRuntime;
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
            // CocktailItem 在自己的 finishUsingItem 里已经调用过 playerDrink，别重复触发。
            if (stack.getItem() instanceof CocktailItem) {
                return;
            }
            // 自定义物品（含自定义饮品）的喝 / 吃由 CustomItemRuntime.onFoodConsumed 统一处理，
            // 里面已经按 CustomItemData#isDrink 分流过了，这里再判一次会重复触发。
            if (stack.getItem() instanceof CustomItem && CustomItemLoader.getData(stack) != null) {
                return;
            }
            // 按统一的饮料判定走：只看「是不是饮料」，不再认物品类名。
            if (FoodDrinkGlowComponent.isDrink(stack)) {
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
