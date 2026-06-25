package org.agmas.noellesroles.scene;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.HashSet;
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
}
