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

package org.agmas.noellesroles.game.roles.innocence.angler;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.core.component.DataComponents;

public final class AnglerItemTags {
    public static final String INVERTED = "angler_inverted";
    public static final String CARP_TICK = "angler_carp_tick";
    public static final String ERROR_USES = "angler_error_uses";

    private AnglerItemTags() {
    }

    public static CompoundTag data(ItemStack stack) {
        CustomData custom = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        return custom.copyTag();
    }

    public static void setData(ItemStack stack, CompoundTag tag) {
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static boolean isInverted(ItemStack stack) {
        return data(stack).getBoolean(INVERTED);
    }

    public static void markInverted(ItemStack stack) {
        CompoundTag tag = data(stack);
        tag.putBoolean(INVERTED, true);
        setData(stack, tag);
    }

    public static void stampCarp(ItemStack stack, long gameTick) {
        CompoundTag tag = data(stack);
        tag.putLong(CARP_TICK, gameTick);
        setData(stack, tag);
    }

    public static long carpTick(ItemStack stack) {
        return data(stack).getLong(CARP_TICK);
    }

    public static int errorUses(ItemStack stack) {
        CompoundTag tag = data(stack);
        return tag.contains(ERROR_USES) ? tag.getInt(ERROR_USES) : -1;
    }

    public static void setErrorUses(ItemStack stack, int uses) {
        CompoundTag tag = data(stack);
        tag.putInt(ERROR_USES, uses);
        setData(stack, tag);
    }
}
