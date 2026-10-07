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

package io.wifi.starrailexpress.customitem;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 自定义列车物品的「原版冷却键」。
 *
 * <p>
 * <b>问题</b>：所有自定义列车物品共用同一个注册物品 {@code starrailexpress:custom_item}，
 * 而原版 {@link net.minecraft.world.item.ItemCooldowns} 是按 {@link Item} 记的，
 * 于是 A 枪进入冷却会把 B 刀、绷带、手铐一起顶掉。
 *
 * <p>
 * <b>做法</b>：给每个自定义物品 id 单独造一个<b>未注册</b>的 {@link Item} 实例当冷却键，
 * 冷却条目依然写在<b>原版那个</b> {@code Map<Item, CooldownInstance>} 里。
 * 这样：
 * <ul>
 * <li>存储、计时（{@code tickCount}）、剩余比例、过期清理仍然是原版逻辑，一行都不用重写；</li>
 * <li>时间静止不推进（{@code PlayerEntityMixin}）、时间回溯快照、每局清冷却等
 * 只认原版 {@code ItemCooldowns} 的代码自动就覆盖到了自定义物品；</li>
 * <li>不同自定义物品的键不同，<b>不再互相顶掉冷却</b>。</li>
 * </ul>
 *
 * <p>
 * 键本身是「未注册物品」，所以不能走原版的 {@code ClientboundCooldownPacket}（它要按
 * {@code ResourceLocation} 序列化，未注册物品拿不到 id 会直接崩）。同步改走
 * {@link io.wifi.starrailexpress.network.CustomItemCooldownS2CPayload}，包里带的是自定义物品 id 字符串，
 * 客户端用同一个字符串拿到<b>同一个</b>键，两边查的是同一张表。
 */
public final class CustomItemCooldownKeys {
    /** 自定义物品 id -> 该物品专属的冷却键。 */
    private static final Map<String, Item> BY_ID = new ConcurrentHashMap<>();
    /** 冷却键 -> 自定义物品 id（反查，用来判断某个 {@link Item} 是不是我们的键）。 */
    private static final Map<Item, String> BY_KEY = new ConcurrentHashMap<>();

    private CustomItemCooldownKeys() {
    }

    /** 取（并按需创建）某个自定义物品 id 的冷却键；id 为空返回 null。 */
    public static Item keyOf(String itemId) {
        if (itemId == null || itemId.isEmpty()) {
            return null;
        }
        return BY_ID.computeIfAbsent(itemId, id -> {
            // 未注册的 Item 实例：只当 Map 的键用，永远不会进物品栏、不会被序列化
            Item key = new Item(new Item.Properties().stacksTo(1));
            BY_KEY.put(key, id);
            return key;
        });
    }

    /** 取物品栈对应的冷却键；不是自定义列车物品返回 null。 */
    public static Item keyFor(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        return keyOf(CustomItemLoader.getCustomItemId(stack));
    }

    /** 反查：这个 {@link Item} 是不是某个自定义物品的冷却键；不是返回 null。 */
    public static String idOf(Item key) {
        return key == null ? null : BY_KEY.get(key);
    }

    /** 这个 {@link Item} 是不是自定义物品的冷却键。 */
    public static boolean isCustomKey(Item key) {
        return key != null && BY_KEY.containsKey(key);
    }
}