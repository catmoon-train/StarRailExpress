package org.agmas.noellesroles.game.halloween.village;

import java.util.ArrayList;
import java.util.List;

/** 先在副本上扣费和装包；只有完整成功的计划才可提交，不丢弃溢出的奖励。 */
public final class VillageInventory {
    private VillageInventory() {}

    public interface Slots<T> {
        int count(T stack);
        int limit(T stack);
        boolean currency(T stack);
        boolean matches(T first, T second);
        T withCount(T stack, int count);
    }

    /** 返回 null 表示余额或背包空间不足，原始列表及其元素始终不被修改。 */
    public static <T> List<T> plan(List<T> original, int price, T reward, int amount, Slots<T> slots) {
        if (price < 0 || amount < 1 || amount > 4096 || slots.limit(reward) < 1) return null;
        long balance = 0;
        List<T> result = new ArrayList<>(original.size());
        for (T stack : original) {
            if (slots.currency(stack)) balance += slots.count(stack);
            result.add(slots.withCount(stack, slots.count(stack)));
        }
        if (balance < price) return null;
        int remaining = price;
        for (int i = 0; i < result.size() && remaining > 0; i++) {
            T stack = result.get(i);
            if (!slots.currency(stack)) continue;
            int take = Math.min(remaining, slots.count(stack));
            result.set(i, slots.withCount(stack, slots.count(stack) - take));
            remaining -= take;
        }
        remaining = amount;
        // 优先合并，再使用空格；扣费腾出的格子也可以接收商品。
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < result.size() && remaining > 0; i++) {
                T stack = result.get(i);
                int count = slots.count(stack);
                if (pass == 0 ? count == 0 || !slots.matches(stack, reward) : count != 0) continue;
                int room = Math.min(64, slots.limit(reward)) - count;
                int add = Math.min(remaining, Math.max(0, room));
                if (add > 0) {
                    result.set(i, slots.withCount(reward, count + add));
                    remaining -= add;
                }
            }
        }
        return remaining == 0 ? List.copyOf(result) : null;
    }
}
