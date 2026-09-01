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

import io.wifi.starrailexpress.cca.DynamicShopComponent;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.init.ModItems;
import org.jetbrains.annotations.NotNull;

/**
 * 毒师毒针的商店条目，行为与杀手刀一致（{@link io.wifi.starrailexpress.game.DiscountShopEntry}）：
 * <ul>
 * <li>若背包内已有「耐久耗尽」的毒针，则原地补满耐久（而非再发一把）；</li>
 * <li>否则发放一把满耐久（{@link ToxinDurability#MAX_DURABILITY} 点）的毒针；</li>
 * <li>首次购买后，后续购买价格 -50%（写入玩家的 {@link DynamicShopComponent}）。</li>
 * </ul>
 *
 * <p>The poisoner-toxin shop entry, matching the killer knife: refill a depleted toxin in place,
 * otherwise give a fresh full-durability one, and apply a -50% discount on the second purchase.
 */
public class ToxinShopEntry extends ShopEntry {
    public ToxinShopEntry(int price) {
        super(ModItems.TOXIN.getDefaultInstance(), price, ShopEntry.Type.POISON);
    }

    @Override
    public boolean onBuy(@NotNull Player player) {
        boolean success;
        if (ToxinDurability.refreshDepletedToxins(player)) {
            // 已有耗尽的毒针 -> 原地补满，并清除其余多余的耗尽毒针 / refill one in place, clear the rest
            success = true;
        } else {
            // 没有耗尽的毒针 -> 发放一把满耐久的新毒针（默认实例即带满耐久）/ give a fresh full one
            success = super.onBuy(player);
        }

        if (success) {
            applyFirstPurchaseDiscount(player);
        }
        return success;
    }

    /** 首次购买后为后续购买挂上 -50% 折扣。 / After the first purchase, attach a -50% discount. */
    private void applyFirstPurchaseDiscount(@NotNull Player player) {
        DynamicShopComponent dynamicShop = DynamicShopComponent.KEY.get(player);
        ResourceLocation toxinId = BuiltInRegistries.ITEM.getKey(this.stack().getItem());
        if (dynamicShop.getPurchaseCount(toxinId) == 0) {
            dynamicShop.setPercentDiscount(toxinId, 50);
        }
        dynamicShop.recordPurchase(toxinId);
    }
}
