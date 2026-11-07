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

package org.agmas.noellesroles.game.fake_steve;

/**
 * 伪人寻路的<b>路径节点类型</b>，语义与代价对齐原版 {@code net.minecraft.world.level.pathfinder.PathType}。
 *
 * <p>
 * 原版把「一个方块位置能不能走、走的代价多大」压缩成一个枚举 + 一个代价（{@code Node#costMalus}），
 * 再用 A* 把代价累加进实际距离 {@code g}。这里保留同样的两层语义，只是把类型收敛到本模组真正用得到的几种：
 * 船舱/车厢里没有栅栏、铁轨、蜂蜜块这些原版特例，但**有**可自动打开的小门、楼梯、半砖和水域。
 *
 * <p>
 * 约定：代价为负数表示<b>不可通行</b>（原版同样以 -1 表示 {@code BLOCKED}），代价越大越难走。
 * 该类是纯数据，不读世界，因此可以被单元测试直接断言。
 */
enum FakeStevePathType {
    /** 不可通行：实心方块、熔岩、世界外。 */
    BLOCKED(-1.0F),
    /** 空气（没有任何支撑），只在起点/终点的归一化时使用，正常不会被当作节点返回。 */
    OPEN(0.0F),
    /** 可以站上去的地面。 */
    WALKABLE(0.0F),
    /** 可以打开的门 / 栅栏门 / 活板门：代价与平地相同（原版 {@code WALKABLE_DOOR} 也是 0）。 */
    WALKABLE_DOOR(0.0F),
    /** 水面（可游泳）。原版陆生生物代价 8，这里保持一致：能走但尽量不走。 */
    WATER(8.0F),
    /** 贴着岸边的水：原版同样 8。 */
    WATER_BORDER(8.0F),
    /** 会受伤的方块（火、岩浆块、仙人掌、细雪）。原版僵尸为 16，这里取 8，绕路优先但允许穿过。 */
    DANGEROUS(8.0F);

    private final float cost;

    FakeStevePathType(float cost) {
        this.cost = cost;
    }

    float cost() {
        return cost;
    }

    boolean passable() {
        return cost >= 0.0F;
    }
}
