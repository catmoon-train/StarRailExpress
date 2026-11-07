package org.agmas.noellesroles.game.fake_steve;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

/**
 * 有节点预算的 A*；世界适配器和回归测试共用同一搜索实现。
 *
 * <p>
 * 对应原版 {@code net.minecraft.world.level.pathfinder.PathFinder}，但把「世界怎么走」完全交给调用方：
 * 本类只负责开放集/关闭集、代价累计、裁剪和回溯，不关心节点类型。
 */
final class FakeStevePathSearch {
    private FakeStevePathSearch() {
    }

    /** 不限制累计路径距离。 */
    static <T> Result<T> find(T start, Predicate<T> reached, ToDoubleFunction<T> heuristic,
            Function<T, List<Edge<T>>> neighbours, int budget) {
        return find(start, reached, heuristic, neighbours, budget, 0.0D);
    }

    /**
     * 有节点预算的 A*。
     *
     * <p>
     * 对齐原版 {@code PathFinder#findPath} 的两个裁剪参数：
     * <ul>
     * <li><b>预算</b>（原版的最大访问节点数 {@code c = l × v}）：展开节点数达到上限就停，
     * 返回当前「离目标最近」的一段，由调用方决定是否重新寻路；</li>
     * <li><b>最大路径距离</b>（原版的最大距离 {@code l}）：累计路径距离超过它就整条剪掉，
     * 防止「绕地球一圈」的搜索把预算浪费在远离目标的分支上。</li>
     * </ul>
     *
     * <p>
     * 这里比较的是<b>几何路径距离</b> {@code p} 而不是代价累计 {@code g}：原版把两者分开，
     * {@code g = p + 节点代价} 只用于开放集排序，{@code p} 才用于这层裁剪。
     * 分开的好处是「危险邻居代价 8」不会把一条本来不长的路线误剪掉，
     * 而真正绕远的分支仍会被拦下。
     *
     * @param maxPathDistance 累计几何路径距离上限；&lt;= 0 表示不限制
     */
    static <T> Result<T> find(T start, Predicate<T> reached, ToDoubleFunction<T> heuristic,
            Function<T, List<Edge<T>>> neighbours, int budget, double maxPathDistance) {
        PriorityQueue<Entry<T>> open = new PriorityQueue<>(Comparator
                .<Entry<T>>comparingDouble(Entry::score).thenComparingDouble(Entry::remaining)
                .thenComparingLong(Entry::order));
        Map<T, T> parent = new HashMap<>();
        Map<T, Double> costs = new HashMap<>();
        Map<T, Double> lengths = new HashMap<>();
        Set<T> closed = new HashSet<>();
        long order = 0L;
        double bestRemaining = heuristic.applyAsDouble(start);
        T best = start;
        boolean complete = false;
        costs.put(start, 0.0D);
        lengths.put(start, 0.0D);
        open.add(new Entry<>(start, 0.0D, bestRemaining, order++));
        while (!open.isEmpty() && closed.size() < budget) {
            Entry<T> entry = open.remove();
            T current = entry.node();
            if (entry.cost() > costs.getOrDefault(current, Double.POSITIVE_INFINITY)
                    || !closed.add(current)) {
                continue;
            }
            if (entry.remaining() < bestRemaining) {
                best = current;
                bestRemaining = entry.remaining();
            }
            if (reached.test(current)) {
                best = current;
                complete = true;
                break;
            }
            for (Edge<T> edge : neighbours.apply(current)) {
                if (closed.contains(edge.to()) || !Double.isFinite(edge.cost()) || edge.cost() <= 0.0D) {
                    continue;
                }
                double candidateCost = entry.cost() + edge.cost();
                double candidateLength = lengths.getOrDefault(current, 0.0D) + edge.length();
                if (maxPathDistance > 0.0D && candidateLength > maxPathDistance) {
                    continue;
                }
                if (candidateCost >= costs.getOrDefault(edge.to(), Double.POSITIVE_INFINITY)) {
                    continue;
                }
                costs.put(edge.to(), candidateCost);
                lengths.put(edge.to(), candidateLength);
                parent.put(edge.to(), current);
                open.add(new Entry<>(edge.to(), candidateCost, heuristic.applyAsDouble(edge.to()), order++));
            }
        }
        ArrayDeque<T> route = new ArrayDeque<>();
        T cursor = best;
        while (!cursor.equals(start) && parent.containsKey(cursor)) {
            route.addFirst(cursor);
            cursor = parent.get(cursor);
        }
        return new Result<>(new ArrayList<>(route), complete, closed.size());
    }

    /**
     * 一条有向边。
     *
     * @param cost   节点代价：含跳跃、危险邻居、人群等惩罚，决定 A* 偏好哪条路
     * @param length 几何路径距离：只用于最大距离裁剪，与惩罚无关
     */
    record Edge<T>(T to, double cost, double length) {
        /** 只关心代价的调用方（网格模拟、测试）用这个重载：长度默认等于代价。 */
        Edge(T to, double cost) {
            this(to, cost, cost);
        }
    }

    /**
     * @param path     从起点之后开始的节点序列（不含起点）
     * @param complete 是否真的走到了 {@code reached} 的节点；false 表示预算/剪枝中断，path 是「离目标最近」的一段
     * @param visited  展开（关闭）的节点数，用于监控预算压力
     */
    record Result<T>(List<T> path, boolean complete, int visited) {
    }

    private record Entry<T>(T node, double cost, double remaining, long order) {
        double score() {
            return cost + remaining;
        }
    }
}
