package io.wifi.starrailexpress.client;

/** 列车环境音专用的结构兜底，不影响任务所用的天空可见性判定。 */
final class TrainInteriorDetection {
    // 覆盖挑高餐车；只扫描眼睛上方，站在车顶时不会把脚下车顶当成遮挡。
    static final int MAX_ROOF_HEIGHT = 12;
    static final int MAX_GAP_RADIUS = 2;

    private TrainInteriorDetection() {
    }

    @FunctionalInterface
    interface BlockProbe {
        /** 相对眼睛所在方块的坐标处，是否存在可遮挡的实体结构。 */
        boolean blocks(int x, int y, int z);
    }

    static boolean isCovered(BlockProbe probe) {
        if (hasRoof(probe, 0, 0)) {
            return true;
        }
        // 连接处可能有透天窄缝。必须有相对两侧顶棚，单侧屋檐不能把车外判成室内。
        return (hasNearbyRoof(probe, 1, 0) && hasNearbyRoof(probe, -1, 0))
                || (hasNearbyRoof(probe, 0, 1) && hasNearbyRoof(probe, 0, -1));
    }

    private static boolean hasNearbyRoof(BlockProbe probe, int dx, int dz) {
        for (int distance = 1; distance <= MAX_GAP_RADIUS; distance++) {
            if (hasRoof(probe, dx * distance, dz * distance)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasRoof(BlockProbe probe, int x, int z) {
        for (int y = 1; y <= MAX_ROOF_HEIGHT; y++) {
            if (probe.blocks(x, y, z)) {
                return true;
            }
        }
        return false;
    }
}
