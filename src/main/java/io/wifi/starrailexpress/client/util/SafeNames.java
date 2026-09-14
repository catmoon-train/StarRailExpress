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

package io.wifi.starrailexpress.client.util;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import java.util.HashMap;
import java.util.Map;

/**
 * 取显示名的安全封装。
 *
 * <p>少数第三方内容把 {@code getDescriptionId} 写成了和它的物品/方块互相递归
 * （例如 wathextras 的 {@code WallCandelabreBlock}：{@code block.getDescriptionId()} →
 * {@code item.getDescriptionId()} → {@code block.getDescriptionId()} → …），
 * 直接调 {@code getName()} / {@code getDescription()} 会栈溢出。
 *
 * <p>展示方块的选择器要遍历整个注册表取名字，所以这里兜住这类异常并退回注册名；
 * 结果缓存，不会每帧重复踩。（方块/物品/实体都是注册表单例，用强引用缓存不会泄漏。）
 */
public final class SafeNames {

    private static final Map<Block, String> BLOCK_CACHE = new HashMap<>();
    private static final Map<Item, String> ITEM_CACHE = new HashMap<>();
    private static final Map<EntityType<?>, String> ENTITY_CACHE = new HashMap<>();

    private SafeNames() {
    }

    public static synchronized String of(Block block) {
        String cached = BLOCK_CACHE.get(block);
        if (cached != null) {
            return cached;
        }
        String name;
        try {
            name = block.getName().getString();
        } catch (StackOverflowError | RuntimeException failure) {
            name = BuiltInRegistries.BLOCK.getKey(block).toString();
        }
        return store(BLOCK_CACHE, block, name, BuiltInRegistries.BLOCK.getKey(block).toString());
    }

    public static synchronized String of(Item item) {
        String cached = ITEM_CACHE.get(item);
        if (cached != null) {
            return cached;
        }
        String name;
        try {
            name = new ItemStack(item).getHoverName().getString();
        } catch (StackOverflowError | RuntimeException failure) {
            name = BuiltInRegistries.ITEM.getKey(item).toString();
        }
        return store(ITEM_CACHE, item, name, BuiltInRegistries.ITEM.getKey(item).toString());
    }

    public static synchronized String of(EntityType<?> type) {
        String cached = ENTITY_CACHE.get(type);
        if (cached != null) {
            return cached;
        }
        String name;
        try {
            name = type.getDescription().getString();
        } catch (StackOverflowError | RuntimeException failure) {
            name = BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
        }
        return store(ENTITY_CACHE, type, name, BuiltInRegistries.ENTITY_TYPE.getKey(type).toString());
    }

    private static <K> String store(Map<K, String> cache, K key, String name, String fallback) {
        String resolved = name == null || name.isEmpty() ? fallback : name;
        cache.put(key, resolved);
        return resolved;
    }
}
