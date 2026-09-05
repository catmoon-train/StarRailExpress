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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.game.roles.innocence.angler.AnglerItemTags;
import org.agmas.noellesroles.game.roles.innocence.angler.AnglerRules;
import org.agmas.noellesroles.game.roles.innocence.angler.AnglerWorldMemory;
import org.agmas.noellesroles.init.ModItems;
import org.jetbrains.annotations.NotNull;

public class LivingCarpItem extends Item {
    public LivingCarpItem(Properties properties) {
        super(properties);
    }

    @Override
    public void inventoryTick(@NotNull ItemStack stack, @NotNull Level level, @NotNull Entity entity, int slot,
            boolean selected) {
        if (level.isClientSide || !(entity instanceof Player player)) {
            return;
        }
        long now = GameUtils.getTicksFromGameStart(level);
        if (!tryKill(stack, now)) {
            return;
        }
        boolean inverted = AnglerItemTags.isInverted(stack);
        player.getInventory().setItem(slot, new ItemStack(ModItems.ANGLER_DEAD_CARP));
        if (player instanceof ServerPlayer serverPlayer && level instanceof ServerLevel serverLevel) {
            AnglerWorldMemory.grantCarpCoins(serverLevel, serverPlayer.position(), inverted);
        }
    }

    public static boolean tryKill(ItemStack stack, long now) {
        long stamp = AnglerItemTags.carpTick(stack);
        if (stamp <= 0) {
            AnglerItemTags.stampCarp(stack, now);
            return false;
        }
        return now - stamp >= AnglerRules.CARP_LIVE_TICKS;
    }
}
