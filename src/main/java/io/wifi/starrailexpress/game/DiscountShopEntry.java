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

package io.wifi.starrailexpress.game;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.cca.DynamicShopComponent;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.utils.RoleUtils;
import org.jetbrains.annotations.NotNull;

/**
 * 二次购买会打折的动态商店条目。
 * 
 */
public class DiscountShopEntry extends ShopEntry {
    public int discount = 0;
    public int maxDiscountCount = 1;
    public int maxPrice = Integer.MAX_VALUE;
    public int minPrice = 0;

    public DiscountShopEntry(ItemStack stack, int price) {
        this(stack, price, 50, ShopEntry.Type.WEAPON);
    }

    public DiscountShopEntry(ItemStack stack, int price, int discount,
            ShopEntry.Type type) {
        super(stack, price, type);
        this.discount = discount;
    }

    public DiscountShopEntry(ItemStack stack, int price, int discount, int maxDiscountCount,
            ShopEntry.Type type) {
        this(stack, price, discount, type);
        this.maxDiscountCount = maxDiscountCount;
    }

    public DiscountShopEntry(ItemStack stack, int price, int discount, int maxDiscountCount, int minPrice, int maxPrice,
            ShopEntry.Type type) {
        this(stack, price, discount, maxDiscountCount, type);
        this.minPrice = minPrice;
        this.maxPrice = maxPrice;
    }

    public int minPrice() {
        return minPrice;
    }

    public int maxPrice() {
        return maxPrice;
    }

    public int maxDiscountCount() {
        return maxDiscountCount;
    }

    public int discount() {
        return discount;
    }

    @Override
    public boolean onBuy(@NotNull Player player) {
        boolean durabilityMode = KillerKnifeDurability.isDurabilityModeEnabled(player.level());

        boolean success;
        if (stack().is(TMMItems.KNIFE) && durabilityMode) {
            if (KillerKnifeDurability.refreshDepletedKnives(player)) {
                // 已有耗尽的刀 -> 原地刷新一把为满耐久，并清除其余多余的耗尽刀 / refresh one in place, clear the rest
                success = true;
            } else {
                // 没有耗尽的刀 -> 与原逻辑一致，发放一把新刀（带耐久）/ no depleted knife: give a fresh one
                ItemStack fresh = this.stack().copy();
                KillerKnifeDurability.applyFreshDurability(fresh);
                success = RoleUtils.insertStackInFreeSlot(player, fresh);
            }
        } else {
            // 耐久模式关闭（或非 murder）：保持原版行为，发放一把无耐久的普通刀。
            // Durability mode off (or non-murder): original behaviour, give a plain
            // durability-less knife.
            success = super.onBuy(player);
        }

        // 首购 -50% 折扣仍由 murder 模式决定（与耐久开关解耦）。
        // The first-purchase -50% discount is still gated by murder mode (decoupled
        // from the durability toggle).
        if (success) {
            applyPurchaseDiscount(player);
        }
        return success;
    }

    /**
     * 首次购买后为后续购买挂上 -50% 折扣。 / After the first purchase, attach a -50% discount for
     * later buys.
     */
    private void applyPurchaseDiscount(@NotNull Player player) {
        DynamicShopComponent dynamicShop = DynamicShopComponent.KEY.get(player);
        ResourceLocation stackId = BuiltInRegistries.ITEM.getKey(this.stack().getItem());
        int buyCount = dynamicShop.getPurchaseCount(stackId);
        if (buyCount < maxDiscountCount) {
            try {
                int discountPercent = (int) Math.powExact(discount, buyCount + 1);
                int truePrice = price() * (100 - discountPercent) / 100;
                if (truePrice > maxPrice) {
                    discountPercent = ((price() - maxPrice) * 100 / price());
                } else if (truePrice < minPrice) {
                    discountPercent = (price() - minPrice) * 100 / price();
                }
                dynamicShop.setPercentDiscount(stackId, discountPercent);
            } catch (ArithmeticException e) {
                SRE.LOGGER.error(
                        "Error while calc discount! Infomation: Stack {}, Original Price {}, Buy Count {}, Max Discount Count {}, Discount Percent Per {}, Min Price {}, Max Price {}",
                        stackId.toString(), price(), buyCount, maxDiscountCount, discount, minPrice, maxPrice, e);
                maxDiscountCount = buyCount;
                // 已经报错了，不能再打折买了。
            }
        }
        dynamicShop.recordPurchase(stackId);
    }
}
