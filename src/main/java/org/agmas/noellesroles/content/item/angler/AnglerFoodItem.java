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

package org.agmas.noellesroles.content.item.angler;

import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.game.roles.innocence.angler.AnglerItemTags;
import org.agmas.noellesroles.game.roles.innocence.angler.AnglerRules;
import org.agmas.noellesroles.init.ModEffects;
import org.jetbrains.annotations.NotNull;

public class AnglerFoodItem extends Item {
    public enum Kind {
        FLOUNDER, INVERTED_FISH
    }

    public static final FoodProperties FOOD = new FoodProperties.Builder()
            .nutrition(2).saturationModifier(0.2F).alwaysEdible().fast().build();

    private final Kind kind;

    public AnglerFoodItem(Properties properties, Kind kind) {
        super(properties.food(FOOD));
        this.kind = kind;
    }

    @Override
    public @NotNull ItemStack finishUsingItem(@NotNull ItemStack stack, @NotNull Level level,
            @NotNull LivingEntity entity) {
        ItemStack result = super.finishUsingItem(stack, level, entity);
        if (level.isClientSide || !(entity instanceof Player player) || !GameUtils.isGameRunning(player)) {
            return result;
        }
        boolean inverted = AnglerItemTags.isInverted(stack);
        if (kind == Kind.FLOUNDER) {
            player.addEffect(ModEffects.of(MobEffects.CONFUSION, AnglerRules.FLOUNDER_TICKS, 0, false, true, true));
            if (inverted) {
                player.addEffect(ModEffects.of(MobEffects.DARKNESS, AnglerRules.FLOUNDER_TICKS, 0, false, true, true));
            } else {
                player.addEffect(ModEffects.of(MobEffects.NIGHT_VISION, AnglerRules.FLOUNDER_TICKS, 0, false, false, true));
            }
        } else if (inverted) {
            player.addEffect(ModEffects.of(ModEffects.MOVE_UPSIDE_DOWN, AnglerRules.UPSIDE_DOWN_TICKS, 0, false, false, true));
            player.addEffect(ModEffects.of(ModEffects.MOUSE_UPSIDE_DOWN, AnglerRules.UPSIDE_DOWN_TICKS, 0, false, false, true));
        } else {
            player.addEffect(ModEffects.of(ModEffects.UPSIDE_DOWN, AnglerRules.UPSIDE_DOWN_TICKS, 0, false, false, true));
        }
        return result;
    }
}
