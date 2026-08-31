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

import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import org.agmas.noellesroles.role.touhou.THHumanVillageRoles;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class DealerPackageItem extends Item {
    public DealerPackageItem(Properties properties) {
        super(properties);
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player,
            @NotNull InteractionHand usedHand) {
        ItemStack packageStack = player.getItemInHand(usedHand);

        if (level.isClientSide) {
            return InteractionResultHolder.sidedSuccess(packageStack, true);
        }

        List<ShopEntry> shopEntries = THHumanVillageRoles.RINNOSUKE.getShopEntries();
        if (shopEntries.isEmpty()) {
            return InteractionResultHolder.fail(packageStack);
        }

        ShopEntry entry = shopEntries.get(player.getRandom().nextInt(shopEntries.size()));
        ItemStack reward = entry.stack().copy();
        if (reward.isEmpty()) {
            return InteractionResultHolder.fail(packageStack);
        }

        // 检查快捷栏是否有空位
        Inventory inventory = player.getInventory();
        int hotbarSlot = -1;
        for (int i = 0; i < 9; i++) {
            if (inventory.getItem(i).isEmpty()) {
                hotbarSlot = i;
                break;
            }
        }

        if (hotbarSlot == -1) {
            // 快捷栏已满，不允许打开
            player.displayClientMessage(Component.translatable("item.noellesroles.dealer_package.hotbar_full"), true);
            return InteractionResultHolder.fail(packageStack);
        }

        if (!player.getAbilities().instabuild) {
            packageStack.shrink(1);
        }

        inventory.setItem(hotbarSlot, reward);

        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BUNDLE_INSERT,
                SoundSource.PLAYERS, 0.8F, 0.9F + level.random.nextFloat() * 0.2F);
        return InteractionResultHolder.sidedSuccess(packageStack, false);
    }
}
