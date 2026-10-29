package org.agmas.noellesroles.game.roles.innocence.tailor;

import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.content.item.SteppedPriceShopEntry;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.utils.MCItemsUtils;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * 裁缝（平民阵营）。
 *
 * <ul>
 * <li>开局自带一根缝纫针和一个毛线球；</li>
 * <li>缝纫针长按右键玩家 5 秒，消耗一个毛线球为其编织一件「裁缝的衣服」（随机颜色的皮革胸甲）；
 * 衣服会自动给目标穿上（胸甲栏为空）或进入其空余快捷栏；</li>
 * <li>身着「裁缝的衣服」时：受到非 forcekill 的击杀有 5% 概率被衣服抵挡（衣服破损消失），
 * 攻击者主手物品进入冷却时额外 +5 秒，且保暖值不再自然下降；</li>
 * <li>商店：毛线球初始 75 金币、每次购买 +50；缝纫针 25 金币，仅物品栏中没有缝纫针时可购。</li>
 * </ul>
 *
 * 规则本体集中在 {@link org.agmas.noellesroles.role_data.innocence.TailorRoleData} 与
 * {@link TailorClothesHandler}。
 */
public class TailorRole extends NormalRole {

    /** 毛线球的基础价格。 */
    public static final int YARN_BALL_BASE_PRICE = 75;
    /** 毛线球每购买一次的价格增幅。 */
    public static final int YARN_BALL_PRICE_STEP = 50;
    /** 缝纫针的价格。 */
    public static final int SEWING_NEEDLE_PRICE = 25;

    public TailorRole(ResourceLocation identifier, int color, boolean isInnocent, boolean canUseKiller,
            MoodType moodType, int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, canSeeTime);
    }

    @Override
    public List<ItemStack> getDefaultItems() {
        ArrayList<ItemStack> items = new ArrayList<>();
        items.add(new ItemStack(ModItems.SEWING_NEEDLE));
        items.add(new ItemStack(ModItems.YARN_BALL));
        return items;
    }

    @Override
    public List<ShopEntry> getShopEntries() {
        ArrayList<ShopEntry> shop = new ArrayList<>();
        // 毛线球：初始 75 金币，每次购买 +50
        shop.add(new SteppedPriceShopEntry(ModItems.YARN_BALL, YARN_BALL_BASE_PRICE, YARN_BALL_PRICE_STEP,
                ShopEntry.Type.TOOL));
        // 缝纫针：25 金币，只有物品栏中没有缝纫针时才能购买
        ShopEntry needle = new ShopEntry(ModItems.SEWING_NEEDLE.getDefaultInstance(), SEWING_NEEDLE_PRICE,
                ShopEntry.Type.TOOL) {
            @Override
            public boolean canBuy(@NotNull Player player) {
                return super.canBuy(player) && MCItemsUtils.countItem(player, ModItems.SEWING_NEEDLE) <= 0;
            }
        };
        needle.setFailedMessage(Component.translatable("message.noellesroles.tailor.needle_owned"));
        shop.add(needle);
        return shop;
    }
}
