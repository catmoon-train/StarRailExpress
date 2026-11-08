package org.agmas.noellesroles.client.renderer;

import net.minecraft.world.phys.Vec3;

/** 固定枪身的直刺姿态；只改变外观，枪尖始终与原有判定射线重合。 */
public record BambooSpearVisualPose(Vec3 tail, Vec3 tip, Vec3 direction) {
    public static final float POLE_LENGTH = 2.4F;

    /** 用同步伸出距离平移整根竹枪，手侧偏移只影响枪尾，不偏移枪尖。 */
    public static BambooSpearVisualPose at(Vec3 base, Vec3 view, float reach, float yaw, boolean rightHand) {
        Vec3 forward = view.normalize();
        double radians = Math.toRadians(yaw);
        Vec3 right = new Vec3(-Math.cos(radians), 0.0, -Math.sin(radians));
        Vec3 down = forward.cross(right).normalize();
        Vec3 gripOffset = right.scale(rightHand ? 0.35 : -0.35).add(down.scale(0.28));
        Vec3 direction = forward.scale(POLE_LENGTH).subtract(gripOffset).normalize();
        Vec3 tip = base.add(forward.scale(Math.max(0.0F, reach)));
        return new BambooSpearVisualPose(tip.subtract(direction.scale(POLE_LENGTH)), tip, direction);
    }
}
