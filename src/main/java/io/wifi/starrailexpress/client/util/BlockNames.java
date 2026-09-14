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
import net.minecraft.world.level.block.Block;

import java.util.HashMap;
import java.util.Map;

/**
 * 取方块显示名的安全封装。
 *
 * <p>少数第三方方块把 {@code getDescriptionId} 写成了和它的 BlockItem 互相递归
 * （例如 wathextras 的 {@code WallCandelabreBlock}：{@code block.getDescriptionId()} →
 * {@code item.getDescriptionId()} → {@code block.getDescriptionId()} → …），
 * 直接调 {@code getName()} 会栈溢出。
 *
 * <p>方块展示方块的选择器要遍历整个方块注册表并对每个方块取显示名，
 * 所以这里兜住这类异常并退回注册名；结果缓存，不会每帧重复踩。
 */
public final class BlockNames {

    private static final Map<Block, String> CACHE = new HashMap<>();

    private BlockNames() {
    }

    /**
     * 方块显示名；取名字时抛异常（栈溢出或运行期异常）就退回注册名。
     * 方块是注册表单例、不会卸载，所以这里用强引用缓存不会泄漏。
     */
    public static synchronized String of(Block block) {
        String cached = CACHE.get(block);
        if (cached != null) {
            return cached;
        }
        String name;
        try {
            name = block.getName().getString();
        } catch (StackOverflowError | RuntimeException failure) {
            name = BuiltInRegistries.BLOCK.getKey(block).toString();
        }
        if (name == null || name.isEmpty()) {
            name = BuiltInRegistries.BLOCK.getKey(block).toString();
        }
        CACHE.put(block, name);
        return name;
    }
}
