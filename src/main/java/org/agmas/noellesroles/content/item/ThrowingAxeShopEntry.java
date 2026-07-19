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
 * 折后价。行为对齐 {@link io.wifi.starrailexpress.game.KillerKnifeShopEntry} /
 * {@link ToxinShopEntry}。
 */
public class ThrowingAxeShopEntry extends ShopEntry {
    /** 二次及以后的折扣百分比（70 = 降价 70%，即只付 3 成）。 */
    public static final int DISCOUNT_PERCENT = 70;

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
