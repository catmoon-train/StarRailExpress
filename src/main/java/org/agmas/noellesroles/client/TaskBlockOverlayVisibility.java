package org.agmas.noellesroles.client;

/** 任务方块透视的纯距离策略；主循环与独立绘制入口共用，不读取世界、不分配对象。 */
final class TaskBlockOverlayVisibility {
    private TaskBlockOverlayVisibility() {
    }

    static int maxDistanceSquared(boolean ultraPerfMode) {
        return ultraPerfMode ? 64 * 64 : 128 * 128;
    }

    static boolean isOutsideDistance(int blockX, int blockY, int blockZ,
            double cameraX, double cameraY, double cameraZ, int maxDistanceSquared) {
        double dx = blockX + 0.5 - cameraX;
        double dy = blockY + 0.5 - cameraY;
        double dz = blockZ + 0.5 - cameraZ;
        return dx * dx + dy * dy + dz * dz > maxDistanceSquared;
    }
}
