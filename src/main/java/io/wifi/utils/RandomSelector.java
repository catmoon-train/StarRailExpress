/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.wifi.utils;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 按给定概率分布随机返回不同结果的选择器。
 * 支持泛型，自动归一化概率，线程安全（默认使用 ThreadLocalRandom）。
 *
 * @param <T> 结果类型
 */
public class RandomSelector<T> {

    private final List<T> items; // 结果列表（顺序与概率一一对应）
    private final double[] cumulativeProb; // 累积概率数组
    private final Random random; // 随机源

    // ---------- 构造器 ----------

    /**
     * 构造选择器（使用 ThreadLocalRandom，适合多线程环境）。
     *
     * @param items         结果列表
     * @param probabilities 对应的概率数组（可为任意非负数，内部自动归一化）
     */
    public RandomSelector(List<T> items, double... probabilities) {
        this(items, ThreadLocalRandom.current(), probabilities);
    }

    /**
     * 构造选择器（可指定随机源，便于测试或需要 SecureRandom 的场景）。
     *
     * @param items         结果列表
     * @param random        随机源（如 new Random(123) 或 new SecureRandom()）
     * @param probabilities 对应的概率数组
     */
    public RandomSelector(List<T> items, Random random, double... probabilities) {
        // 参数校验
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Result lists cannot be empty.");
        }
        if (probabilities == null || probabilities.length == 0) {
            throw new IllegalArgumentException("Probabilities cannot be empty.");
        }
        if (items.size() != probabilities.length) {
            throw new IllegalArgumentException("The number of items must equals the number of probabilties.");
        }
        if (random == null) {
            throw new IllegalArgumentException("Random source cannot be empty.");
        }

        // 校验并计算总和
        double sum = 0;
        for (double p : probabilities) {
            if (p < 0) {
                throw new IllegalArgumentException("The probability must not be less than 0.");
            }
            sum += p;
        }
        if (sum == 0) {
            throw new IllegalArgumentException("Total of probabilities cannot be 0");
        }

        // 赋值
        this.items = new ArrayList<>(items);
        this.random = random;
        this.cumulativeProb = new double[probabilities.length];

        // 归一化并构建累积概率
        double cumulative = 0;
        for (int i = 0; i < probabilities.length; i++) {
            cumulative += probabilities[i] / sum;
            cumulativeProb[i] = cumulative;
        }
        // 浮点误差修正，保证最后一个值为 1.0
        cumulativeProb[cumulativeProb.length - 1] = 1.0;
    }

    // ---------- 核心方法 ----------

    /**
     * 根据概率分布随机返回一个结果。
     */
    public T next() {
        double rand = random.nextDouble();
        int index = Arrays.binarySearch(cumulativeProb, rand);
        if (index < 0) {
            index = -index - 1;
        }
        // 边界保护（极少发生，但防止浮点误差越界）
        if (index >= items.size()) {
            index = items.size() - 1;
        }
        return items.get(index);
    }

    // ---------- 静态工具方法 ----------

    /**
     * 简易布尔判定：以 numerator/denominator 的概率返回 true。
     * 内部会自动约分（例如 1000/10000 会自动化简为 1/10），再生成随机数。
     *
     * @param numerator   分子（命中次数），必须 ≥ 0
     * @param denominator 分母（总次数），必须 > 0
     * @return true 表示命中
     */
    public static boolean tryChance(int numerator, int denominator) {
        if (denominator <= 0) {
            throw new IllegalArgumentException("The denominator must bigger than 0.");
        }
        if (numerator <= 0) {
            return false;
        }
        if (numerator >= denominator) {
            return true;
        }

        // 1. 自动约分（化简为最简整数比）
        int gcd = gcd(numerator, denominator);
        numerator /= gcd;
        denominator /= gcd;

        // 2. 生成 [0, denominator) 随机整数，小于 numerator 即命中
        return ThreadLocalRandom.current().nextInt(denominator) < numerator;
    }

    /**
     * 辗转相除法求最大公约数（支持正数）
     */
    private static int gcd(int a, int b) {
        while (b != 0) {
            int temp = a % b;
            a = b;
            b = temp;
        }
        return a;
    }

    /**
     * 快捷静态方法：根据概率列表随机选择一个元素。
     * 每次调用会临时构造选择器，高频场景建议复用实例。
     *
     * @param items         结果列表
     * @param probabilities 对应的概率数组
     * @param <T>           元素类型
     * @return 随机选中的元素
     */
    public static <T> T randomChoice(List<T> items, double... probabilities) {
        return new RandomSelector<>(items, probabilities).next();
    }

    /**
     * 快捷静态方法：根据权重 Map 随机选择一个元素（权重自动归一化）。
     * Map 的键为结果，值为权重（非负数）。
     *
     * @param weightMap 权重映射
     * @param <T>       元素类型
     * @return 随机选中的元素
     */
    public static <T> T randomChoice(Map<T, Double> weightMap) {
        if (weightMap == null || weightMap.isEmpty()) {
            throw new IllegalArgumentException("Weight map cannot be empty");
        }
        // 提取键和权重（保持迭代顺序一致）
        List<T> items = new ArrayList<>(weightMap.keySet());
        double[] probs = new double[items.size()];
        for (int i = 0; i < items.size(); i++) {
            Double w = weightMap.get(items.get(i));
            if (w == null || w < 0) {
                throw new IllegalArgumentException("Weight cannot be negative numbers or null");
            }
            probs[i] = w;
        }
        return new RandomSelector<>(items, probs).next();
    }
}