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

import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.cca.DynamicShopComponent;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.init.ModItems;
import org.jetbrains.annotations.NotNull;

/**
 * 飞斧的动态商店条目（强盗专属）。
 *
 * <ul>
 * <li>首次购买价格 {@link #BASE_PRICE}（115 金币）；</li>
 * <li>首次购买后，为后续购买挂上 {@link #DISCOUNT_PERCENT}% 折扣
 * （70% 折扣 = 只付 3 成价 ≈ 35 金币），写入玩家的 {@link DynamicShopComponent}。</li>
 * </ul>
 *
 * <p>
 * 实际扣费价由 {@link DynamicShopComponent#effectivePrice} 结算，商店 UI 也会显示同样的
 * 折后价。行为对齐 {@link io.wifi.starrailexpress.game.DiscountShopEntry} /
 * {@link ToxinShopEntry}。
 */
public class ThrowingAxeShopEntry extends ShopEntry {
    /** 二次及以后的折扣百分比（39 = 降价 39%）。 */
    public static final int DISCOUNT_PERCENT = 30;

    public ThrowingAxeShopEntry() {
        super(ModItems.THROWING_AXE.getDefaultInstance(), SREConfig.instance().knifePrice, ShopEntry.Type.WEAPON);
    }

    @Override
    public boolean onBuy(@NotNull Player player) {
        boolean success = super.onBuy(player);
        if (success) {
            applyRepurchaseDiscount(player);
        }
        return success;
    }

    /**
     * 首次购买后为后续购买挂上折扣。 / After the first purchase, attach the discount for later
     * buys.
     */
    private void applyRepurchaseDiscount(@NotNull Player player) {
        DynamicShopComponent dynamicShop = DynamicShopComponent.KEY.get(player);
        ResourceLocation axeId = BuiltInRegistries.ITEM.getKey(this.stack().getItem());
        if (dynamicShop.getPurchaseCount(axeId) == 0) {
            dynamicShop.setPercentDiscount(axeId, DISCOUNT_PERCENT);
        }
        dynamicShop.recordPurchase(axeId);
    }
}
