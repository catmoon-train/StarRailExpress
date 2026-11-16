package io.wifi.sreperf.memory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 有界聚合分配采样；weight 仅作采样权重，不是 retained bytes 或精确分配量。 */
public final class AllocationSamples {
    private record Key(String type, List<String> stack, String nearestModPath) { }
    private static final class Total { long samples, weight; }
    private final int limit;
    private final Map<String, Total> classes = new LinkedHashMap<>();
    private final Map<Key, Total> stacks = new LinkedHashMap<>();
    private long samples, weight, droppedClassSamples, droppedStackSamples, truncatedStacks;

    public AllocationSamples(int limit) {
        if (limit < 1) throw new IllegalArgumentException("Positive capacity required");
        this.limit = limit;
    }

    public void add(String type, List<String> stack, String nearestModPath, long sampleWeight, boolean truncated) {
        if (sampleWeight < 0) throw new IllegalArgumentException("Negative sample weight");
        samples++;
        weight = Math.addExact(weight, sampleWeight);
        if (truncated) truncatedStacks++;
        Total byClass = classes.get(type);
        if (byClass == null && classes.size() < limit) { byClass = new Total(); classes.put(type, byClass); }
        if (byClass == null) droppedClassSamples++; else add(byClass, sampleWeight);
        Key key = new Key(type, List.copyOf(stack), nearestModPath);
        Total byStack = stacks.get(key);
        if (byStack == null && stacks.size() < limit) { byStack = new Total(); stacks.put(key, byStack); }
        if (byStack == null) droppedStackSamples++; else add(byStack, sampleWeight);
    }

    private static void add(Total total, long weight) { total.samples++; total.weight = Math.addExact(total.weight, weight); }

    public Map<String, Object> snapshot() {
        List<Map<String, Object>> byClass = new ArrayList<>(), byStack = new ArrayList<>();
        classes.forEach((type, total) -> byClass.add(Map.of("class", type, "samples", total.samples, "sampleWeightBytes", total.weight)));
        stacks.forEach((key, total) -> byStack.add(Map.of("class", key.type, "stack", key.stack,
                "nearestModPath", key.nearestModPath, "samples", total.samples, "sampleWeightBytes", total.weight)));
        Comparator<Map<String, Object>> order = Comparator.comparingLong((Map<String, Object> r) -> (Long) r.get("sampleWeightBytes")).reversed();
        byClass.sort(order);
        byStack.sort(order);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("samples", samples);
        out.put("sampleWeightBytes", weight);
        out.put("classes", byClass);
        out.put("stacks", byStack);
        out.put("capacityPerTable", limit);
        out.put("droppedClassSamples", droppedClassSamples);
        out.put("droppedStackSamples", droppedStackSamples);
        out.put("truncatedStacks", truncatedStacks);
        out.put("measurement", "jdk.ObjectAllocationSample: weighted allocation pressure, NOT retained/live heap or exact allocation totals. Class and stack tables overlap; do not add them. nearestModPath is only a stack resource association, not exclusive mod ownership. Empty samples do not mean no allocations.");
        return out;
    }
}
