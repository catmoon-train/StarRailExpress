package io.wifi.starrailexpress.api;

public class AreasSettingUtils {
    public static class StoreableAABB {
        public final double minX;
        public final double minY;
        public final double minZ;
        public final double maxX;
        public final double maxY;
        public final double maxZ;

        public StoreableAABB(net.minecraft.world.phys.AABB aabb) {
            this.minX = aabb.minX;
            this.minY = aabb.minY;
            this.minZ = aabb.minZ;
            this.maxX = aabb.maxX;
            this.maxY = aabb.maxY;
            this.maxZ = aabb.maxZ;
        }

        public StoreableAABB(net.minecraft.world.phys.Vec3 pos1, net.minecraft.world.phys.Vec3 pos2) {
            this.minX = Math.min(pos1.x, pos2.x);
            this.minY = Math.min(pos1.y, pos2.y);
            this.minZ = Math.min(pos1.z, pos2.z);
            this.maxX = Math.max(pos1.x, pos2.x);
            this.maxY = Math.max(pos1.y, pos2.y);
            this.maxZ = Math.max(pos1.z, pos2.z);
        }

        public StoreableAABB(double d, double e, double f, double g, double h, double i) {
            this.minX = Math.min(d, g);
            this.minY = Math.min(e, h);
            this.minZ = Math.min(f, i);
            this.maxX = Math.max(d, g);
            this.maxY = Math.max(e, h);
            this.maxZ = Math.max(f, i);
        }

        public StoreableAABB setMinX(double d) {
            return new StoreableAABB(d, this.minY, this.minZ, this.maxX, this.maxY, this.maxZ);
        }

        public StoreableAABB setMinY(double d) {
            return new StoreableAABB(this.minX, d, this.minZ, this.maxX, this.maxY, this.maxZ);
        }

        public StoreableAABB setMinZ(double d) {
            return new StoreableAABB(this.minX, this.minY, d, this.maxX, this.maxY, this.maxZ);
        }

        public StoreableAABB setMaxX(double d) {
            return new StoreableAABB(this.minX, this.minY, this.minZ, d, this.maxY, this.maxZ);
        }

        public StoreableAABB setMaxY(double d) {
            return new StoreableAABB(this.minX, this.minY, this.minZ, this.maxX, d, this.maxZ);
        }

        public StoreableAABB setMaxZ(double d) {
            return new StoreableAABB(this.minX, this.minY, this.minZ, this.maxX, this.maxY, d);
        }

        public net.minecraft.world.phys.AABB toAABB() {
            return new net.minecraft.world.phys.AABB(this.minX, this.minY, this.minZ, this.maxX, this.maxY, this.maxZ);
        }
    }

    public static class PosWithOrientation {
        public final StoreableVec3 pos;
        public final float yaw;
        public final float pitch;

        public PosWithOrientation(StoreableVec3 pos, float yaw, float pitch) {
            this.pos = pos;
            this.yaw = yaw;
            this.pitch = pitch;
        }

        public PosWithOrientation(double x, double y, double z, float yaw, float pitch) {
            this(new StoreableVec3(x, y, z), yaw, pitch);
        }

    }

    public static class StoreableBlockPos {
        public int x = 0, y = 0, z = 0;

        public int x() {
            return x;
        }

        public int y() {
            return y;
        }

        public int z() {
            return z;
        }

        public StoreableBlockPos(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public StoreableBlockPos(net.minecraft.core.BlockPos blockPos) {
            this.x = blockPos.getX();
            this.y = blockPos.getY();
            this.z = blockPos.getZ();
        }

        public net.minecraft.core.BlockPos toBlockPos() {
            return new net.minecraft.core.BlockPos(x, y, z);
        }
    }

    public static class StoreableVec3 {
        public double x = 0, y = 0, z = 0;

        public double x() {
            return x;
        }

        public double y() {
            return y;
        }

        public double z() {
            return z;
        }

        public StoreableVec3(net.minecraft.world.phys.Vec3 vec) {
            this.x = vec.x;
            this.y = vec.y;
            this.z = vec.z;
        }

        public StoreableVec3(double x, double y, double z) {
            this.x = x;
            this.z = z;
            this.y = y;
        }

        public net.minecraft.world.phys.Vec3 toVec3() {
            return new net.minecraft.world.phys.Vec3(x, y, z);
        }
    }
}
