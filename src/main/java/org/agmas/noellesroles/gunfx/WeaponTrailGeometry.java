/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.agmas.noellesroles.gunfx;

import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** 枪口 / 双刀持握点的共用几何，服务端广播与客户端补正都走这里。 */
public final class WeaponTrailGeometry {
    private WeaponTrailGeometry() {
    }

    public static Vec3 sideways(Vec3 view) {
        Vec3 side = view.cross(new Vec3(0.0D, 1.0D, 0.0D));
        return side.lengthSqr() < 1.0E-4D ? new Vec3(1.0D, 0.0D, 0.0D) : side.normalize();
    }

    public static Vec3 muzzlePoint(Player shooter, Vec3 eye, Vec3 view) {
        double handSide = shooter.getMainArm() == HumanoidArm.RIGHT ? 0.20D : -0.20D;
        return eye.add(view.scale(0.55D)).add(sideways(view).scale(handSide)).add(0.0D, -0.18D, 0.0D);
    }

    /** 第一人称本地视角下更贴枪口的起点，避免弹道从脸侧凭空伸出。 */
    public static Vec3 firstPersonMuzzle(Vec3 eye, Vec3 view, HumanoidArm mainArm) {
        double handSide = mainArm == HumanoidArm.RIGHT ? 0.16D : -0.16D;
        return eye.add(view.scale(0.42D)).add(sideways(view).scale(handSide)).add(0.0D, -0.12D, 0.0D);
    }

    /**
     * 冲刺时一把刀的位置：以脚底坐标为原点，抬到持刀高度后再沿位移方向和左右手偏移。
     *
     * @param sideSign +1 主手侧，-1 副手侧
     */
    public static Vec3 bladePoint(Vec3 feet, Vec3 view, double sideSign) {
        return feet.add(0.0D, 1.18D, 0.0D)
                .add(view.scale(0.38D))
                .add(sideways(view).scale(sideSign * 0.33D))
                .add(0.0D, -0.08D, 0.0D);
    }
}
