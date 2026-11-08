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

import io.wifi.starrailexpress.content.block.SmallDoorBlock;
import io.wifi.starrailexpress.content.block_entity.DoorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/** Identifies blocks that the possessed body may open while following a route. */
final class FakeSteveDoorAccess {
    private FakeSteveDoorAccess() {
    }

    static boolean isOpenablePassage(BlockState state) {
        return state.getBlock() instanceof DoorBlock
                || state.getBlock() instanceof SmallDoorBlock
                || state.getBlock() instanceof FenceGateBlock
                || state.getBlock() instanceof TrapDoorBlock
                // Covers registered glass/sliding doors and future door implementations
                // that expose the standard open property without subclassing vanilla DoorBlock.
                || state.hasProperty(BlockStateProperties.OPEN);
    }

    static boolean isOpen(BlockState state) {
        return !state.hasProperty(BlockStateProperties.OPEN)
                || state.getValue(BlockStateProperties.OPEN);
    }

    static boolean isInsideApproachCorridor(double bodyX, double bodyZ,
                                            double routeX, double routeZ,
                                            double doorX, double doorZ) {
        double routeDx = routeX - bodyX;
        double routeDz = routeZ - bodyZ;
        double routeLengthSqr = routeDx * routeDx + routeDz * routeDz;
        if (routeLengthSqr < 1.0E-4D) {
            double dx = doorX - bodyX;
            double dz = doorZ - bodyZ;
            return dx * dx + dz * dz <= 1.0D;
        }
        double projection = ((doorX - bodyX) * routeDx + (doorZ - bodyZ) * routeDz)
                / routeLengthSqr;
        if (projection < -0.15D || projection > 1.35D) {
            return false;
        }
        double closestX = bodyX + routeDx * Math.max(0.0D, Math.min(1.0D, projection));
        double closestZ = bodyZ + routeDz * Math.max(0.0D, Math.min(1.0D, projection));
        double distanceX = doorX - closestX;
        double distanceZ = doorZ - closestZ;
        return distanceX * distanceX + distanceZ * distanceZ <= 1.0D;
    }

    /**
     * 门当前是否<b>真的</b>能让人通过。
     *
     * <p>
     * 判据顺序刻意从「便宜」到「昂贵」：
     * <ol>
     * <li>方块状态 {@code OPEN}（原版门、栅栏门、活板门都靠它）</li>
     * <li>模组门实体的 {@code open} 字段——列车门/小门的动画目标是先写在实体里的，
     * 方块状态要等 {@code toggleBlocks()} 才同步，只看方块状态会在「刚按下、门正在滑开」
     * 的那几刻误判成关闭</li>
     * <li>上下半门任一打开即算打开（实体只挂在下半部分）</li>
     * </ol>
     *
     * @param level 服务端世界；为 {@code null} 时只看方块状态（网格测试用）
     */
    static boolean isPassableNow(Level level, BlockPos pos, BlockState state) {
        if (!isOpenablePassage(state)) return false;
        boolean stateOpen = state.hasProperty(BlockStateProperties.OPEN)
                && state.getValue(BlockStateProperties.OPEN);
        if (level == null) return stateOpen;
        if (stateOpen) return true;
        BlockPos lower = lowerHalf(state, pos);
        if (level.getBlockEntity(lower) instanceof DoorBlockEntity entity) {
            return FakeStevePathPolicy.isOpenByStateOrEntity(false, entity.isOpen());
        }
        return false;
    }

    /**
     * 门这一格对应的「下半部分」位置：模组小门的方块实体只挂在下半格。
     *
     * <p>
     * {@link SmallDoorBlock#getLowerHalfPos} 需要 {@code SmallDoorBlock} 实例，而列车门/平面门
     * 都继承自它，所以这里直接按 {@code HALF} 属性判断，省掉一次 instanceof。
     */
    static BlockPos lowerHalf(BlockState state, BlockPos pos) {
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
            return pos.below();
        }
        return pos;
    }

}
