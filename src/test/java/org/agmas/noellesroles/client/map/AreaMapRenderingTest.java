package org.agmas.noellesroles.client.map;

import org.junit.jupiter.api.Test;

import java.util.BitSet;

import static org.junit.jupiter.api.Assertions.*;

/** 颜色、透明层、扫描顺序与上传边界的无游戏环境回归测试。 */
class AreaMapRenderingTest {
    @Test void biomeTintUsesRgbWithoutSwappingNativeAbgrChannels() {
        assertEquals(0xFF4080FF, AreaMapColor.tint(0xFFFFFFFF, 0xFF8040));
        assertEquals(0x804080FF, AreaMapColor.tint(0x804080FF, -1));
        assertEquals(0x80000000, AreaMapColor.tint(0x804080FF, 0));
    }

    @Test void transparentPixelsDoNotPolluteTextureAverage() {
        var average = new AreaMapColor.Average();
        average.add(0xFF0000FF);
        average.add(0x00FFFFFF);
        assertEquals(0x7F0000FF, average.color());
        var transparent = new AreaMapColor.Average();
        transparent.add(0x00FFFFFF);
        assertEquals(0, transparent.color());
        assertEquals(0, new AreaMapColor.Average().color());
    }

    @Test void averageIsAlphaWeightedAndDoesNotOverflowOnHdSamples() {
        var average = new AreaMapColor.Average();
        for (int i = 0; i < 256; i++) average.add(0xFFFFFFFF);
        assertEquals(0xFFFFFFFF, average.color());
        var weighted = new AreaMapColor.Average();
        weighted.add(0xFF0000FF);
        weighted.add(0x5500FF00);
        assertEquals(0xAA003FBF, weighted.color());
    }

    @Test void translucentWaterAndGlassPreserveUnderlyingMaterial() {
        assertEquals(0xFF7F0080, AreaMapColor.over(0x800000FF, 0xFFFF0000));
        assertEquals(0xFF123456, AreaMapColor.over(0, 0xFF123456));
        assertEquals(0xFF123456, AreaMapColor.over(0xFF123456, 0xFFFFFFFF));
        assertEquals(0, AreaMapColor.over(0, 0));
        assertEquals(0x80123456, AreaMapColor.over(0x80123456, 0));
    }

    @Test void reliefKeepsAlphaAndClampsRatherThanWrappingBrightChannels() {
        assertEquals(0x80808080, AreaMapColor.shade(0x80FFFFFF, 0.5F));
        assertEquals(0xFFFFFFFF, AreaMapColor.shade(0xFFFFFFFF, 1.12F));
        assertEquals(0xFF000000, AreaMapColor.shade(0xFFFFFFFF, -1));
        assertEquals(0, AreaMapColor.shade(0, 1));
    }

    @Test void scansNearPlayerFirstAndEachCellExactlyOnceIncludingEdgeTiles() {
        var queue = new AreaMapTileQueue(35, 33);
        queue.reset(34, 32);
        int first = queue.next();
        assertEquals(32, queue.x0(first));
        assertEquals(32, queue.z0(first));
        BitSet cells = new BitSet();
        int count = 0;
        for (int tile = first; tile >= 0; tile = queue.next()) {
            for (int z = queue.z0(tile); z < queue.z1(tile); z++) {
                for (int x = queue.x0(tile); x < queue.x1(tile); x++) {
                    int index = z * 35 + x;
                    assertFalse(cells.get(index));
                    cells.set(index);
                    count++;
                }
            }
        }
        assertEquals(35 * 33, count);
        assertEquals(-1, queue.next(), "扫描完毕后不循环空扫");
    }

    @Test void dirtyRegionsAreClippedDeduplicatedAndCanWakeFinishedQueue() {
        var queue = new AreaMapTileQueue(35, 33);
        queue.reset(0, 0);
        while (queue.next() >= 0) { }
        queue.markRegion(-20, -20, 2, 2);
        queue.markRegion(0, 0, 2, 2);
        int tile = queue.next();
        assertEquals(0, queue.x0(tile));
        assertEquals(0, queue.z0(tile));
        assertEquals(-1, queue.next());
        queue.markRegion(34, 32, 100, 100);
        tile = queue.next();
        assertEquals(35, queue.x1(tile));
        assertEquals(33, queue.z1(tile));
        queue.markRegion(-50, -50, -1, -1);
        queue.markRegion(50, 50, 100, 100);
        queue.markRegion(3, 3, 3, 3);
        assertEquals(-1, queue.next());
    }

    @Test void movingFocusReordersRemainingWorkWithoutRescanningCompletedTiles() {
        var queue = new AreaMapTileQueue(64, 64);
        queue.reset(0, 0);
        int completed = queue.next();
        queue.prioritize(63, 63);
        int nearest = queue.next();
        assertEquals(48, queue.x0(nearest));
        assertEquals(48, queue.z0(nearest));
        for (int id = queue.next(); id >= 0; id = queue.next()) assertNotEquals(completed, id);
    }

    @Test void blockChangeOnTileBoundaryInvalidatesBothSides() {
        var queue = new AreaMapTileQueue(32, 32);
        queue.markRegion(14, 14, 17, 17);
        int count = 0;
        while (queue.next() >= 0) count++;
        assertEquals(4, count);
    }

    @Test void partialUploadUsesExclusiveMaximumAndCanBeCleared() {
        var rect = new AreaMapDirtyRect();
        assertTrue(rect.isEmpty());
        rect.include(12, 7);
        assertEquals(12, rect.minX);
        assertEquals(7, rect.minZ);
        assertEquals(13, rect.maxX);
        assertEquals(8, rect.maxZ);
        rect.include(3, 20);
        assertEquals(3, rect.minX);
        assertEquals(7, rect.minZ);
        assertEquals(13, rect.maxX);
        assertEquals(21, rect.maxZ);
        rect.clear();
        assertTrue(rect.isEmpty());
    }
}
