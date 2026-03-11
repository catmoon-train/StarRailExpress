package io.wifi.starrailexpress.game;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

public class BlockCopyUtils {
    // ── 在 copyLayer 中加 Y 轴迭代（向下兼容单层）────────────────────────
    public static void copyLayer(ServerLevel level, BoundingBox area, BlockPos offset) {

        List<Map.Entry<BlockPos, CompoundTag>> pendingBlockEntities = new ArrayList<>();

        for (int y = area.minY(); y <= area.maxY(); y++) { // ← 新增 Y 循环
            for (int x = area.minX(); x <= area.maxX(); x++) {
                for (int z = area.minZ(); z <= area.maxZ(); z++) {
                    BlockPos srcPos = new BlockPos(x, y, z);
                    BlockEntity srcBE = level.getBlockEntity(srcPos);
                    if (srcBE != null) {
                        CompoundTag tag = srcBE.saveWithFullMetadata(level.registryAccess());
                        BlockPos dstPos = srcPos.offset(offset);
                        pendingBlockEntities.add(new AbstractMap.SimpleEntry<>(dstPos, tag));
                    }
                }
            }
        }

        for (int y = area.minY(); y <= area.maxY(); y++) {
            for (int x = area.minX(); x <= area.maxX(); x++) {
                for (int z = area.minZ(); z <= area.maxZ(); z++) {
                    BlockPos srcPos = new BlockPos(x, y, z);
                    BlockPos dstPos = srcPos.offset(offset);
                    BlockState state = level.getBlockState(srcPos);
                    level.setBlock(dstPos, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                    level.getLightEngine().checkBlock(dstPos);
                }
            }
        }

        for (Map.Entry<BlockPos, CompoundTag> entry : pendingBlockEntities) {
            BlockPos dstPos = entry.getKey();
            CompoundTag tag = entry.getValue().copy();
            tag.putInt("x", dstPos.getX());
            tag.putInt("y", dstPos.getY());
            tag.putInt("z", dstPos.getZ());
            BlockEntity dstBE = level.getBlockEntity(dstPos);
            if (dstBE != null) {
                dstBE.loadWithComponents(tag, level.registryAccess());
                dstBE.setChanged();
            }
        }
    }
}
