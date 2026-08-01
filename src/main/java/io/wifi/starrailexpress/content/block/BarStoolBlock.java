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

package io.wifi.starrailexpress.content.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class BarStoolBlock extends MountableBlock {
    private static final Vec3 SIT_POS = new Vec3(0.5f, -0.2f, 0.5f);

    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(6, 0, 6, 10, 1, 10),
            Block.box(7, 1, 7, 9, 9, 9),
            Block.box(4, 4, 4, 12, 5, 12),
            Block.box(3, 9, 3, 13, 12, 13)
    );

    public BarStoolBlock(Properties settings) {
        super(settings);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public Vec3 getSitPos(Level world, BlockState state, BlockPos pos) {
        return SIT_POS;
    }
}
