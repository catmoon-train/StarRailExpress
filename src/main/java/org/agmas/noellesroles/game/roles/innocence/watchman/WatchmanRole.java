package org.agmas.noellesroles.game.roles.innocence.watchman;

import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.content.item.WatchmanShopEntry;
import org.agmas.noellesroles.init.ModItems;

import java.util.ArrayList;
import java.util.List;

/**
 * 更夫（平民阵营）。
 *
 * <ul>
 * <li>可以透视到游戏时间（{@code canSeeTime = true}）；</li>
 * <li>技能「敲钟」：使周围玩家短暂透视游戏时间（CD 90 秒）；</li>
 * <li>商店：锣（100）、梆（100），每购买一次价格 +25（冷却固定 120 秒）。</li>
 * </ul>
 *
 * 规则本体集中在 {@link org.agmas.noellesroles.role_data.innocence.WatchmanRoleData}。
 */
public class WatchmanRole extends NormalRole {

    /** 锣的基础价格。 */
    public static final int GONG_BASE_PRICE = 100;
    /** 梆的基础价格。 */
    public static final int BANG_BASE_PRICE = 100;
    /** 每购买一次的价格增幅（冷却不随购买次数增加）。 */
    public static final int PRICE_STEP = 25;

    public WatchmanRole(ResourceLocation identifier, int color, boolean isInnocent, boolean canUseKiller,
            MoodType moodType, int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, canSeeTime);
    }

    @Override
    public List<ShopEntry> getShopEntries() {
        ArrayList<ShopEntry> shop = new ArrayList<>();
        shop.add(new WatchmanShopEntry(ModItems.GONG, GONG_BASE_PRICE, PRICE_STEP, ShopEntry.Type.TOOL));
        shop.add(new WatchmanShopEntry(ModItems.BANG, BANG_BASE_PRICE, PRICE_STEP, ShopEntry.Type.TOOL));
        return shop;
    }
}
