/* SPDX-License-Identifier: GPL-3.0-or-later */
package io.wifi.starrailexpress.game;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.IntUnaryOperator;
import java.util.function.Predicate;

/** 不依赖世界的开局选点：随机列、列内顶部优先、先避开已预留玩家。 */
final class SpawnScatterSearch {
    static final int RADIUS = 6;
    // 相对旧传送落点（已包含 +1）的脚部高度；不搜索世界最高点，避免跨越地图楼层。
    static final int MAX_VERTICAL_OFFSET = 2;

    record Point(double x, double y, double z) {
    }

    private SpawnScatterSearch() {
    }

    static Point find(Point origin, IntUnaryOperator randomIndex,
            Function<Point, List<Double>> safeHeights, Predicate<Point> unoccupied) {
        List<Point> columns = new ArrayList<>();
        for (int x = (int) Math.floor(origin.x - RADIUS); x <= Math.floor(origin.x + RADIUS); x++) {
            for (int z = (int) Math.floor(origin.z - RADIUS); z <= Math.floor(origin.z + RADIUS); z++) {
                double dx = x + 0.5 - origin.x;
                double dz = z + 0.5 - origin.z;
                if (dx * dx + dz * dz <= RADIUS * RADIUS) {
                    columns.add(new Point(x + 0.5, origin.y, z + 0.5));
                }
            }
        }
        Point occupiedFallback = null;
        // 随机无放回遍历；有限范围穷尽后才允许安全站位重叠。
        for (int remaining = columns.size(); remaining > 0; remaining--) {
            int index = randomIndex.applyAsInt(remaining);
            Point column = columns.get(index);
            columns.set(index, columns.get(remaining - 1));
            double top = Double.NEGATIVE_INFINITY;
            for (double y : safeHeights.apply(column)) {
                if (Double.isFinite(y) && Math.abs(y - origin.y) <= MAX_VERTICAL_OFFSET && y > top) {
                    top = y;
                }
            }
            if (top == Double.NEGATIVE_INFINITY) continue;
            Point candidate = new Point(column.x, top, column.z);
            if (unoccupied.test(candidate)) return candidate;
            if (occupiedFallback == null) occupiedFallback = candidate;
        }
        return occupiedFallback;
    }
}
