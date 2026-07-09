package io.wifi.starrailexpress.client.mirror;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.phys.Vec3;

/**
 * 镜面所在的反射平面。
 *
 * <p>镜子方块位于层坐标 {@code L}（沿 facing 轴），反射面取它朝向房间的那一侧表面，
 * 于是平面落在整数边界 {@code boundary}：facing 为正向时 {@code L + 1}，负向时 {@code L}。
 *
 * <p>方块格 {@code [v, v+1]} 关于该边界的像是 {@code [2B-1-v, 2B-v]}，故 {@code v' = 2B-1-v}；
 * 连续坐标直接 {@code x' = 2B-x}。
 *
 * <p>注意 {@link #sourceCoord(int)} 的 {@code k = 0} 层（紧贴镜面的那层房间格）的像正好落在
 * 镜子自己所在的墙层上。调用方必须从 {@code k = 1} 开始遍历，否则会把镜子方块连同它周围的墙一起冲掉。
 * 由于 {@code k = 0} 层就是玩家站立的空气层，跳过它不会丢失任何可见内容，也不会让反射产生位移：
 * 镜子方块本身渲染为不可见，恰好顶替了那一层空气的像。
 */
public record MirrorPlane(Direction facing, int boundary) {

    public static MirrorPlane of(BlockPos mirrorPos, Direction facing) {
        int layer = coord(mirrorPos, facing.getAxis());
        boolean positive = facing.getAxisDirection() == Direction.AxisDirection.POSITIVE;
        return new MirrorPlane(facing, positive ? layer + 1 : layer);
    }

    public static int coord(BlockPos pos, Direction.Axis axis) {
        return axis.choose(pos.getX(), pos.getY(), pos.getZ());
    }

    public Direction.Axis axis() {
        return facing.getAxis();
    }

    public boolean positive() {
        return facing.getAxisDirection() == Direction.AxisDirection.POSITIVE;
    }

    /** 镜子方块自身所在的层坐标。 */
    public int mirrorLayer() {
        return positive() ? boundary - 1 : boundary;
    }

    /** 镜面正前方第 k 层房间格的层坐标（k >= 1，见类注释）。 */
    public int sourceCoord(int k) {
        return positive() ? boundary + k : boundary - 1 - k;
    }

    public int reflectBlockCoord(int v) {
        return 2 * boundary - 1 - v;
    }

    public double reflectEntityCoord(double v) {
        return 2.0D * boundary - v;
    }

    public BlockPos reflect(BlockPos pos) {
        return switch (axis()) {
            case X -> new BlockPos(reflectBlockCoord(pos.getX()), pos.getY(), pos.getZ());
            case Y -> new BlockPos(pos.getX(), reflectBlockCoord(pos.getY()), pos.getZ());
            case Z -> new BlockPos(pos.getX(), pos.getY(), reflectBlockCoord(pos.getZ()));
        };
    }

    public Vec3 reflect(Vec3 v) {
        return switch (axis()) {
            case X -> new Vec3(reflectEntityCoord(v.x), v.y, v.z);
            case Y -> new Vec3(v.x, reflectEntityCoord(v.y), v.z);
            case Z -> new Vec3(v.x, v.y, reflectEntityCoord(v.z));
        };
    }

    /** X 轴平面翻 east/west，Z 轴平面翻 north/south。 */
    public Mirror blockMirror() {
        return axis() == Direction.Axis.X ? Mirror.FRONT_BACK : Mirror.LEFT_RIGHT;
    }

    /**
     * MC 的朝向向量为 {@code (-sin(yaw), 0, cos(yaw))}。
     * 翻 X 分量得 {@code yaw' = -yaw}；翻 Z 分量得 {@code yaw' = 180 - yaw}。
     */
    public float reflectYaw(float yaw) {
        return axis() == Direction.Axis.X ? -yaw : 180.0F - yaw;
    }

    public boolean inFront(Vec3 point) {
        double c = axis().choose(point.x, point.y, point.z);
        return positive() ? c >= boundary : c <= boundary;
    }
}
