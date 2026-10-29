package org.agmas.noellesroles.game.command_block;

import java.util.ArrayList;
import java.util.List;

/** 直线穿墙寻路，不限距离。纯逻辑便于无游戏环境测试。 */
public final class Error404Pathfinder {
    public static final int MAX_NODES = 65536;
    public record Cell(int x, int y, int z) {
        public Cell offset(int dx, int dy, int dz) { return new Cell(x + dx, y + dy, z + dz); }
    }
    public interface Grid {
        boolean standable(Cell cell);
        boolean clear(Cell cell);
    }
    private Error404Pathfinder() { }

    public static List<Cell> find(Grid grid, Cell start, Cell goal) {
        if (!grid.standable(goal)) return List.of();
        if (start.equals(goal)) return List.of(start);
        List<Cell> result = new ArrayList<>();
        Cell current = start;
        result.add(start);
        int guard = 0;
        while (!current.equals(goal) && guard++ < MAX_NODES) {
            int dx = Integer.compare(goal.x(), current.x());
            int dy = Integer.compare(goal.y(), current.y());
            int dz = Integer.compare(goal.z(), current.z());
            Cell next = current.offset(dx, dy, dz);
            if (!grid.standable(next)) return List.of();
            result.add(next);
            current = next;
        }
        return current.equals(goal) ? List.copyOf(result) : List.of();
    }
}
