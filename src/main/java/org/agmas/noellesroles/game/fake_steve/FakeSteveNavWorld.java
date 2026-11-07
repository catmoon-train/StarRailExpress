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

import net.minecraft.core.BlockPos;

/**
 * A* 需要知道的最小世界信息集合（等价于原版 {@code NodeEvaluator} 的对外能力）。
 *
 * <p>
 * 抽出这一层的目的有两个：
 * <ul>
 * <li>让 {@link FakeSteveNavigator} 的搜索算法<b>不依赖 {@code ServerLevel}</b>，可以用一张网格图直接单元测试
 * （原版同样是「评估器读世界、{@code PathFinder} 只认节点」的分层）；</li>
 * <li>把「读方块」与「搜索」的缓存分开——搜索过程中每个节点会被反复询问，
 * 由实现类负责缓存，避免重复读方块状态。</li>
 * </ul>
 *
 * <p>
 * 所有方法的 {@code feet} 参数都指<b>身体双脚所在的那一格</b>（不是方块中心，也不是脚下的支撑方块）。
 */
interface FakeSteveNavWorld {

    /** 该格作为「双脚所在格」的路径节点类型。 */
    FakeStevePathType nodeType(BlockPos feet);

    /** 双脚能否站在该格：脚部与头部空间足够，且有支撑（或在水中）。 */
    default boolean standable(BlockPos feet) {
        return nodeType(feet).passable();
    }

    /** 该格是否被其他玩家（或正在被替换的身体）占住。 */
    boolean blockedByBody(BlockPos feet);

    /** 该格双脚是否浸在水里（可以游泳）。 */
    boolean isWater(BlockPos feet);

    /** 站在该格时双脚的世界坐标高度（含半砖/楼梯的精确高度）。 */
    double standY(BlockPos feet);

    /** 贴着悬空边缘的格子：路线能不走就不走。 */
    boolean dropBeside(BlockPos feet);

    /**
     * 该格是否属于「原版不需要跳跃输入就能抬脚上去」的台阶类方块（楼梯、半砖）。
     *
     * <p>
     * 禁跳地图里这条判定决定成败：真人依然能走上楼梯和半砖（游戏自动抬脚），
     * 只是不能按跳跃键。寻路必须复现同一个边界，否则禁跳地图里所有带楼梯的房间都会变成孤岛。
     */
    default boolean stepBlock(BlockPos feet) {
        return false;
    }

    /** 路径简化时必须保留的节点：门（要停下来开）与台阶（要抬脚）。 */
    boolean preserveNode(BlockPos feet);

    /** 斜向移动时，斜穿经过的直角列是否在这一层高度上可以通过。 */
    boolean canCutCorner(BlockPos column, double limitY);

    /** 把任意位置吸附到「真正能站的那一格」。 */
    BlockPos normalize(BlockPos pos);
}
