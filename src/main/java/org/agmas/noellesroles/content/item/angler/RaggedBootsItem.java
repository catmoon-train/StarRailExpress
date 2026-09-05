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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.game.roles.innocence.angler.AnglerItemTags;
import org.agmas.noellesroles.init.ModEffects;
import org.jetbrains.annotations.NotNull;

public class RaggedBootsItem extends ArmorItem {
    public RaggedBootsItem(Properties properties) {
        super(ArmorMaterials.LEATHER, Type.BOOTS, properties);
    }

    @Override
    public void inventoryTick(@NotNull ItemStack stack, @NotNull Level level, @NotNull Entity entity, int slot,
            boolean selected) {
        if (level.isClientSide || !(entity instanceof Player player)) {
            return;
        }
        if (!GameUtils.isGameRunning(player) || player.getItemBySlot(EquipmentSlot.FEET) != stack) {
            return;
        }
        if (AnglerItemTags.isInverted(stack)) {
            player.addEffect(ModEffects.of(MobEffects.MOVEMENT_SLOWDOWN, 40, 0, false, false, true));
        } else {
            player.addEffect(ModEffects.of(MobEffects.MOVEMENT_SPEED, 40, 0, false, false, true));
            player.addEffect(ModEffects.of(MobEffects.JUMP, 40, 0, false, false, true));
        }
    }
}
