package org.agmas.noellesroles.game.fake_steve;

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

/** 有节点预算的 A*；世界适配器和回归测试共用同一搜索实现。 */
final class FakeStevePathSearch {
    private FakeStevePathSearch() {
    }

    static <T> Result<T> find(T start, Predicate<T> reached, ToDoubleFunction<T> heuristic,
            Function<T, List<Edge<T>>> neighbours, int budget) {
        PriorityQueue<Entry<T>> open = new PriorityQueue<>(Comparator
                .<Entry<T>>comparingDouble(Entry::score).thenComparingDouble(Entry::remaining)
                .thenComparingLong(Entry::order));
        Map<T, T> parent = new HashMap<>();
        Map<T, Double> costs = new HashMap<>();
        Set<T> closed = new HashSet<>();
        long order = 0;
        double bestRemaining = heuristic.applyAsDouble(start);
        T best = start;
        boolean complete = false;
        costs.put(start, 0.0D);
        open.add(new Entry<>(start, 0, bestRemaining, order++));
        while (!open.isEmpty() && closed.size() < budget) {
            Entry<T> entry = open.remove();
            T current = entry.node();
            if (entry.cost() > costs.getOrDefault(current, Double.POSITIVE_INFINITY)
                    || !closed.add(current)) continue;
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
                if (closed.contains(edge.to()) || !Double.isFinite(edge.cost()) || edge.cost() <= 0) continue;
                double candidateCost = entry.cost() + edge.cost();
                if (candidateCost >= costs.getOrDefault(edge.to(), Double.POSITIVE_INFINITY)) continue;
                costs.put(edge.to(), candidateCost);
                parent.put(edge.to(), current);
                open.add(new Entry<>(edge.to(), candidateCost,
                        heuristic.applyAsDouble(edge.to()), order++));
            }
        }
        java.util.ArrayDeque<T> route = new java.util.ArrayDeque<>();
        T cursor = best;
        while (!cursor.equals(start) && parent.containsKey(cursor)) {
            route.addFirst(cursor);
            cursor = parent.get(cursor);
        }
        return new Result<>(new ArrayList<>(route), complete, closed.size());
    }

    record Edge<T>(T to, double cost) {
    }

    record Result<T>(List<T> path, boolean complete, int visited) {
    }

    private record Entry<T>(T node, double cost, double remaining, long order) {
        double score() { return cost + remaining; }
    }
}
