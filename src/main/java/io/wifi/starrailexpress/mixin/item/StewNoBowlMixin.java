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

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.Optional;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import io.wifi.starrailexpress.SRE;

@Mixin(Player.class)
public class StewNoBowlMixin {
    /**
     * 当食物即将转换出碗时，返回空 Optional，阻止后续的转换逻辑。
     * 其他转换物品（比如瓶子）不受影响。
     */
    @Redirect(method = "eat", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/food/FoodProperties;usingConvertsTo()Ljava/util/Optional;"))
    private Optional<ItemStack> cancelBowlConversion(FoodProperties foodProperties) {
        Optional<ItemStack> original = foodProperties.usingConvertsTo();
        if (SRE.isLobby)
            return original;
        if (original.isPresent() && original.get().is(Items.BOWL)) {
            return Optional.empty();
        }
        return original;
    }
}
