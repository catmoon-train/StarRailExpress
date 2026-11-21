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

package io.wifi.starrailexpress.client.ponder;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.game.ShopContent;
import io.wifi.starrailexpress.util.ShopEntry;
import org.agmas.noellesroles.init.RoleShopHandler;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * 把本项目的职业商店数据转换成思索覆盖层可渲染的陈列项。
 *
 * <p>数据源与游戏内商店界面完全一致（{@link ShopContent#getShopEntries}），
 * 因此思索里展示的商品、价格与货币都与实际商店相符。
 * 没有观看者时按「无玩家」查询，只取静态配置的条目。
 */
public final class PonderShopEntries {

    /** 覆盖层最多展示的条目数，超出部分不画，避免超出屏幕。 */
    private static final int MAX_ENTRIES = 8;

    private PonderShopEntries() {
    }

    /** 构造职业商店的陈列项；没有商品时返回空列表。 */
    public static List<PonderShopOverlayElement.Entry> build(@Nullable SRERole role) {
        if (role == null) {
            return List.of();
        }
        List<ShopEntry> source;
        try {
            // 与 U 键介绍页一致：先确保商店注册完成，再按「无玩家」读取全部展示条目。
            // 传当前玩家会让依赖对局状态的修改器过滤掉条目，导致思索里商店为空。
            if (!RoleShopHandler.haveRegistered) {
                RoleShopHandler.shopRegister();
            }
            source = ShopContent.getShopEntries(role.getIdentifier(), null);
        } catch (Throwable error) {
            return List.of();
        }
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        List<PonderShopOverlayElement.Entry> result = new ArrayList<>();
        for (ShopEntry entry : source) {
            if (result.size() >= MAX_ENTRIES) {
                break;
            }
            ItemStack stack = entry.stack();
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            ShopEntry.Currency currency = entry.currency() == null ? ShopEntry.Currency.MONEY : entry.currency();
            String priceText = Integer.toString(entry.price()) + currency.iconText();
            result.add(new PonderShopOverlayElement.Entry(
                    stack.copy(), entry.price(), priceText, currency.color()));
        }
        return List.copyOf(result);
    }
}
