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

package io.wifi.starrailexpress.synccontent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 自定义内容通道的加载顺序不变式。
 *
 * <p>
 * 这些顺序不是「好看」而已：自定义职业的初始物品 / 任务奖励 / 商店条目、自定义修饰符的阵营与
 * 职业限制都是在**注册时**按 id 查已有索引的，顺序错了不会抛异常，而是静默丢条目（商店里那条
 * 自定义物品不显示、也买不到）。所以把顺序钉在这里，防止以后有人改 {@code dependents()} 或
 * {@code LOAD_ORDER} 时悄悄破坏依赖关系。
 */
class ContentChannelOrderTest {

    @Test
    void loadOrderCoversEveryChannelExactlyOnce() {
        assertEquals(ContentChannel.values().length, ContentChannel.LOAD_ORDER.size(),
                "LOAD_ORDER 必须覆盖全部通道，且每个只出现一次");
        for (ContentChannel channel : ContentChannel.values()) {
            assertTrue(ContentChannel.LOAD_ORDER.contains(channel), channel + " 不在 LOAD_ORDER 里");
        }
    }

    @Test
    void loadOrderIsTopological() {
        for (ContentChannel channel : ContentChannel.values()) {
            int selfIndex = ContentChannel.LOAD_ORDER.indexOf(channel);
            for (ContentChannel dependent : channel.dependents()) {
                assertTrue(ContentChannel.LOAD_ORDER.indexOf(dependent) > selfIndex,
                        dependent + " 依赖 " + channel + "，所以在 LOAD_ORDER 里必须排在它后面");
            }
        }
    }

    @Test
    void noChannelDependsOnItself() {
        for (ContentChannel channel : ContentChannel.values()) {
            assertFalse(channel.dependents().contains(channel), channel + " 不能依赖自己");
            assertFalse(channel.dependentsTransitive().contains(channel),
                    channel + " 的间接依赖里不能出现自己（依赖图必须无环）");
        }
    }

    /**
     * 依赖链就是「物品 → 职业 → 修饰符」：物品索引建好后要重新注册职业，职业换实例后要重新
     * 注册修饰符（修饰符持有 SRERole 实例）。
     */
    @Test
    void dependencyChainIsItemToRoleToModifier() {
        assertEquals(List.of(ContentChannel.CUSTOM_ROLE, ContentChannel.CUSTOM_MODIFIER),
                ContentChannel.CUSTOM_ITEM.dependentsTransitive(),
                "物品变了，职业与（经职业的）修饰符都要重新注册");
        assertEquals(List.of(ContentChannel.CUSTOM_MODIFIER),
                ContentChannel.CUSTOM_ROLE.dependentsTransitive(),
                "职业变了，引用这些职业的修饰符要重新注册");
        assertEquals(List.of(), ContentChannel.CUSTOM_MODIFIER.dependentsTransitive(),
                "修饰符没有被谁依赖");
        assertEquals(List.of(), ContentChannel.CUSTOM_BLOCK.dependentsTransitive(),
                "方块没有被谁依赖");
    }

    /** 间接依赖按加载顺序返回，调用方可以直接照着顺序重注册。 */
    @Test
    void transitiveDependentsComeBackInLoadOrder() {
        for (ContentChannel channel : ContentChannel.values()) {
            List<ContentChannel> dependents = channel.dependentsTransitive();
            for (int i = 1; i < dependents.size(); i++) {
                assertTrue(
                        ContentChannel.LOAD_ORDER.indexOf(dependents.get(i - 1)) < ContentChannel.LOAD_ORDER
                                .indexOf(dependents.get(i)),
                        channel + " 的间接依赖必须按 LOAD_ORDER 排列：" + dependents);
            }
        }
    }
}
