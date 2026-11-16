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

package org.agmas.noellesroles.game.roles.innocence.magician;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.game.ShopContent;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role_data.innocence.MagicianRoleData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

/**
 * 魔术师的商店装配。
 *
 * <p>魔术师没有自己的固定商店：<b>商店完全跟随当前伪装的杀手职业</b>
 * （读 {@link io.wifi.starrailexpress.game.ShopContent} 的同一套解析流程，所以被伪装职业的
 * 自定义商店、默认杀手商店、动态价格都能原样继承），所有条目统一加价 20%，
 * 并套上 {@link MagicianShopEntry} 的防护外壳。</p>
 *
 * <p>结果按「玩家 + 伪装职业」缓存：伪装职业每局只会确定一次，条目内容本身与时间无关
 * （折扣 / 阶梯价由 {@code DynamicShopComponent} 在渲染与结算时另行叠加），
 * 而商店界面每帧都会取一次条目列表，缓存可以避免每帧重建整份伪装商店。</p>
 */
public final class MagicianShop {

    /** 伪装商店的加价倍率：所有商品价格 +20%。 */
    public static final double PRICE_MULTIPLIER = 1.2;

    /** 玩家 → 已装配好的商店（WeakHashMap 跟随玩家实例生命周期，避免离线后泄漏）。 */
    private static final Map<Player, CachedShop> CACHE = new WeakHashMap<>();

    /** 递归保护：伪装职业自身的商店可能反过来查询魔术师的商店（如复制类职业）。 */
    private static final ThreadLocal<Boolean> BUILDING = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private MagicianShop() {
    }

    /** 缓存的商店：伪装职业变化时自动失效。 */
    private record CachedShop(@Nullable ResourceLocation disguiseId, List<ShopEntry> entries) {
    }

    /**
     * 构造魔术师当前的商店。
     *
     * <p>伪装职业尚未确定 / 解析失败时退回默认杀手商店（同样加价并套防护），
     * 保证魔术师任何时候都不会出现空商店或崩溃。</p>
     */
    public static List<ShopEntry> buildEntries(@Nullable Player player) {
        SRERole disguised = MagicianDisguiseHelper.resolveDisguisedRole(player);
        ResourceLocation disguiseId = disguised == null ? null : disguised.identifier();
        if (player != null) {
            CachedShop cached = CACHE.get(player);
            if (cached != null && Objects.equals(cached.disguiseId(), disguiseId)) {
                return cached.entries();
            }
        }
        if (BUILDING.get()) {
            // 递归回环：直接给回默认杀手商店，避免无限递归
            return wrap(ShopContent.getDefaultKnifeEntries());
        }
        BUILDING.set(Boolean.TRUE);
        List<ShopEntry> entries;
        try {
            entries = createEntries(player, disguised);
        } finally {
            BUILDING.set(Boolean.FALSE);
        }
        if (player != null) {
            CACHE.put(player, new CachedShop(disguiseId, entries));
        }
        return entries;
    }

    private static List<ShopEntry> createEntries(@Nullable Player player, @Nullable SRERole disguised) {
        List<ShopEntry> raw = null;
        if (disguised != null) {
            try {
                raw = ShopContent.getShopEntries(disguised, player);
            } catch (Throwable t) {
                SRE.LOGGER.warn("Magician disguise shop failed for {}", disguised.identifier(), t);
            }
        }
        if (raw == null || raw.isEmpty()) {
            raw = ShopContent.getDefaultKnifeEntries();
        }
        List<ShopEntry> result = wrap(adaptEntries(disguised, raw));
        if (result.isEmpty()) {
            // 极端兜底：伪装商店整体不可用时给回默认杀手商店
            result = wrap(ShopContent.getDefaultKnifeEntries());
        }
        return result;
    }

    /**
     * 把依赖「职业专属数据」的条目换成魔术师自己的实现。
     *
     * <p>目前只有刽子手的「射击狂热」：原条目走 {@code ExecutionerRoleData#startFrenzy()}，
     * 魔术师身上没有该组件，会直接购买失败。其余条目（包括 Dream 的巨幕面具）原样保留，
     * 由 {@code MagicianRole} 把疯狂模式钩子转发给伪装职业，等价于原职业的实现。</p>
     */
    private static List<ShopEntry> adaptEntries(@Nullable SRERole disguised, List<ShopEntry> raw) {
        if (disguised == null || !ModRoles.EXECUTIONER_ID.equals(disguised.identifier())) {
            return raw;
        }
        List<ShopEntry> adapted = new ArrayList<>(raw.size());
        for (ShopEntry entry : raw) {
            ItemStack stack = entry == null ? null : entry.stack();
            if (stack != null && stack.is(TMMItems.PSYCHO_MODE)) {
                adapted.add(createFrenzyEntry(entry));
            } else {
                adapted.add(entry);
            }
        }
        return adapted;
    }

    /** 射击狂热：展示与价格沿用刽子手条目，触发时改用魔术师自己的狂热状态。 */
    private static ShopEntry createFrenzyEntry(ShopEntry original) {
        return new ShopEntry(original.stack().copy(), original.price(), original.type()) {
            @Override
            public boolean onBuy(@NotNull Player player) {
                MagicianRoleData data = RoleData.getNullable(MagicianRoleData.class, player);
                if (data == null || !data.startFrenzy()) {
                    return false;
                }
                player.getCooldowns().addCooldown(TMMItems.PSYCHO_MODE, 20 * 60);
                return true;
            }
        };
    }



    /** 逐条套上加价 + 防护外壳；单条装配失败只丢这一条，不影响整份商店。 */
    private static List<ShopEntry> wrap(List<ShopEntry> raw) {
        List<ShopEntry> result = new ArrayList<>(raw.size());
        for (ShopEntry entry : raw) {
            if (entry == null) {
                continue;
            }
            try {
                result.add(new MagicianShopEntry(entry));
            } catch (Throwable t) {
                SRE.LOGGER.warn("Magician shop entry skipped: {}", entry.stack(), t);
            }
        }
        return result;
    }
}
