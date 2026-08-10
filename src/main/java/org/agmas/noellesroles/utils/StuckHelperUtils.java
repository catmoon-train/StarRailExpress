package org.agmas.noellesroles.utils;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

public class StuckHelperUtils {
    public static boolean isPlayerStuck(Player player) {
        Level level = player.level();
        AABB playerBox = player.getBoundingBox();

        int minX = Mth.floor(playerBox.minX);
        int maxX = Mth.ceil(playerBox.maxX);
        int minY = Mth.floor(playerBox.minY);
        int maxY = Mth.ceil(playerBox.maxY);
        int minZ = Mth.floor(playerBox.minZ);
        int maxZ = Mth.ceil(playerBox.maxZ);

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = minX; x < maxX; x++) {
            for (int y = minY; y < maxY; y++) {
                for (int z = minZ; z < maxZ; z++) {
                    pos.set(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (canBlockStuckPlayer(level, pos, state)) {
                        VoxelShape shape = state.getCollisionShape(level, pos);
                        if (!shape.isEmpty() && shape.bounds().move(pos).intersects(playerBox)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private static boolean canBlockStuckPlayer(Level level, BlockPos pos, BlockState state) {
        if (state.isAir())
            return false;
        if (!state.getFluidState().isEmpty())
            return false;
        if (state.is(Blocks.POWDER_SNOW))
            return false;

        // 完整立方体碰撞箱 (石头、泥土、木板等) -> 卡人
        if (state.isCollisionShapeFullBlock(level, pos))
            return true;

        // 白名单：楼梯和台阶，只要碰撞形状非空就纳入检测
        Block block = state.getBlock();
        if (block instanceof StairBlock || block instanceof SlabBlock) {
            VoxelShape shape = state.getCollisionShape(level, pos);
            return !shape.isEmpty();
        }

        // 门、活板门、栅栏、梯子等均被排除
        return false;
    }
}
