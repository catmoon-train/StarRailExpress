package org.agmas.noellesroles.client.map;

import java.util.Arrays;
import java.util.BitSet;
import java.util.Comparator;

/** 去重的地图瓦片队列；只在玩家跨瓦片时重排，优先填充附近的地图。 */
final class AreaMapTileQueue {
    static final int TILE_SIZE = 16;
    private final int width, height, tilesX, tilesZ;
    private final BitSet pending;
    private final Integer[] order;

    AreaMapTileQueue(int width, int height) {
        this.width = width;
        this.height = height;
        tilesX = (width + TILE_SIZE - 1) / TILE_SIZE;
        tilesZ = (height + TILE_SIZE - 1) / TILE_SIZE;
        pending = new BitSet(tilesX * tilesZ);
        order = new Integer[tilesX * tilesZ];
        Arrays.setAll(order, i -> i);
    }

    void reset(int focusX, int focusZ) {
        pending.set(0, order.length);
        prioritize(focusX, focusZ);
    }

    void prioritize(int focusX, int focusZ) {
        Arrays.sort(order, Comparator.comparingLong(id -> {
            long dx = x0(id) + (x1(id) - x0(id)) / 2 - (long) focusX;
            long dz = z0(id) + (z1(id) - z0(id)) / 2 - (long) focusZ;
            return dx * dx + dz * dz;
        }));
    }

    void markRegion(int x0, int z0, int x1, int z1) {
        if (x1 <= 0 || z1 <= 0 || x0 >= width || z0 >= height || x0 >= x1 || z0 >= z1) return;
        int minX = Math.max(0, x0) / TILE_SIZE, minZ = Math.max(0, z0) / TILE_SIZE;
        int maxX = (Math.min(width, x1) - 1) / TILE_SIZE;
        int maxZ = (Math.min(height, z1) - 1) / TILE_SIZE;
        for (int z = minZ; z <= maxZ; z++) pending.set(z * tilesX + minX, z * tilesX + maxX + 1);
    }

    int next() {
        for (int id : order) {
            if (pending.get(id)) {
                pending.clear(id);
                return id;
            }
        }
        return -1;
    }

    int x0(int id) { return id % tilesX * TILE_SIZE; }
    int z0(int id) { return id / tilesX * TILE_SIZE; }
    int x1(int id) { return Math.min(width, x0(id) + TILE_SIZE); }
    int z1(int id) { return Math.min(height, z0(id) + TILE_SIZE); }
}
