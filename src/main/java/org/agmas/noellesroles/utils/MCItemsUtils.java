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

package org.agmas.noellesroles.utils;

import io.wifi.starrailexpress.util.SREItemUtils;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

public class MCItemsUtils extends SREItemUtils {
    public static List<Item> getItemsByTag(ServerLevel level, TagKey<Item> tag) {
        var opt2 = level.getServer().registryAccess()
                .registry(Registries.ITEM);
        if (opt2.isEmpty())
            return List.of();
        Optional<HolderSet.Named<Item>> holderSet = opt2.get()
                .getTag(tag);
        if (holderSet.isEmpty())
            return List.of();

        return holderSet.get().stream()
                .map(Holder::value)
                .toList();
    }

    public static Optional<Item> getItemById(String name) {
        if (name == null)
            return Optional.empty();
        return getItemById(ResourceLocation.tryParse(name));
    }

    public static Optional<Block> getBlockById(String name) {
        if (name == null)
            return Optional.empty();
        return getBlockById(ResourceLocation.tryParse(name));
    }

    public static Optional<Item> getItemById(ResourceLocation name) {
        if (name == null)
            return Optional.empty();
        return BuiltInRegistries.ITEM.getOptional(name);
    }

    public static Optional<Block> getBlockById(ResourceLocation name) {
        if (name == null)
            return Optional.empty();
        return BuiltInRegistries.BLOCK.getOptional(name);
    }

    public static @Nullable ItemStack getFirstMatchedItem(Player player, Item item) {
        return getFirstMatchedItem(player, (it) -> it.is(item));
    }

    public static @Nullable ItemStack getFirstMatchedItem(Player player, TagKey<Item> item) {
        return getFirstMatchedItem(player, (it) -> it.is(item));
    }

    public static @Nullable ItemStack getFirstMatchedItem(Player player, Predicate<ItemStack> predicate) {
        for (ItemStack item : player.containerMenu.getItems()) {
            if (predicate.test(item)) {
                return item;
            }
        }
        return null;
    }
}
