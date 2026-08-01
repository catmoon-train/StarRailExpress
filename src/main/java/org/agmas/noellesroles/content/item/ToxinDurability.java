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

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.init.ModItems;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 毒师毒针「有限耐久」行为的工具类。 / Helper for the poisoner toxin's limited-durability behaviour.
 *
 * <p>毒针不再是消耗品，而是与杀手刀一致的 {@link #MAX_DURABILITY} 点耐久武器：每次成功下毒消耗 1 点，
 * 耐久耗尽后毒针不会消失，但无法继续使用，需要回商店购买补满（第二次购买半价，参见
 * {@link ToxinShopEntry}）。 / The toxin is no longer a consumable; like the killer knife it carries
 * {@link #MAX_DURABILITY} durability, each successful poison consuming one point. When depleted the
 * toxin does not disappear but can no longer be used until re-bought in the shop (second buy at half
 * price, see {@link ToxinShopEntry}).
 *
 * <p>耐久通过逐栈的 {@code MAX_DAMAGE}/{@code DAMAGE} 数据组件实现（毒针在注册时即带满耐久）。
 * Durability is stored via per-stack {@code MAX_DAMAGE}/{@code DAMAGE} data components (the toxin is
 * registered with full durability).
 */
public final class ToxinDurability {
    /** 毒针的耐久点数。 / Durability points of the toxin. */
    public static final int MAX_DURABILITY = 3;

    private ToxinDurability() {
    }

    /** 该物品栈是否为带耐久的毒针。 / Whether the stack is a toxin carrying our durability marker. */
    public static boolean isDurabilityToxin(@NotNull ItemStack stack) {
        return stack.is(ModItems.TOXIN) && stack.has(DataComponents.MAX_DAMAGE);
    }

    /** 毒针耐久是否已耗尽。 / Whether a toxin is fully depleted. */
    public static boolean isDepleted(@NotNull ItemStack stack) {
        return isDurabilityToxin(stack) && stack.getDamageValue() >= stack.getMaxDamage();
    }

    /** 把毒针重置为满耐久。 / Reset a toxin stack to full durability. */
    public static void applyFreshDurability(@NotNull ItemStack stack) {
        stack.set(DataComponents.MAX_DAMAGE, MAX_DURABILITY);
        stack.set(DataComponents.DAMAGE, 0);
    }

    /**
     * 消耗一点耐久（封顶为最大值，永不破坏/移除物品）。
     * Consume one durability point (capped at max; never breaks or removes the item).
     *
     * @return 消耗后是否已耗尽 / whether the toxin is depleted after this consumption
     */
    public static boolean consumeOne(@NotNull ItemStack stack) {
        if (!isDurabilityToxin(stack)) {
            return false;
        }
        int next = Math.min(stack.getMaxDamage(), stack.getDamageValue() + 1);
        stack.setDamageValue(next);
        return next >= stack.getMaxDamage();
    }

    /**
     * 在玩家背包中查找一把「耐久耗尽」的毒针。 / Find a depleted toxin in the player's inventory.
     */
    @Nullable
    public static ItemStack findDepletedToxin(@NotNull Player player) {
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && isDepleted(stack)) {
                return stack;
            }
        }
        return null;
    }

    /**
     * 把背包内所有「耐久耗尽」的毒针整理为最多一把满耐久：原地刷新第一把，清除其余多余的耗尽毒针，
     * 避免玩家重复购买后背包残留没耐久的毒针。
     * Refresh the first depleted toxin to full durability in place and remove every other depleted
     * toxin, so re-buying never leaves a leftover unusable toxin in the inventory.
     *
     * @return 是否找到并刷新了至少一把耗尽的毒针 / whether at least one depleted toxin was refreshed
     */
    public static boolean refreshDepletedToxins(@NotNull Player player) {
        var inventory = player.getInventory();
        ItemStack refreshed = null;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty() || !isDepleted(stack)) {
                continue;
            }
            if (refreshed == null) {
                applyFreshDurability(stack);
                refreshed = stack;
            } else {
                inventory.setItem(i, ItemStack.EMPTY);
            }
        }
        return refreshed != null;
    }
}
