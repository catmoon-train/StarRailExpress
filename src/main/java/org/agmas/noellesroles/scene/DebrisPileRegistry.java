package org.agmas.noellesroles.scene;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import io.wifi.starrailexpress.game.GameUtils;
import org.agmas.noellesroles.content.block.scene.DebrisPileBlock;
import org.agmas.noellesroles.content.block_entity.scene.DebrisPileBlockEntity;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public final class DebrisPileRegistry {
    private static final Map<ResourceKey<Level>, Set<BlockPos>> PILES = new HashMap<>();

    private DebrisPileRegistry() {}

    public static void add(ServerLevel level, BlockPos pos) {
        PILES.computeIfAbsent(level.dimension(), k -> new HashSet<>()).add(pos.immutable());
    }

    public static void remove(ServerLevel level, BlockPos pos) {
        Set<BlockPos> set = PILES.get(level.dimension());
        if (set != null) set.remove(pos);
    }

    public static boolean allExtinguished(ServerLevel level) {
        if (GameUtils.taskBlocks != null && !GameUtils.taskBlocks.isEmpty()) {
            return allScannedExtinguished(level);
        }
        Set<BlockPos> set = PILES.get(level.dimension());
        if (set == null || set.isEmpty()) {
            return false;
        }
        boolean anyValid = false;
        Iterator<BlockPos> it = set.iterator();
        while (it.hasNext()) {
            BlockPos pos = it.next();
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof DebrisPileBlockEntity) {
                anyValid = true;
                var state = level.getBlockState(pos);
                if (!state.hasProperty(DebrisPileBlock.CLOSED)
                        || !state.getValue(DebrisPileBlock.CLOSED)) {
                    return false;
                }
            } else {
                it.remove();
            }
        }
        return anyValid;
    }

    private static boolean allScannedExtinguished(ServerLevel level) {
        boolean anyValid = false;
        for (BlockPos pos : GameUtils.taskBlocks.keySet()) {
            var state = level.getBlockState(pos);
            if (state.getBlock() instanceof DebrisPileBlock) {
                anyValid = true;
                if (!state.hasProperty(DebrisPileBlock.CLOSED)
                        || !state.getValue(DebrisPileBlock.CLOSED)) {
                    return false;
                }
            }
        }
        return anyValid;
    }
}
