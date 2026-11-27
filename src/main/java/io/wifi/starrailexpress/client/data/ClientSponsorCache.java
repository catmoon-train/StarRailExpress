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

package io.wifi.starrailexpress.client.data;

import io.wifi.starrailexpress.api.PlushApi;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 客户端缓存的赞助者名单，由 {@link io.wifi.starrailexpress.sponsor.SponsorListPayload} 更新，
 * 供游戏介绍界面（{@code RoleIntroduceScreen}）的「赞助者」栏目列出名单、渲染头像与赞助链接。
 * <p>
 * 名单顺序沿用服务端下发的顺序（不做任何「按贡献排序」的处理，界面文案里也写明排名不分前后）。
 */
public final class ClientSponsorCache {
    private static volatile List<Item> plushItems = List.of();
    private static volatile Set<Item> plushItemSet = Set.of();
    /** 服务端下发的原始赞助者名（已规范化小写，去重），用于头像渲染。 */
    private static volatile List<String> sponsorNames = List.of();
    /** 赞助页链接（服务端配置下发）。 */
    private static volatile String sponsorPageUrl = "";

    private ClientSponsorCache() {
    }

    /** 用服务端同步来的名字列表刷新缓存（名字 -> plush 物品）。 */
    public static void update(List<String> names) {
        update(names, null);
    }

    /**
     * 用服务端同步来的名字列表与赞助页链接刷新缓存。
     *
     * @param names          赞助者名（可为 {@code null}，表示空名单）
     * @param sponsorPageUrl 赞助页链接；{@code null} 表示本次不下发、沿用旧值
     */
    public static void update(List<String> names, String sponsorPageUrl) {
        List<Item> items = new ArrayList<>();
        Set<Item> set = new LinkedHashSet<>();
        List<String> namesCopy = new ArrayList<>();
        if (names != null) {
            for (String name : names) {
                if (name == null || name.isBlank()) {
                    continue;
                }
                namesCopy.add(name);
                PlushApi.getPlushForSkin(name).ifPresent(item -> {
                    if (set.add(item)) {
                        items.add(item);
                    }
                });
            }
        }
        plushItems = List.copyOf(items);
        plushItemSet = Set.copyOf(set);
        sponsorNames = List.copyOf(namesCopy);
        if (sponsorPageUrl != null) {
            ClientSponsorCache.sponsorPageUrl = sponsorPageUrl.trim();
        }
    }

    /** 当前赞助者 plush 物品列表（顺序与名单一致，已去重）。 */
    public static List<Item> getPlushItems() {
        return plushItems;
    }

    /** 当前赞助者名字列表（顺序与名单一致，已去重）。 */
    public static List<String> getSponsorNames() {
        return sponsorNames;
    }

    /** 赞助页链接；未配置时为空串。 */
    public static String getSponsorPageUrl() {
        return sponsorPageUrl;
    }

    /** 该物品是否为赞助者 plush。 */
    public static boolean isSponsorPlush(Item item) {
        return plushItemSet.contains(item);
    }

    public static boolean isEmpty() {
        return plushItems.isEmpty();
    }

    public static void clear() {
        plushItems = List.of();
        plushItemSet = Set.of();
        sponsorNames = List.of();
        sponsorPageUrl = "";
    }
}
