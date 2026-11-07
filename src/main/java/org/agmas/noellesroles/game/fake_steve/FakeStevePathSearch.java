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
     * <li><b>最大路径距离</b>（原版的最大距离 {@code l}）：累计路径代价超过它就整条剪掉，
     * 防止「绕地球一圈」的搜索把预算浪费在远离目标的分支上。</li>
     * </ul>
     *
     * <p>
     * 注意这里比较的是<b>累计代价</b>而不是几何长度：原版同样用「路径距离」做这层裁剪，
     * 代价里已经含了跳跃/落地/危险邻居等惩罚，所以带惩罚的绕行会先被剪掉，符合直觉。
     *
     * @param maxPathDistance 累计路径代价上限；&lt;= 0 表示不限制
     */
    static <T> Result<T> find(T start, Predicate<T> reached, ToDoubleFunction<T> heuristic,
            Function<T, List<Edge<T>>> neighbours, int budget, double maxPathDistance) {
        PriorityQueue<Entry<T>> open = new PriorityQueue<>(Comparator
                .<Entry<T>>comparingDouble(Entry::score).thenComparingDouble(Entry::remaining)
                .thenComparingLong(Entry::order));
        Map<T, T> parent = new HashMap<>();
        Map<T, Double> costs = new HashMap<>();
        Set<T> closed = new HashSet<>();
        long order = 0L;
        double bestRemaining = heuristic.applyAsDouble(start);
        T best = start;
        boolean complete = false;
        costs.put(start, 0.0D);
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
                if (maxPathDistance > 0.0D && candidateCost > maxPathDistance) {
                    continue;
                }
                if (candidateCost >= costs.getOrDefault(edge.to(), Double.POSITIVE_INFINITY)) {
                    continue;
                }
                costs.put(edge.to(), candidateCost);
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

    /** 一条有向边：走到 {@code to} 的代价。 */
    record Edge<T>(T to, double cost) {
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
