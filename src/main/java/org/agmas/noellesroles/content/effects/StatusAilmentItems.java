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

package org.agmas.noellesroles.content.effects;

import io.wifi.starrailexpress.content.item.CocktailItem;
import io.wifi.starrailexpress.content.item.KnifeItem;
import io.wifi.starrailexpress.content.item.api.SREItemProperties.TrainWeapon;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.index.tag.TMMItemTags;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.UseAnim;

/** Shared item predicates for status-ailment potions. */
public final class StatusAilmentItems {
    private StatusAilmentItems() {
    }

    public static boolean isFoodOrDrink(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        UseAnim animation = stack.getUseAnimation();
        return stack.get(DataComponents.FOOD) != null
                || stack.getItem() instanceof CocktailItem
                || stack.is(Items.POTION)
                || stack.is(Items.HONEY_BOTTLE)
                || stack.is(Items.MILK_BUCKET)
                || animation == UseAnim.EAT
                || animation == UseAnim.DRINK;
    }

    public static boolean isWeapon(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        Item item = stack.getItem();
        return item instanceof KnifeItem
                || item instanceof TrainWeapon
                || stack.is(TMMItems.KNIFE)
                || stack.is(TMMItems.BAT)
                || stack.is(TMMItemTags.GUNS)
                || stack.is(TMMItemTags.HELD_LIKE_GUNS_ITEMS)
                || stack.is(TMMItemTags.HELD_LIKE_BAT_ITEMS)
                || stack.is(TMMItemTags.BOWS);
    }
}
