package io.wifi.utils.ai;

import java.util.*;

/**
 * 极简 KNN (K-Nearest Neighbors) 分类器
 * 适用于低像素手绘图识别（数字矩阵输入）
 * 支持最低匹配度阈值，当最高类别得票比例不足时返回 -1（未知）
 */
public class SimpleKNN {

    // 存储训练样本
    private static class Sample {
        double[] features;  // 特征向量（展平后的矩阵）
        byte[][] rawMatrix; // 原始矩阵，用于像素级校验
        int label;          // 类别标签，如 0=圆形, 1=方形
    }

    private final List<Sample> samples = new ArrayList<>();
    private final int k;  // KNN 的 K 值，通常取奇数，如 3, 5

    /**
     * 构造 KNN 分类器
     * @param k 邻居个数，推荐 3 或 5
     */
    public SimpleKNN(int k) {
        if (k <= 0) throw new IllegalArgumentException("k must be positive");
        this.k = k;
    }

    /**
     * 添加一个训练样本
     * @param features 特征向量（double数组）
     * @param label    类别标签
     */
    public void addSample(double[] features, int label) {
        addSample(features, null, label);
    }

    /**
     * 添加一个训练样本（带原始矩阵）
     * @param features 特征向量（double数组）
     * @param rawMatrix 原始矩阵，用于像素级校验
     * @param label    类别标签
     */
    public void addSample(double[] features, byte[][] rawMatrix, int label) {
        Sample s = new Sample();
        s.features = features.clone();
        s.rawMatrix = rawMatrix;
        s.label = label;
        samples.add(s);
    }

    // 辅助类：存储邻居的距离和标签
    private static class Neighbor {
        double distance;
        int label;
        Neighbor(double distance, int label) {
            this.distance = distance;
            this.label = label;
        }
    }

    // ========== 算法1: 标准欧氏距离 ==========
    private double euclideanDistance(double[] a, double[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException("Feature vectors must have same length");
        }
        double sum = 0;
        for (int i = 0; i < a.length; i++) {
            double diff = a[i] - b[i];
            sum += diff * diff;
        }
        return Math.sqrt(sum);
    }

    // ========== 算法2: 宽松形状匹配 ==========
    // 允许多余像素，主要关注形状轮廓和颜色
    // 惩罚权重: 多余像素=0.05, 遗漏像素=0.3, 颜色差异=1.0
    private double shapeAwareDistance(double[] a, double[] b) {
        return shapeAwareDistance(a, b, 0.05, 0.3, 1.0);
    }

    /**
     * 宽松形状匹配（可调参数版）
     * @param extraPenalty 多余像素惩罚 (建议 0.01~0.1)
     * @param missingPenalty 遗漏像素惩罚 (建议 0.2~0.5)
     * @param colorPenalty 颜色差异惩罚 (建议 1.0)
     */
    public double shapeAwareDistance(double[] a, double[] b, double extraPenalty, double missingPenalty, double colorPenalty) {
        if (a.length != b.length) {
            throw new IllegalArgumentException("Feature vectors must have same length");
        }

        double sum = 0;
        int validCount = 0;

        for (int i = 0; i < a.length; i++) {
            double va = a[i];
            double vb = b[i];

            // 透明/背景像素（≈0）不参与距离计算
            boolean aIsTransparent = Math.abs(va) < 0.01;
            boolean bIsTransparent = Math.abs(vb) < 0.01;

            if (aIsTransparent && bIsTransparent) {
                continue;
            }

            if (aIsTransparent) {
                // 样本无此像素，输入多了像素（多余笔触）- 极小惩罚
                sum += extraPenalty * vb * vb;
            } else if (bIsTransparent) {
                // 输入无此像素，样本有（遗漏笔触）- 中等惩罚
                sum += missingPenalty * va * va;
            } else {
                // 两者都有像素，计算颜色差异
                double diff = va - vb;
                sum += colorPenalty * diff * diff;
                validCount++;
            }
        }

        if (validCount > 0) {
            return Math.sqrt(sum / validCount);
        }
        return Math.sqrt(sum);
    }

    // ========== 算法3: 纯形状匹配（忽略颜色） ==========
    // 只关注有像素的位置，不比较颜色值
    private double pureShapeDistance(double[] a, double[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException("Feature vectors must have same length");
        }

        double sum = 0;
        int validCount = 0;

        for (int i = 0; i < a.length; i++) {
            double va = a[i];
            double vb = b[i];

            boolean aHasColor = Math.abs(va) > 0.01;
            boolean bHasColor = Math.abs(vb) > 0.01;

            if (aHasColor && bHasColor) {
                // 两者都有颜色，贡献0
                validCount++;
            } else if (aHasColor) {
                // a有颜色，b没有
                sum += 1.0;
            } else if (bHasColor) {
                // b有颜色，a没有
                sum += 1.0;
            }
        }

        // 返回形状差异比例
        if (validCount > 0) {
            return sum / (validCount + sum);
        }
        return sum;
    }

    // ========== 算法4: 颜色直方图匹配 ==========
    // 统计每种颜色的像素数量，比较颜色分布
    private double colorHistogramDistance(double[] a, double[] b, int colorLevels) {
        if (a.length != b.length) {
            throw new IllegalArgumentException("Feature vectors must have same length");
        }

        // 统计颜色直方图
        int[] histA = new int[colorLevels];
        int[] histB = new int[colorLevels];

        for (int i = 0; i < a.length; i++) {
            // 透明像素不统计
            if (Math.abs(a[i]) > 0.01) {
                int bin = Math.min((int) (a[i] * colorLevels), colorLevels - 1);
                histA[bin]++;
            }
            if (Math.abs(b[i]) > 0.01) {
                int bin = Math.min((int) (b[i] * colorLevels), colorLevels - 1);
                histB[bin]++;
            }
        }

        // 计算直方图差异（使用卡方距离）
        double sum = 0;
        for (int i = 0; i < colorLevels; i++) {
            if (histA[i] + histB[i] > 0) {
                sum += (histA[i] - histB[i]) * (histA[i] - histB[i]) / (double) (histA[i] + histB[i]);
            }
        }
        return sum;
    }

    // ========== 算法6: 色块数量匹配 ==========
    // 统计每种颜色的像素数量，数量较多的颜色占更高权重
    // 数量较少的颜色（少于3个像素）如果少画了可以忽略不计
    private double colorCountDistance(double[] a, double[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException("Feature vectors must have same length");
        }

        // 将归一化的颜色值转换为颜色索引
        Map<Integer, Integer> countA = new HashMap<>();
        Map<Integer, Integer> countB = new HashMap<>();

        for (int i = 0; i < a.length; i++) {
            // 透明像素（背景白色）不统计
            if (Math.abs(a[i]) > 0.01) {
                int colorIndex = Math.min((int) (a[i] * 16), 15);
                countA.put(colorIndex, countA.getOrDefault(colorIndex, 0) + 1);
            }
            if (Math.abs(b[i]) > 0.01) {
                int colorIndex = Math.min((int) (b[i] * 16), 15);
                countB.put(colorIndex, countB.getOrDefault(colorIndex, 0) + 1);
            }
        }

        // 找出样本中数量最多的颜色（主色）
        int maxCountB = countB.values().stream().mapToInt(Integer::intValue).max().orElse(0);

        // 计算加权差异分数
        double totalDiff = 0;
        double totalWeight = 0;
        Set<Integer> allColors = new HashSet<>();
        allColors.addAll(countA.keySet());
        allColors.addAll(countB.keySet());

        for (int color : allColors) {
            int cntA = countA.getOrDefault(color, 0);
            int cntB = countB.getOrDefault(color, 0);

            // 跳过样本中数量很少的颜色（小于3个像素），不参与计算
            if (cntB < 3) {
                continue;
            }

            // 计算权重：颜色数量越多，权重越高
            double weight = cntB / (double) maxCountB;
            totalWeight += weight;

            // 计算差异
            int diff = Math.abs(cntA - cntB);
            double colorDiff = diff / (double) Math.max(cntA, cntB);

            // 如果数量较多的颜色少画了很多，给予更高惩罚
            // 差异超过30%时开始累积惩罚
            if (cntA < cntB && colorDiff > 0.3) {
                // 额外惩罚：少画的越多，惩罚越高
                double extraPenalty = (colorDiff - 0.3) * 2;
                colorDiff += extraPenalty;
            }

            totalDiff += colorDiff * weight;
        }

        // 返回加权平均差异
        return totalWeight > 0 ? totalDiff / totalWeight : 1.0;
    }

    // ========== 算法5: 重心偏移容忍匹配 ==========
    // 计算形状的加权重心，允许位置有一定偏移
    private double centroidAwareDistance(double[] a, double[] b, int width, int height) {
        if (a.length != b.length || a.length != width * height) {
            throw new IllegalArgumentException("Invalid dimensions");
        }

        // 计算重心
        double cxA = 0, cyA = 0, weightA = 0;
        double cxB = 0, cyB = 0, weightB = 0;

        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                int idx = i * width + j;
                if (Math.abs(a[idx]) > 0.01) {
                    cxA += j;
                    cyA += i;
                    weightA++;
                }
                if (Math.abs(b[idx]) > 0.01) {
                    cxB += j;
                    cyB += i;
                    weightB++;
                }
            }
        }

        if (weightA > 0) { cxA /= weightA; cyA /= weightA; }
        if (weightB > 0) { cxB /= weightB; cyB /= weightB; }

        // 重心偏移惩罚
        double dx = cxA - cxB;
        double dy = cyA - cyB;
        double centroidPenalty = Math.sqrt(dx * dx + dy * dy) * 0.1;

        // 像素数量差异惩罚
        double countDiff = Math.abs(weightA - weightB) / Math.max(weightA, weightB);
        double countPenalty = countDiff * 0.2;

        return centroidPenalty + countPenalty;
    }

    // ========== 预测方法 ==========

    /**
     * 标准KNN预测（精确匹配）
     */
    public int predict(double[] features) {
        return predictByAlgorithm(features, "euclidean");
    }

    /**
     * 宽松形状匹配预测（允许多余像素）
     */
    public int predictShapeMatch(double[] features) {
        return predictByAlgorithm(features, "shape");
    }

    /**
     * 纯形状匹配预测（忽略颜色，只看轮廓）
     */
    public int predictPureShape(double[] features) {
        return predictByAlgorithm(features, "pureShape");
    }

    /**
     * 重心偏移容忍预测
     */
    public int predictCentroidAware(double[] features, int width, int height) {
        return predictByAlgorithmWithDims(features, "centroid", width, height);
    }

    /**
     * 根据指定算法预测
     * @param algorithm "euclidean", "shape", "pureShape"
     */
    public int predictByAlgorithm(double[] features, String algorithm) {
        if (samples.isEmpty()) {
            throw new IllegalStateException("No training samples added");
        }

        PriorityQueue<Neighbor> heap = new PriorityQueue<>(Comparator.comparingDouble(n -> n.distance));
        for (Sample s : samples) {
            double dist = calculateDistance(features, s.features, algorithm);
            heap.offer(new Neighbor(dist, s.label));
        }

        // K近邻投票
        Map<Integer, Integer> labelCount = new HashMap<>();
        for (int i = 0; i < k && !heap.isEmpty(); i++) {
            Neighbor neighbor = heap.poll();
            labelCount.put(neighbor.label, labelCount.getOrDefault(neighbor.label, 0) + 1);
        }

        // 返回得票最高的标签
        int bestLabel = -1;
        int bestCount = -1;
        for (Map.Entry<Integer, Integer> entry : labelCount.entrySet()) {
            if (entry.getValue() > bestCount) {
                bestCount = entry.getValue();
                bestLabel = entry.getKey();
            }
        }
        return bestLabel;
    }

    /**
     * 获取最接近的类别（基于欧氏距离）
     * @param features 特征向量
     * @return 最接近的类别标签
     */
    public int getClosestCategory(double[] features) {
        if (samples.isEmpty()) {
            return -1;
        }

        double minDistance = Double.MAX_VALUE;
        int closestLabel = -1;

        for (Sample s : samples) {
            double dist = calculateDistance(features, s.features, "euclidean");
            if (dist < minDistance) {
                minDistance = dist;
                closestLabel = s.label;
            }
        }

        return closestLabel;
    }

    public int predictByAlgorithmWithDims(double[] features, String algorithm, int width, int height) {
        if (samples.isEmpty()) {
            throw new IllegalStateException("No training samples added");
        }

        PriorityQueue<Neighbor> heap = new PriorityQueue<>(Comparator.comparingDouble(n -> n.distance));
        for (Sample s : samples) {
            double dist = calculateDistanceWithDims(features, s.features, algorithm, width, height);
            heap.offer(new Neighbor(dist, s.label));
        }

        Map<Integer, Integer> labelCount = new HashMap<>();
        for (int i = 0; i < k && !heap.isEmpty(); i++) {
            Neighbor neighbor = heap.poll();
            labelCount.put(neighbor.label, labelCount.getOrDefault(neighbor.label, 0) + 1);
        }

        int bestLabel = -1;
        int bestCount = -1;
        for (Map.Entry<Integer, Integer> entry : labelCount.entrySet()) {
            if (entry.getValue() > bestCount) {
                bestCount = entry.getValue();
                bestLabel = entry.getKey();
            }
        }
        return bestLabel;
    }

    private double calculateDistance(double[] a, double[] b, String algorithm) {
        switch (algorithm) {
            case "shape": return shapeAwareDistance(a, b);
            case "pureShape": return pureShapeDistance(a, b);
            case "histogram": return colorHistogramDistance(a, b, 8);
            case "colorCount": return colorCountDistance(a, b);
            default: return euclideanDistance(a, b);
        }
    }

    private double calculateDistanceWithDims(double[] a, double[] b, String algorithm, int width, int height) {
        switch (algorithm) {
            case "centroid": return centroidAwareDistance(a, b, width, height);
            default: return calculateDistance(a, b, algorithm);
        }
    }

    // ========== 像素级校验方法 ==========
    // 透明阈值：pattern中透明位置被画上颜色的比例不超过此值
    private static final double EXTRA_PIXEL_THRESHOLD = 0.35;  // 35%
    // 遗漏阈值：pattern中有色位置被画成透明的比例不超过此值
    private static final double MISSING_PIXEL_THRESHOLD = 0.40; // 40%

    /**
     * 像素级校验：检查输入是否符合pattern的约束
     * @param input 输入像素矩阵（归一化）
     * @param patternSample 训练样本的原始矩阵
     * @return true 表示通过校验，false 表示被否决
     */
    public boolean validatePixelConstraints(byte[][] input, int label) {
        // 找到该label对应的样本
        for (Sample s : samples) {
            if (s.label == label && s.rawMatrix != null) {
                return validatePixelConstraintsForMatrix(input, s.rawMatrix);
            }
        }
        // 没有原始矩阵，默认通过
        return true;
    }

    /**
     * 像素级校验：检查输入是否符合约束
     * @param input 输入像素矩阵
     * @param pattern pattern矩阵
     * @return true 表示通过校验，false 表示被否决
     */
    private boolean validatePixelConstraintsForMatrix(byte[][] input, byte[][] pattern) {
        if (input == null || pattern == null || input.length != pattern.length) {
            return true; // 默认通过
        }

        int width = Math.min(input[0].length, pattern[0].length);
        int height = Math.min(input.length, pattern.length);

        int patternTransparentCount = 0; // pattern中透明位置总数
        int extraColorCount = 0;          // pattern中透明位置被画上颜色的数量

        int patternColoredCount = 0;      // pattern中有色位置总数
        int missingColorCount = 0;         // pattern中有色位置被画成透明的数量

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int patternColor = pattern[y][x] & 0xFF;
                int inputColor = input[y][x] & 0xFF;

                // 判断是否为透明/背景色（索引接近0或等于背景白色16）
                boolean patternIsTransparent = patternColor == 0 || patternColor == 16;
                boolean inputIsTransparent = inputColor == 0 || inputColor == 16;

                if (patternIsTransparent) {
                    patternTransparentCount++;
                    // pattern是透明的，但输入画上了颜色
                    if (!inputIsTransparent) {
                        extraColorCount++;
                    }
                } else {
                    patternColoredCount++;
                    // pattern是有色的，但输入画成了透明
                    if (inputIsTransparent) {
                        missingColorCount++;
                    }
                }
            }
        }

        // 检查透明位置被占领的比例
        if (patternTransparentCount > 0) {
            double extraRatio = (double) extraColorCount / patternTransparentCount;
            if (extraRatio > EXTRA_PIXEL_THRESHOLD) {
                return false; // 超过35%阈值，拒绝
            }
        }

        // 检查有色位置变成透明的比例
        if (patternColoredCount > 0) {
            double missingRatio = (double) missingColorCount / patternColoredCount;
            if (missingRatio > MISSING_PIXEL_THRESHOLD) {
                return false; // 超过40%阈值，拒绝
            }
        }

        return true;
    }

    /**
     * 获取样本的原始矩阵
     */
    public byte[][] getSampleRawMatrix(int label) {
        for (Sample s : samples) {
            if (s.label == label && s.rawMatrix != null) {
                return s.rawMatrix;
            }
        }
        return null;
    }

    /**
     * 多算法融合预测 - 同时使用多种算法，返回投票最多的标签
     * 按严格程度分配权重：越严格权重越高
     */
    public int predictMultiAlgorithm(double[] features) {
        return predictMultiAlgorithmWithDims(features, 16, 16);
    }

    /**
     * 多算法融合预测（带尺寸参数，用于重心算法）
     */
    public int predictMultiAlgorithmWithDims(double[] features, int width, int height) {
        Map<Integer, Integer> totalVotes = new HashMap<>();

        // 算法严格程度排名（从高到低）:
        // 1. euclidean    - 最严格，逐像素精确匹配（权重6）
        // 2. histogram    - 颜色直方图，统计颜色分布（权重5）
        // 3. colorCount   - 色块数量统计，权重高数量多（权重4）
        // 4. shape        - 宽松形状匹配，允许多余像素（权重3）
        // 5. pureShape    - 忽略颜色，只看轮廓（权重2）
        // 6. centroid     - 最宽松，只看位置和数量（权重1）

        // euclidean（权重6）
        int result1 = predictByAlgorithm(features, "euclidean");
        if (result1 != -1) {
            totalVotes.put(result1, totalVotes.getOrDefault(result1, 0) + 6);
        }

        // histogram（权重5）
        int result2 = predictByAlgorithm(features, "histogram");
        if (result2 != -1) {
            totalVotes.put(result2, totalVotes.getOrDefault(result2, 0) + 5);
        }

        // colorCount（权重4）
        int result6 = predictByAlgorithm(features, "colorCount");
        if (result6 != -1) {
            totalVotes.put(result6, totalVotes.getOrDefault(result6, 0) + 4);
        }

        // shape（权重3）
        int result3 = predictByAlgorithm(features, "shape");
        if (result3 != -1) {
            totalVotes.put(result3, totalVotes.getOrDefault(result3, 0) + 3);
        }

        // pureShape（权重2）
        int result4 = predictByAlgorithm(features, "pureShape");
        if (result4 != -1) {
            totalVotes.put(result4, totalVotes.getOrDefault(result4, 0) + 2);
        }

        // centroid（权重1）
        int result5 = predictByAlgorithmWithDims(features, "centroid", width, height);
        if (result5 != -1) {
            totalVotes.put(result5, totalVotes.getOrDefault(result5, 0) + 1);
        }

        // 返回得票最高的
        int bestLabel = -1;
        int bestCount = 0;
        for (Map.Entry<Integer, Integer> entry : totalVotes.entrySet()) {
            if (entry.getValue() > bestCount) {
                bestCount = entry.getValue();
                bestLabel = entry.getKey();
            }
        }
        return bestLabel;
    }

    /**
     * 预测样本类别，带最低匹配度要求（基于投票比例）
     */
    public int predictWithThreshold(double[] features, double minMatchRatio) {
        return predictWithThresholdByAlgorithm(features, minMatchRatio, "euclidean");
    }

    public int predictWithThresholdByAlgorithm(double[] features, double minMatchRatio, String algorithm) {
        if (samples.isEmpty()) {
            throw new IllegalStateException("No training samples added");
        }
        if (minMatchRatio <= 0 || minMatchRatio > 1) {
            throw new IllegalArgumentException("minMatchRatio must be in (0,1]");
        }

        PriorityQueue<Neighbor> heap = new PriorityQueue<>(Comparator.comparingDouble(n -> n.distance));
        for (Sample s : samples) {
            double dist = calculateDistance(features, s.features, algorithm);
            heap.offer(new Neighbor(dist, s.label));
        }

        Map<Integer, Integer> labelCount = new HashMap<>();
        for (int i = 0; i < k && !heap.isEmpty(); i++) {
            Neighbor neighbor = heap.poll();
            labelCount.put(neighbor.label, labelCount.getOrDefault(neighbor.label, 0) + 1);
        }

        int maxVotes = 0;
        int bestLabel = -1;
        for (Map.Entry<Integer, Integer> entry : labelCount.entrySet()) {
            if (entry.getValue() > maxVotes) {
                maxVotes = entry.getValue();
                bestLabel = entry.getKey();
            }
        }

        double confidence = (double) maxVotes / k;
        if (confidence >= minMatchRatio) {
            return bestLabel;
        } else {
            return -1;
        }
    }

    public int predictWithDistanceLimit(double[] features, double maxDistance) {
        if (samples.isEmpty()) return -1;

        double minDist = Double.MAX_VALUE;
        int bestLabel = -1;

        for (Sample s : samples) {
            double dist = euclideanDistance(features, s.features);
            if (dist < minDist) {
                minDist = dist;
                bestLabel = s.label;
            }
        }

        if (minDist <= maxDistance) {
            return bestLabel;
        } else {
            return -1;
        }
    }

    // ========== 便捷工具：将字节矩阵转为 double 特征向量 ==========
    /**
     * 将 n x m 的字节矩阵转换为 double 特征向量（展平，值归一化到 0~1）
     * @param matrix 输入矩阵，每个元素为 byte（例如 0~255 的灰度值）
     * @return 归一化后的 double 数组
     */
    public static double[] matrixToFeature(byte[][] matrix) {
        if (matrix == null || matrix.length == 0) return new double[0];
        int rows = matrix.length;
        int cols = matrix[0].length;
        double[] feature = new double[rows * cols];
        int idx = 0;
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                int val = matrix[i][j] & 0xFF;   // 转为 0~255 无符号
                feature[idx++] = val / 255.0;     // 归一化到 [0,1]
            }
        }
        return feature;
    }

    /**
     * 将字节矩阵转换为特征向量，支持将指定颜色视为透明
     * @param matrix 输入矩阵
     * @param transparentAsZero 如果为 true，则将背景白色（索引16）视为透明
     * @return 归一化后的 double 数组
     */
    public static double[] matrixToFeature(byte[][] matrix, boolean transparentAsZero) {
        if (matrix == null || matrix.length == 0) return new double[0];
        int rows = matrix.length;
        int cols = matrix[0].length;
        
        // 背景白色ID（用于识别时视为透明）
        final int BACKGROUND_WHITE_ID = 16;
        
        if (transparentAsZero) {
            // 真正忽略透明像素：将透明像素设为-1，归一化后为-1/255≈-0.004
            double[] feature = new double[rows * cols];
            int idx = 0;
            for (int i = 0; i < rows; i++) {
                for (int j = 0; j < cols; j++) {
                    int val = matrix[i][j] & 0xFF;
                    // 背景白色(16)视为透明，设为-1
                    if (val == BACKGROUND_WHITE_ID) {
                        feature[idx++] = -1.0 / 255.0;
                    } else {
                        feature[idx++] = val / 255.0;
                    }
                }
            }
            return feature;
        } else {
            // 不使用透明处理
            double[] feature = new double[rows * cols];
            int idx = 0;
            for (int i = 0; i < rows; i++) {
                for (int j = 0; j < cols; j++) {
                    int val = matrix[i][j] & 0xFF;
                    feature[idx++] = val / 255.0;
                }
            }
            return feature;
        }
    }

    /**
     * 将字节矩阵转为原始整数值特征向量（不归一化）
     */
    public static double[] matrixToFeatureRaw(byte[][] matrix) {
        if (matrix == null || matrix.length == 0) return new double[0];
        int rows = matrix.length;
        int cols = matrix[0].length;
        double[] feature = new double[rows * cols];
        int idx = 0;
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                feature[idx++] = matrix[i][j] & 0xFF;
            }
        }
        return feature;
    }

    // ========== 示例主函数 ==========
    public static void main(String[] args) {
        // 假设我们有 8x8 的低像素手绘图（数字矩阵）
        // 类别：0 代表圆形，1 代表方形（举例）

        SimpleKNN knn = new SimpleKNN(5);   // K=5，使用投票比例阈值时更稳定

        // ---- 训练数据：手工构造几个简单样本 ----
        // 样本1: 近似圆形的 8x8 矩阵
        byte[][] circle = {
            {0,0,0,0,0,0,0,0},
            {0,1,1,1,1,1,1,0},
            {0,1,2,2,2,2,1,0},
            {0,1,2,3,3,2,1,0},
            {0,1,2,3,3,2,1,0},
            {0,1,2,2,2,2,1,0},
            {0,1,1,1,1,1,1,0},
            {0,0,0,0,0,0,0,0}
        };
        knn.addSample(matrixToFeature(circle), 0); // 类别0: 圆形

        // 样本2: 近似方形的 8x8 矩阵
        byte[][] square = {
            {0,0,0,0,0,0,0,0},
            {0,3,3,3,3,3,3,0},
            {0,3,1,1,1,1,3,0},
            {0,3,1,1,1,1,3,0},
            {0,3,1,1,1,1,3,0},
            {0,3,1,1,1,1,3,0},
            {0,3,3,3,3,3,3,0},
            {0,0,0,0,0,0,0,0}
        };
        knn.addSample(matrixToFeature(square), 1); // 类别1: 方形

        // 添加更多样本，增加鲁棒性
        byte[][] circle2 = {
            {0,0,0,0,0,0,0,0},
            {0,1,1,1,1,1,0,0},
            {0,1,2,2,2,1,0,0},
            {0,1,2,3,2,1,0,0},
            {0,1,2,2,2,1,0,0},
            {0,1,1,1,1,1,0,0},
            {0,0,0,0,0,0,0,0},
            {0,0,0,0,0,0,0,0}
        };
        knn.addSample(matrixToFeature(circle2), 0);

        byte[][] square2 = {
            {0,0,0,0,0,0,0,0},
            {0,2,2,2,2,2,2,0},
            {0,2,1,1,1,1,2,0},
            {0,2,1,1,1,1,2,0},
            {0,2,1,1,1,1,2,0},
            {0,2,2,2,2,2,2,0},
            {0,0,0,0,0,0,0,0},
            {0,0,0,0,0,0,0,0}
        };
        knn.addSample(matrixToFeature(square2), 1);

        // ---- 测试1: 一个接近圆形的未知图形 ----
        byte[][] unknownCircle = {
            {0,0,0,0,0,0,0,0},
            {0,1,1,1,1,1,1,0},
            {0,1,2,2,2,2,1,0},
            {0,1,2,2,2,2,1,0},
            {0,1,2,2,2,2,1,0},
            {0,1,2,2,2,2,1,0},
            {0,1,1,1,1,1,1,0},
            {0,0,0,0,0,0,0,0}
        };
        double[] feat = matrixToFeature(unknownCircle);
        
        // 无阈值预测
        int normalResult = knn.predict(feat);
        System.out.println("无阈值预测结果: " + normalResult + (normalResult == 0 ? " (圆形)" : " (方形)"));
        
        // 带阈值预测，要求至少 60% 的邻居投票一致
        int thresholdResult = knn.predictWithThreshold(feat, 0.6);
        if (thresholdResult == -1) {
            System.out.println("带阈值(0.6)预测结果: 无法确定，匹配度不足");
        } else {
            System.out.println("带阈值(0.6)预测结果: " + thresholdResult + (thresholdResult == 0 ? " (圆形)" : " (方形)"));
        }

        // ---- 测试2: 一个完全不像训练数据的随机矩阵 ----
        byte[][] noise = new byte[8][8];
        Random rand = new Random();
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                noise[i][j] = (byte) rand.nextInt(256);
            }
        }
        double[] noiseFeat = matrixToFeature(noise);
        int noiseResult = knn.predictWithThreshold(noiseFeat, 0.6);
        System.out.println("\n随机噪声预测结果: " + (noiseResult == -1 ? "无法识别" : "类别 " + noiseResult));
    }
}