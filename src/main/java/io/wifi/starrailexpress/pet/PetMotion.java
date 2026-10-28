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

package io.wifi.starrailexpress.pet;

/**
 * 随从运动的纯数学部分：不引用任何 Minecraft 类型，可直接单测。
 * <p>
 * 设计要点：
 * <ul>
 * <li>所有方法都是静态、无状态、不分配对象（{@code double} 三元组由调用方用数组或字段承载）；</li>
 * <li>位置用 {@code double} 计算，避免长时间跟随后的精度漂移；</li>
 * <li>「5 格硬钳制」是最后一道保险：无论平滑、地面吸附、维度切换怎么出问题，
 * 结果一定落在主人 {@link PetMotionProfile#MAX_DISTANCE} 格内。</li>
 * </ul>
 * 坐标系约定与 Minecraft 一致：+X 东、+Y 上、+Z 南；yaw 为角度制，0 朝 +Z（南）。
 */
public final class PetMotion {

    /** 地面吸附最多向下搜索的方块数。 */
    public static final int GROUND_SCAN_DEPTH = 5;

    /** 水平移动小于此值时不触发地面扫描（站着不动零成本）。 */
    public static final double GROUND_SCAN_EPSILON = 0.02D;

    private PetMotion() {
    }

    /**
     * 指数平滑逼近：把 {@code current} 朝 {@code target} 推进，与帧率无关。
     *
     * @param stiffness 刚度（每秒量级），越大越快贴近
     * @param dt        步长（秒）
     */
    public static double approach(double current, double target, double stiffness, double dt) {
        if (dt <= 0.0D) {
            return current;
        }
        // 1 - e^(-k·dt)：dt 越小步长越小，且永不越过 target（无过冲、无振荡）
        double factor = 1.0D - Math.exp(-stiffness * dt);
        return current + (target - current) * factor;
    }

    /**
     * 角度插值，走最短弧（跨越 ±180° 不会绕远路）。
     *
     * @param current 当前角度（度）
     * @param target  目标角度（度）
     * @param factor  0~1，1 表示立即到位
     */
    public static float lerpAngle(float current, float target, float factor) {
        float delta = wrapDegrees(target - current);
        return current + delta * clamp01(factor);
    }

    /** 把角度折回 [-180, 180)。180° 会被折成 -180°，与 Minecraft 的 Mth.wrapDegrees 一致。 */
    public static float wrapDegrees(float degrees) {
        float wrapped = degrees % 360.0F;
        if (wrapped >= 180.0F) {
            wrapped -= 360.0F;
        }
        if (wrapped < -180.0F) {
            wrapped += 360.0F;
        }
        return wrapped;
    }

    /**
     * 由「随从相对主人的水平偏移」求朝向（度）。
     * <p>
     * 与 Minecraft 的实体 yaw 一致：0 朝 +Z，90 朝 -X。
     */
    public static float yawFromDelta(double dx, double dz) {
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    /**
     * 环绕相位随时间的推进量：走一圈 {@code periodSeconds} 秒。
     * <p>
     * 用「绕主人转圈」而不是「直线追尾」，是因为陆行随从需要始终落在方块地面上；
     * 环绕时它只在小半径内移动，地面吸附几乎不需要向下深扫。
     */
    public static double orbitAngle(long tick, float periodSeconds) {
        if (periodSeconds <= 0.0F) {
            return 0.0D;
        }
        return (tick / 20.0D) * (Math.PI * 2.0D) / periodSeconds;
    }

    /**
     * 期望位置的 X 分量：主人位置 + 环绕偏移。
     *
     * @param angle 环绕相位（弧度）
     */
    public static double desiredX(double ownerX, float orbitRadius, double angle) {
        return ownerX + Math.cos(angle) * orbitRadius;
    }

    /** 期望位置的 Z 分量。 */
    public static double desiredZ(double ownerZ, float orbitRadius, double angle) {
        return ownerZ + Math.sin(angle) * orbitRadius;
    }

    /**
     * 期望位置的 Y 分量。
     *
     * @param ownerY       主人脚部 Y
     * @param ownerHeight  主人碰撞箱高度
     * @param tick         游戏刻，用于待机浮动
     */
    public static double desiredY(PetMotionProfile profile, double ownerY, float ownerHeight, long tick) {
        if (!profile.isFlyer()) {
            // 陆行类的 Y 由地面吸附决定，这里先给个「脚在地上」的起点
            return ownerY;
        }
        double base = ownerY + ownerHeight + profile.hoverHeight();
        if (profile.bobAmplitude() <= 0.0F) {
            return base;
        }
        // 0.15 rad/tick ≈ 3.4 秒一个来回，幅度很小，只用来「呼吸」
        return base + Math.sin(tick * 0.15D) * profile.bobAmplitude();
    }

    /**
     * 地面吸附：给定期望 Y 与当前 Y，返回该列上可站立的地面高度。
     * <p>
     * 这是随从运动里唯一需要查询世界的操作，因此：
     * <ul>
     * <li>由调用方在「水平位移足够小」时直接跳过（见 {@link #GROUND_SCAN_EPSILON}）；</li>
     * <li>最多向下扫 {@link #GROUND_SCAN_DEPTH} 格，找不到就回退 {@code fallbackY}，
     * 而不是无限向下找——悬崖边、水面上都不会卡住。</li>
     * </ul>
     *
     * @param sampler   查询「(x,y,z) 是否是可站立地面」的函数；返回 {@code null} 表示该格不是地面
     * @param startY    从哪个 Y 开始向下找
     * @param fallbackY 找不到地面时的回退值
     */
    public static double snapToGround(GroundSampler sampler, double x, double startY, double z, double fallbackY) {
        if (sampler == null) {
            return fallbackY;
        }
        double top = startY + 1.0D;
        for (int i = 0; i <= GROUND_SCAN_DEPTH; i++) {
            double y = top - i;
            Double ground = sampler.groundAt(x, y, z);
            if (ground != null) {
                return ground;
            }
        }
        return fallbackY;
    }

    /**
     * 5 格硬钳制：把随从位置拉回主人 {@link PetMotionProfile#MAX_DISTANCE} 格内。
     * <p>
     * 结果写回 {@code out}（长度 ≥ 3 的数组，{@code [x, y, z]}），不分配对象。
     *
     * @return 是否发生了钳制
     */
    public static boolean clampToOwner(double[] out, double ownerX, double ownerY, double ownerZ) {
        double dx = out[0] - ownerX;
        double dy = out[1] - ownerY;
        double dz = out[2] - ownerZ;
        double distanceSqr = dx * dx + dy * dy + dz * dz;
        double max = PetMotionProfile.MAX_DISTANCE;
        if (distanceSqr <= max * max) {
            return false;
        }
        double distance = Math.sqrt(distanceSqr);
        if (distance <= 1.0E-6D) {
            // 完全重合（理论上不会发生）：直接落到主人脚上，避免除零
            out[0] = ownerX;
            out[1] = ownerY;
            out[2] = ownerZ;
            return true;
        }
        double scale = max / distance;
        out[0] = ownerX + dx * scale;
        out[1] = ownerY + dy * scale;
        out[2] = ownerZ + dz * scale;
        return true;
    }

    /** 两点水平距离的平方（判断「是否走了足够远、需要重新吸附地面」）。 */
    public static double horizontalDistanceSqr(double x1, double z1, double x2, double z2) {
        double dx = x1 - x2;
        double dz = z1 - z2;
        return dx * dx + dz * dz;
    }

    private static float clamp01(float value) {
        if (value <= 0.0F) {
            return 0.0F;
        }
        return Math.min(value, 1.0F);
    }

    /**
     * 地面查询回调。
     * <p>
     * 抽象成函数式接口，是为了让 {@link PetMotion} 不依赖 Minecraft 世界对象：
     * 生产代码传「查 BlockState 是否可站立」的实现，单测传固定表。
     */
    @FunctionalInterface
    public interface GroundSampler {
        /**
         * @return 该列在此高度可站立时返回「站立面 Y」（方块顶面 + 1）；否则 {@code null}
         */
        Double groundAt(double x, double y, double z);
    }
}
