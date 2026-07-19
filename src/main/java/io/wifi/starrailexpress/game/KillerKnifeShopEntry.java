package io.wifi.starrailexpress.game;

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
 * 杀手刀的动态商店条目。 / Dynamic shop entry for the killer knife.
 *
 * <p>
 * 这是 {@link DynamicShopComponent} 的示例落地，仅在 murder 或继承 murder 的模式下生效：
 * <ul>
 * <li>购买的刀拥有 {@link KillerKnifeDurability#MAX_DURABILITY} 点耐久；</li>
 * <li>若背包内已有「耐久耗尽」的刀，则原地替换为满耐久（而非再发一把）；</li>
 * <li>否则按原逻辑发放一把新刀；</li>
 * <li>首次购买刀后，后续购买价格 -50%（写入玩家的 {@link DynamicShopComponent}）。</li>
 * </ul>
 *
 * <p>
 * 在非 murder 模式下，行为与普通 {@link ShopEntry} 完全一致（发放一把无耐久的刀）。
 * Outside murder modes this behaves exactly like a plain {@link ShopEntry}
 * (gives a normal,
 * durability-less knife).
 */
public class KillerKnifeShopEntry extends ShopEntry {
    public int discount = 50;

    public KillerKnifeShopEntry(int price) {
        this(TMMItems.KNIFE.getDefaultInstance(), price);
    }

    public KillerKnifeShopEntry(ItemStack stack, int price) {
        this(stack, price, 50);
    }

    public KillerKnifeShopEntry(ItemStack stack, int price, int discount) {
        super(stack, price, ShopEntry.Type.WEAPON);
        this.discount = discount;
    }

    @Override
    public boolean onBuy(@NotNull Player player) {
        boolean murder = KillerKnifeDurability.isMurderMode(player.level());
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
        if (success && murder) {
            applyFirstPurchaseDiscount(player);
        }
        return success;
    }

    /**
     * 首次购买后为后续购买挂上 -50% 折扣。 / After the first purchase, attach a -50% discount for
     * later buys.
     */
    private void applyFirstPurchaseDiscount(@NotNull Player player) {
        if (!KillerKnifeDurability.isDurabilityModeEnabled(player.level())) {
            return;
        }
        DynamicShopComponent dynamicShop = DynamicShopComponent.KEY.get(player);
        ResourceLocation knifeId = BuiltInRegistries.ITEM.getKey(this.stack().getItem());
        if (dynamicShop.getPurchaseCount(knifeId) == 0) {
            dynamicShop.setPercentDiscount(knifeId, discount);
        }
        dynamicShop.recordPurchase(knifeId);
    }
}
