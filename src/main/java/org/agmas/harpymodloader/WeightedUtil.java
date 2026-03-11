package org.agmas.harpymodloader;

import java.util.*;


//by canyuesama
public class WeightedUtil<T> {
    private final Map<T, Float> weights;
    private final List<T> keys;
    private  List<Float> cumulativeWeights;
    private float totalWeight;
    private final Random random;

    public WeightedUtil(Map<T, Float> weights) {
        if (weights == null) {
            throw new IllegalArgumentException("Weights map cannot be null");
        }

        this.weights = new LinkedHashMap<>(weights);
        this.keys = new ArrayList<>(this.weights.keySet());
        this.random = new Random();

        // 验证并移除非法权重
        validateAndCleanWeights();

        calculateCumulativeWeights();
    }

    public WeightedUtil(Map<T, Float> weights, Random random) {
        this(weights);
        if (random != null) {
            this.random.setSeed(random.nextLong());
        }
    }

    /**
     * 验证并清理权重值
     */
    private void validateAndCleanWeights() {
        Iterator<Map.Entry<T, Float>> iterator = weights.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<T, Float> entry = iterator.next();
            if (entry.getValue() == null || entry.getValue() <= 0) {
                iterator.remove();
                System.err.println("Warning: Removed invalid weight for key: " + entry.getKey());
            }
        }

        // 更新keys列表
        keys.clear();
        keys.addAll(weights.keySet());
    }

    /**
     * 计算累积权重
     */
    private void calculateCumulativeWeights() {
        totalWeight = 0f;
        cumulativeWeights = new ArrayList<>(keys.size());

        for (T key : keys) {
            Float weight = weights.get(key);
            if (weight != null && weight > 0) {
                totalWeight += weight;
                cumulativeWeights.add(totalWeight);
            }
        }
    }

    /**
     * 合并另一个权重工具
     */
    public void merge(WeightedUtil<T> other) {
        if (other == null) return;

        other.weights.forEach((key, value) ->
                this.weights.merge(key, value, Float::sum)
        );

        keys.clear();
        keys.addAll(weights.keySet());
        calculateCumulativeWeights();
    }

    /**
     * 移除指定键
     */
    public void removeKey(T key) {
        if (weights.remove(key) != null) {
            keys.remove(key);
            calculateCumulativeWeights();
        }
    }

    /**
     * 获取指定权重出现的概率
     */
    public float getProbability(T key) {
        Float weight = weights.get(key);
        if (weight == null || weight <= 0 || totalWeight <= 0) {
            return 0f;
        }
        return weight / totalWeight;
    }

    /**
     * 根据权重随机选择一个键
     */
    public T selectRandomKeyBasedOnWeights() {
        if (weights.isEmpty() || totalWeight <= 0) {
            return null;
        }

        float value = random.nextFloat() * totalWeight;

        // 使用二分查找提高性能
        int index = binarySearch(value);

        if (index >= 0 && index < keys.size()) {
            return keys.get(index);
        }

        // 备选方案：线性查找
        return linearSearchFallback(value);
    }

    /**
     * 二分查找累积权重
     */
    private int binarySearch(float value) {
        int low = 0;
        int high = cumulativeWeights.size() - 1;

        while (low <= high) {
            int mid = (low + high) >>> 1;
            float midValue = cumulativeWeights.get(mid);

            if (midValue < value) {
                low = mid + 1;
            } else if (midValue > value) {
                if (mid == 0 || cumulativeWeights.get(mid - 1) < value) {
                    return mid;
                }
                high = mid - 1;
            } else {
                return mid;
            }
        }

        return -1;
    }

    /**
     * 线性查找备选方案
     */
    private T linearSearchFallback(float value) {
        for (int i = 0; i < cumulativeWeights.size(); i++) {
            if (value <= cumulativeWeights.get(i)) {
                return keys.get(i);
            }
        }

        // 如果所有查找都失败，返回最后一个键
        return keys.isEmpty() ? null : keys.get(keys.size() - 1);
    }

    /**
     * 根据权重随机选择并移除键
     */
    public T selectRandomKeyBasedOnWeightsAndRemoved() {
        T selected = selectRandomKeyBasedOnWeights();
        if (selected != null) {
            removeKey(selected);
        }
        return selected;
    }

    /**
     * 提升所有权重值
     */
    public void increaseWeightsByRarity(int rarity) {
        if (rarity <= 0) return;

        for (Map.Entry<T, Float> entry : weights.entrySet()) {
            float currentWeight = entry.getValue();
            float increase = ((float) rarity / 2) / Math.max(currentWeight, 1);
            float newWeight = currentWeight + increase;

            // 确保新权重为正数
            if (newWeight <= 0) {
                newWeight = 1f;
            }

            entry.setValue(newWeight);
        }

        calculateCumulativeWeights();
    }

    /**
     * 按权重缩放所有值
     */
    public void scaleWeights(float multiplier) {
        if (multiplier <= 0) {
            throw new IllegalArgumentException("Multiplier must be positive");
        }

        weights.replaceAll((k, v) -> v * multiplier);
        calculateCumulativeWeights();
    }

    /**
     * 归一化权重
     */
    public void normalizeWeights() {
        if (totalWeight <= 0) return;

        float scale = 1.0f / totalWeight;
        weights.replaceAll((k, v) -> v * scale);
        calculateCumulativeWeights();
    }

    /**
     * 获取权重最高的键
     */
    public T getHighestWeightKey() {
        if (weights.isEmpty()) return null;

        return weights.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    /**
     * 获取权重最低的键
     */
    public T getLowestWeightKey() {
        if (weights.isEmpty()) return null;

        return weights.entrySet().stream()
                .min(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    /**
     * 获取权重分布
     */
    public Map<T, Float> getWeightDistribution() {
        if (totalWeight <= 0) return Collections.emptyMap();

        Map<T, Float> distribution = new LinkedHashMap<>();
        weights.forEach((key, weight) ->
                distribution.put(key, weight / totalWeight)
        );
        return distribution;
    }

    /**
     * 获取权重和
     */
    public float getTotalWeight() {
        return totalWeight;
    }

    /**
     * 获取所有权重键
     */
    public Set<T> getKeys() {
        return Collections.unmodifiableSet(weights.keySet());
    }

    /**
     * 获取指定键的权重
     */
    public Float getWeight(T key) {
        return weights.get(key);
    }

    /**
     * 设置指定键的权重
     */
    public void setWeight(T key, float weight) {
        if (weight <= 0) {
            removeKey(key);
            return;
        }

        if (!weights.containsKey(key)) {
            keys.add(key);
        }

        weights.put(key, weight);
        calculateCumulativeWeights();
    }

    public int size() {
        return weights.size();
    }

    public boolean isEmpty() {
        return weights.isEmpty();
    }

    /**
     * 清空所有权重
     */
    public void clear() {
        weights.clear();
        keys.clear();
        totalWeight = 0;
        cumulativeWeights.clear();
    }

    /**
     * 深度拷贝
     */
    public WeightedUtil<T> copy() {
        return new WeightedUtil<>(new LinkedHashMap<>(this.weights));
    }

    /**
     * 获取带权重的随机样本
     */
    public List<T> getWeightedSample(int count, boolean withReplacement) {
        if (count <= 0 || isEmpty()) {
            return Collections.emptyList();
        }

        List<T> samples = new ArrayList<>();
        WeightedUtil<T> source = withReplacement ? this : this.copy();

        for (int i = 0; i < count; i++) {
            T sample = source.selectRandomKeyBasedOnWeights();
            if (sample == null) break;

            samples.add(sample);
            if (!withReplacement) {
                source.removeKey(sample);
            }
        }

        return samples;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("WeightedUtil{\n");
        sb.append("  totalWeight: ").append(totalWeight).append("\n");
        sb.append("  weights: {\n");

        for (Map.Entry<T, Float> entry : weights.entrySet()) {
            float probability = getProbability(entry.getKey());
            sb.append(String.format("    %s: %.4f (%.2f%%)\n",
                    entry.getKey(), entry.getValue(), probability * 100));
        }

        sb.append("  }\n}");
        return sb.toString();
    }
}