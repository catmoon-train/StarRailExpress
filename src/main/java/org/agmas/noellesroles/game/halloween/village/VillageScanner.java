package org.agmas.noellesroles.game.halloween.village;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import io.wifi.starrailexpress.index.SREBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;

/** 有界、可暂停的地图扫描；只读 getChunkNow，绝不加载或添加区块票据。 */
public final class VillageScanner {
    private final AABB bounds;
    private final int minY, maxY, minCX, minCZ, width, depth;
    private final Map<Long, Integer> progress = new HashMap<>();
    private final ArrayDeque<ChunkPos> nearby = new ArrayDeque<>();
    private long cursor;
    private ChunkPos current;

    public VillageScanner(AABB bounds, ServerLevel level) {
        this.bounds = bounds;
        minY = Math.max(level.getMinBuildHeight(), Mth.floor(bounds.minY));
        maxY = Math.min(level.getMaxBuildHeight() - 1, Mth.ceil(bounds.maxY) - 1);
        minCX = Mth.floor(bounds.minX) >> 4;
        minCZ = Mth.floor(bounds.minZ) >> 4;
        width = Math.max(0, ((Mth.ceil(bounds.maxX) - 1) >> 4) - minCX + 1);
        depth = Math.max(0, ((Mth.ceil(bounds.maxZ) - 1) >> 4) - minCZ + 1);
    }

    public void tick(ServerLevel level, int requestedBudget, Consumer<BlockPos> found) {
        if (minY > maxY || width == 0 || depth == 0) return;
        int budget = Mth.clamp(requestedBudget, 1, 65536);
        if (level.getGameTime() % 20 == 0) {
            nearby.clear();
            // 活跃玩家周围优先发现，避免极大地图的冷区拖住实际活动区。
            for (ServerPlayer player : level.players()) {
                if (nearby.size() >= 128) break;
                ChunkPos p = player.chunkPosition();
                for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) nearby.add(new ChunkPos(p.x + x, p.z + z));
            }
        }
        int probes = 0;
        int volume = (maxY - minY + 1) * 256;
        while (budget > 0 && probes < 64) {
            if (current == null) {
                if (!nearby.isEmpty()) current = nearby.removeFirst();
                else {
                    long index = cursor++ % ((long) width * depth);
                    current = new ChunkPos(minCX + (int) (index % width), minCZ + (int) (index / width));
                }
                probes++;
            }
            long key = current.toLong();
            int index = progress.getOrDefault(key, 0);
            if (current.x < minCX || current.x >= (long) minCX + width || current.z < minCZ
                    || current.z >= (long) minCZ + depth || index >= volume) { current = null; continue; }
            LevelChunk chunk = level.getChunkSource().getChunkNow(current.x, current.z);
            if (chunk == null) { current = null; continue; }
            while (budget > 0 && index < volume) {
                int x = current.getMinBlockX() + (index & 15);
                int z = current.getMinBlockZ() + ((index >> 4) & 15);
                int y = minY + (index >> 8);
                index++; budget--;
                if (x < bounds.minX || x >= bounds.maxX || z < bounds.minZ || z >= bounds.maxZ) continue;
                BlockPos pos = new BlockPos(x, y, z);
                var state = chunk.getBlockState(pos);
                if (state.is(Blocks.JACK_O_LANTERN) || state.is(SREBlocks.JACK_O_LANTERN)) found.accept(pos);
            }
            progress.put(key, index);
            if (index >= volume) current = null;
        }
    }
}
