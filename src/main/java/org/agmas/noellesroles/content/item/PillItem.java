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

package org.agmas.noellesroles.content.item;

import io.wifi.starrailexpress.cca.SREPlayerPoisonComponent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.component.InfectedPlayerComponent;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.init.HSRConstants;
import org.agmas.noellesroles.init.ModItems;
import org.jetbrains.annotations.NotNull;

public class PillItem extends Item {
    public PillItem(Properties settings) {
        super(settings);
    }

    @Override
    public @NotNull ItemStack finishUsingItem(ItemStack stack, Level world, LivingEntity user) {
        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        var tag = customData.copyTag();
        boolean poisonous = false;
        if (tag.contains(ModItems.PILL_POISONOUS_KEY)) {
            poisonous = tag.getBoolean(ModItems.PILL_POISONOUS_KEY);
        }
        ItemStack result = super.finishUsingItem(stack, world, user);
        if (user instanceof Player player && !world.isClientSide) {
            if (poisonous) {
                SREPlayerPoisonComponent.KEY.get(player).setPoisonTicks(HSRConstants.toxinPoisonTime, player.getUUID());
            } else {
                SREPlayerPoisonComponent.KEY.get(player).init();
                // 治愈感染
                InfectedPlayerComponent infectedComponent = ModComponents.INFECTED.get(player);
                infectedComponent.cure();
            }
        }
        return result;
    }
}