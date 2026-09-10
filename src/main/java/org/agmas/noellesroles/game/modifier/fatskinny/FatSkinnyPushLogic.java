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

package org.agmas.noellesroles.game.modifier.fatskinny;

/**
 * 胖子/瘦子水平拉伸与玩家间推挤的纯计算。不依赖 Minecraft 类型，便于单测。
 */
public final class FatSkinnyPushLogic {
    /** 胖子模型左右（水平）拉伸倍率。高度不变。 */
    public static final float FAT_HORIZONTAL_SCALE = 1.8F;
    /** 瘦子模型左右压扁倍率。高度不变。 */
    public static final float SKINNY_HORIZONTAL_SCALE = 0.45F;

    /** 胖子把周围玩家挤开的水平半径（格）。 */
    public static final double FAT_PUSH_RADIUS = 1.4D;
    /** 瘦子被周围玩家挤压的水平半径（格）。 */
    public static final double SKINNY_SQUEEZE_RADIUS = 1.05D;

    /** 胖子推开力度（每 tick 叠加到水平速度上）。越近越强。 */
    public static final double FAT_PUSH_STRENGTH = 0.32D;
    /** 瘦子被挤开力度。 */
    public static final double SKINNY_SQUEEZE_STRENGTH = 0.24D;
    /** 胖子推瘦子时的额外倍率。 */
    public static final double SKINNY_FROM_FAT_MULTIPLIER = 1.4D;
    /** 两个胖子互相推挤时略减弱，避免弹飞。 */
    public static final double FAT_VS_FAT_MULTIPLIER = 0.7D;

    private static final double OVERLAP_EPSILON = 1.0e-4D;

    private FatSkinnyPushLogic() {
    }

    public record Vec2(double x, double z) {
        public static final Vec2 ZERO = new Vec2(0.0D, 0.0D);

        public boolean isZero() {
            return x == 0.0D && z == 0.0D;
        }
    }

    /**
     * 胖子把目标沿水平方向推开。目标在半径外时返回零向量。
     */
    public static Vec2 fatPush(double fatX, double fatZ, double targetX, double targetZ,
            boolean targetIsSkinny, boolean targetIsFat) {
        return outwardForce(fatX, fatZ, targetX, targetZ, FAT_PUSH_RADIUS, FAT_PUSH_STRENGTH,
                targetIsSkinny, targetIsFat);
    }

    /**
     * 瘦子被另一名玩家挤开：力从对方指向瘦子。
     */
    public static Vec2 skinnySqueeze(double skinnyX, double skinnyZ, double otherX, double otherZ) {
        return outwardForce(otherX, otherZ, skinnyX, skinnyZ, SKINNY_SQUEEZE_RADIUS,
                SKINNY_SQUEEZE_STRENGTH, false, false);
    }

    public static float horizontalScale(boolean fat, boolean skinny) {
        if (fat) {
            return FAT_HORIZONTAL_SCALE;
        }
        if (skinny) {
            return SKINNY_HORIZONTAL_SCALE;
        }
        return 1.0F;
    }

    private static Vec2 outwardForce(double fromX, double fromZ, double toX, double toZ,
            double radius, double strength, boolean targetIsSkinny, boolean targetIsFat) {
        double dx = toX - fromX;
        double dz = toZ - fromZ;
        double distSq = dx * dx + dz * dz;
        if (distSq > radius * radius) {
            return Vec2.ZERO;
        }
        double dist = Math.sqrt(distSq);
        if (dist < OVERLAP_EPSILON) {
            dx = 1.0D;
            dz = 0.0D;
            dist = 1.0D;
        }
        double penetration = (radius - Math.min(dist, radius)) / radius;
        double force = strength * penetration;
        if (targetIsSkinny) {
            force *= SKINNY_FROM_FAT_MULTIPLIER;
        } else if (targetIsFat) {
            force *= FAT_VS_FAT_MULTIPLIER;
        }
        double inv = 1.0D / dist;
        return new Vec2(dx * inv * force, dz * inv * force);
    }
}
