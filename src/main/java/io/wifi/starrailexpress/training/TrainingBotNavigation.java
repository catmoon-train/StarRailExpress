package io.wifi.starrailexpress.training;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/** 固定地图的小型可见性图与局部避障策略；纯逻辑测试无需初始化 Minecraft。 */
public final class TrainingBotNavigation {
    static final int MAX_NODES = 64;
    static final int MAX_LINK_TESTS = 128;
    static final double MAX_LINK_LENGTH = 24;

    private TrainingBotNavigation() { }

    public record Point(double x, double y, double z) {
        public double distanceSquared(Point other) {
            double dx = x - other.x, dy = y - other.y, dz = z - other.z;
            return dx * dx + dy * dy + dz * dz;
        }
        public Point add(double dx, double dy, double dz) { return new Point(x + dx, y + dy, z + dz); }
    }

    @FunctionalInterface
    public interface Passage { boolean clear(Point from, Point to); }

    /** 最多 64 个地图节点、128 次边检查；失败返回空路而不是穿墙的终点。 */
    public static List<Point> route(Point from, Point goal, List<Point> mapNodes, Passage passage) {
        if (from.distanceSquared(goal) < 0.16) return List.of();
        int[] checks = {0};
        Passage bounded = (a, b) -> a.distanceSquared(b) <= MAX_LINK_LENGTH * MAX_LINK_LENGTH
                && checks[0]++ < MAX_LINK_TESTS && passage.clear(a, b);
        if (bounded.clear(from, goal)) return List.of(goal);
        List<Point> points = new ArrayList<>();
        points.add(from);
        mapNodes.stream().limit(MAX_NODES).forEach(points::add);
        points.add(goal);
        int n = points.size(), end = n - 1;
        double[] cost = new double[n];
        Arrays.fill(cost, Double.POSITIVE_INFINITY);
        cost[0] = 0;
        int[] previous = new int[n];
        Arrays.fill(previous, -1);
        boolean[] visited = new boolean[n];
        for (int expanded = 0; expanded < n && checks[0] < MAX_LINK_TESTS; expanded++) {
            int best = -1;
            double bestScore = Double.POSITIVE_INFINITY;
            for (int i = 0; i < n; i++) {
                double score = cost[i] + Math.sqrt(points.get(i).distanceSquared(goal));
                if (!visited[i] && score < bestScore) { best = i; bestScore = score; }
            }
            if (best < 0) break;
            if (best == end) {
                List<Point> path = new ArrayList<>();
                for (int i = end; i != 0; i = previous[i]) path.add(points.get(i));
                return path.reversed();
            }
            visited[best] = true;
            Point current = points.get(best);
            List<Integer> neighbors = new ArrayList<>();
            for (int i = 1; i < n; i++) if (!visited[i] && i != best) neighbors.add(i);
            neighbors.sort(Comparator.comparingDouble(i -> current.distanceSquared(points.get(i))));
            // 近邻保证图足够稀疏；终点额外检查，避免近旁装饰节点挡住出口。
            List<Integer> candidates = new ArrayList<>(neighbors.stream().limit(10).toList());
            if (!visited[end] && !candidates.contains(end)) candidates.add(end);
            for (int next : candidates) {
                double nextCost = cost[best] + Math.sqrt(current.distanceSquared(points.get(next)));
                if (nextCost < cost[next] && bounded.clear(current, points.get(next))) {
                    cost[next] = nextCost;
                    previous[next] = best;
                }
            }
        }
        return List.of();
    }

    /** 实际移动前再次检查半径；两侧绕行失败时原地等待，不传送脱困。 */
    public static Point localStep(Point from, Point goal, Passage passage, int preferredSide) {
        double dx = goal.x - from.x, dz = goal.z - from.z;
        double length = Math.hypot(dx, dz);
        if (length < 0.1) return from;
        dx /= length;
        dz /= length;
        double step = Math.min(0.9, length);
        for (int angle : new int[]{0, 45, -45, 90, -90, 135, -135}) {
            double radians = Math.toRadians(angle * (preferredSide < 0 ? -1 : 1));
            double x = (dx * Math.cos(radians) - dz * Math.sin(radians)) * step;
            double z = (dx * Math.sin(radians) + dz * Math.cos(radians)) * step;
            Point candidate = from.add(x, Math.clamp(goal.y - from.y, -0.5, 0.5), z);
            if (passage.clear(from, candidate)) return candidate;
        }
        return from;
    }

    /** Carpet 的前进/横移是相对视角输入，瞄准目标时仍沿世界空间路点移动。 */
    public static float[] inputs(Point from, Point to, float yaw, float speed) {
        double dx = to.x - from.x, dz = to.z - from.z, length = Math.hypot(dx, dz);
        if (length < 0.05) return new float[]{0, 0};
        double radians = Math.toRadians(yaw);
        return new float[]{(float) ((-Math.sin(radians) * dx + Math.cos(radians) * dz) / length * speed),
                (float) ((Math.cos(radians) * dx + Math.sin(radians) * dz) / length * speed)};
    }

    static int combatMotion(boolean gun, double distanceSquared, boolean sight) {
        if (!gun) return distanceSquared > 2.2 * 2.2 || !sight ? 1 : 0;
        if (!sight || distanceSquared > 16 * 16) return 1;
        return distanceSquared < 6 * 6 ? -1 : 0;
    }

    static boolean mayAttack(boolean gun, double distanceSquared, boolean sight, boolean tracedOwner,
                             long visibleTicks, int reactionTicks) {
        return sight && tracedOwner && visibleTicks >= reactionTicks && distanceSquared <= (gun ? 20 * 20 : 3 * 3);
    }
}
