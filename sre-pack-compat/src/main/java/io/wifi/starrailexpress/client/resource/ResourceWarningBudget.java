package io.wifi.starrailexpress.client.resource;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/** 单次加载器的逐资源警告预算；计数保留真实总数，不修改全局日志等级。 */
public final class ResourceWarningBudget<K> {
    private final int samples;
    private final Map<K, Long> counts = new LinkedHashMap<>();

    public ResourceWarningBudget(int samples) {
        if (samples < 0) {
            throw new IllegalArgumentException("samples must not be negative");
        }
        this.samples = samples;
    }

    public synchronized boolean allow(K resource) {
        long count = counts.merge(resource, 1L, Long::sum);
        return count <= samples;
    }

    /** 输出被省略的条数后清空；下次重载仍能看到首批实际错误。 */
    public synchronized void drain(BiConsumer<K, Long> summary) {
        try {
            counts.forEach((resource, count) -> {
                if (count > samples) {
                    summary.accept(resource, count - samples);
                }
            });
        } finally {
            counts.clear();
        }
    }
}
