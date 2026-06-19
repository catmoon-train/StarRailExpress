package io.wifi.starrailexpress.client.data;

import io.wifi.starrailexpress.api.PlushApi;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 客户端缓存的赞助者 plush 名单，由 {@link io.wifi.starrailexpress.sponsor.SponsorListPayload} 更新，
 * 供游戏介绍界面（{@code RoleIntroduceScreen}）列出赞助者 plush。
 */
public final class ClientSponsorCache {
    private static volatile List<Item> plushItems = List.of();
    private static volatile Set<Item> plushItemSet = Set.of();

    private ClientSponsorCache() {
    }

    /** 用服务端同步来的名字列表刷新缓存（名字 -> plush 物品）。 */
    public static void update(List<String> names) {
        List<Item> items = new ArrayList<>();
        Set<Item> set = new LinkedHashSet<>();
        if (names != null) {
            for (String name : names) {
                PlushApi.getPlushForSkin(name).ifPresent(item -> {
                    if (set.add(item)) {
                        items.add(item);
                    }
                });
            }
        }
        plushItems = List.copyOf(items);
        plushItemSet = Set.copyOf(set);
    }

    /** 当前赞助者 plush 物品列表（顺序与名单一致，已去重）。 */
    public static List<Item> getPlushItems() {
        return plushItems;
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
    }
}
