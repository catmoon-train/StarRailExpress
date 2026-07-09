package org.agmas.noellesroles.client;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 把被箱庭视野剔除的方块视为空体素的射线上下文（对应 dungeons-perspective 的
 * {@code RaycastContextCull} + {@code CustomShapeTypes.CULLED}）。
 * <p>
 * 没有它，射线仍会命中那些在画面上已经被剔掉的方块 —— 指针会打在看不见的屋顶 / 二楼上。
 */
public final class HakoniwaClipContext extends ClipContext {

    public HakoniwaClipContext(Vec3 from, Vec3 to, Block block, Fluid fluid, Entity entity) {
        super(from, to, block, fluid, entity);
    }

    @Override
    public VoxelShape getBlockShape(BlockState state, BlockGetter level, BlockPos pos) {
        if (HakoniwaVisionClientHandle.shouldHideBlock(state, pos.getX(), pos.getY(), pos.getZ())) {
            return Shapes.empty();
        }
        return super.getBlockShape(state, level, pos);
    }
}
