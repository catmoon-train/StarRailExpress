package org.agmas.noellesroles.scene;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import io.wifi.starrailexpress.game.GameUtils;
import org.agmas.noellesroles.content.block.scene.WaterValveBlock;
import org.agmas.noellesroles.content.block_entity.scene.WaterValveBlockEntity;

import java.util.*;

/**
 * 水阀位置登记表（按维度，瞬态）。用于判断"场上所有水阀是否都已关闭"。
 */
public final class WaterValveRegistry {
    private WaterValveRegistry() {
    }

    private static final Map<ResourceKey<Level>, Set<BlockPos>> VALVES = new HashMap<>();

    public static void add(ServerLevel level, BlockPos pos) {
        VALVES.computeIfAbsent(level.dimension(), k -> new HashSet<>()).add(pos.immutable());
    }

    public static void remove(ServerLevel level, BlockPos pos) {
        Set<BlockPos> set = VALVES.get(level.dimension());
        if (set != null) {
            set.remove(pos);
        }
    }

    public static int count(ServerLevel level) {
        Set<BlockPos> set = VALVES.get(level.dimension());
        return set == null ? 0 : set.size();
    }

    /** 场上是否存在水阀且全部已关闭。 */
    public static boolean allClosed(ServerLevel level) {
        if (GameUtils.taskBlocks != null && !GameUtils.taskBlocks.isEmpty()) {
            return allScannedClosed(level);
        }
        Set<BlockPos> set = VALVES.get(level.dimension());
        if (set == null || set.isEmpty()) {
            return false;
        }
        boolean anyValid = false;
        Iterator<BlockPos> it = set.iterator();
        while (it.hasNext()) {
            BlockPos pos = it.next();
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof WaterValveBlockEntity valve) {
                anyValid = true;
                if (!valve.isClosed()) {
                    return false;
                }
            } else {
                it.remove();
            }
        }
        return anyValid;
    }

    private static boolean allScannedClosed(ServerLevel level) {
        boolean anyValid = false;
        for (BlockPos pos : GameUtils.taskBlocks.keySet()) {
            var state = level.getBlockState(pos);
            if (state.getBlock() instanceof WaterValveBlock) {
                anyValid = true;
                if (!state.hasProperty(WaterValveBlock.CLOSED)
                        || !state.getValue(WaterValveBlock.CLOSED)) {
                    return false;
                }
            }
        }
        return anyValid;
    }
}
