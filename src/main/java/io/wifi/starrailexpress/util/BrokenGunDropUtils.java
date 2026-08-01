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

package io.wifi.starrailexpress.util;

import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.index.tag.TMMItemTags;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class BrokenGunDropUtils {
    public static final String BROKEN_GUN_TAG = "SREBrokenGunDrop";
    public static final int DESPAWN_TICKS = 10 * 20;

    private BrokenGunDropUtils() {
    }

    public static ItemStack createBrokenGunStack() {
        ItemStack stack = TMMItems.REVOLVER.getDefaultInstance();
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putBoolean(BROKEN_GUN_TAG, true);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        stack.set(DataComponents.ITEM_NAME,
                Component.translatable("item.starrailexpress.broken_gun").withStyle(ChatFormatting.DARK_GRAY));
        stack.set(DataComponents.LORE, new ItemLore(List.of(
                Component.translatable("item.starrailexpress.broken_gun.tooltip")
                        .withStyle(ChatFormatting.GRAY))));
        return stack;
    }

    public static boolean isBrokenGun(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        return customData != null && customData.copyTag().getBoolean(BROKEN_GUN_TAG);
    }

    public static void dropBrokenGun(Player victim) {
        dropBrokenGun(victim, true);
    }

    public static ItemEntity dropBrokenGun(Player player, boolean throwRandomly) {
        ItemEntity item = player.drop(createBrokenGunStack(), throwRandomly, false);
        if (item != null) {
            prepareBrokenGunEntity(item);
        }
        return item;
    }

    public static void prepareBrokenGunEntity(ItemEntity item) {
        item.setPickUpDelay(DESPAWN_TICKS);
    }

    public static boolean shouldBreakVictimGunOnKillerKill(SREGameWorldComponent gameWorldComponent, Player victim,
            @Nullable Player killer, ItemStack stack) {
        SREConfig config = SREConfig.instance();
        if (!config.enableBrokenGunDropWhenKillerKillsGunHolder || killer == null || killer.equals(victim)
                || !stack.is(TMMItemTags.GUNS) || isBrokenGun(stack)) {
            return false;
        }
        return gameWorldComponent.canUseKillerFeatures(killer)
                && rollChance(victim.getRandom(), config.brokenGunDropChanceWhenKillerKillsGunHolder);
    }

    public static boolean shouldBreakKillerGunOnGunKill(SREGameWorldComponent gameWorldComponent, Player shooter,
            @Nullable Player target, ItemStack stack) {
        SREConfig config = SREConfig.instance();
        if (!config.enableBrokenGunDropWhenKillerShootsPlayer || target == null || shooter.isCreative()
                || !stack.is(TMMItemTags.GUNS) || isBrokenGun(stack)) {
            return false;
        }
        return gameWorldComponent.canUseKillerFeatures(shooter)
                && rollChance(shooter.getRandom(), config.brokenGunDropChanceWhenKillerShootsPlayer);
    }

    private static boolean rollChance(RandomSource random, int chancePercent) {
        int chance = Math.max(0, Math.min(100, chancePercent));
        return chance > 0 && random.nextInt(100) < chance;
    }
}
