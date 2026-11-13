package org.agmas.noellesroles.client.map;

/** 每张纹理独立累计脏矩形；上限为开区间，可直接用于子区域上传。 */
final class AreaMapDirtyRect {
    int minX, minZ, maxX, maxZ;

    AreaMapDirtyRect() { clear(); }

    void clear() {
        minX = minZ = Integer.MAX_VALUE;
        maxX = maxZ = 0;
    }

    void include(int x, int z) {
        minX = Math.min(minX, x);
        minZ = Math.min(minZ, z);
        maxX = Math.max(maxX, x + 1);
        maxZ = Math.max(maxZ, z + 1);
    }

    boolean isEmpty() { return minX >= maxX || minZ >= maxZ; }
}
