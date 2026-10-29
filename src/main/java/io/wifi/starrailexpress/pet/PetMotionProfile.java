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
 * 一种随从的运动手感参数。
 * <p>
 * 决定「它绕主人转多大圈、浮多高、贴得多紧」这类纯数值问题，
 * 与具体模型、动画、贴图无关——那些由附属模组的皮肤目录提供。
 * <p>
 * 两个内置类型（{@link Kind#WALKER} / {@link Kind#FLYER}）由 {@link #defaults(Kind)} 给出，
 * 单个皮肤可以在皮肤目录里覆盖其中任意一项（例如一只特别大的陆行随从需要更大的环绕半径）。
 *
 * @param kind         运动类型
 * @param orbitRadius  环绕主人的水平半径（格）
 * @param hoverHeight  悬浮高度（格）；{@link Kind#WALKER} 忽略此项
 * @param stiffness    跟随刚度，越大贴得越紧（每秒的量级）
 * @param bobAmplitude 待机浮动幅度（格）；{@link Kind#WALKER} 通常为 0
 */
public record PetMotionProfile(Kind kind, float orbitRadius, float hoverHeight, float stiffness,
        float bobAmplitude) {

    /** 运动类型。 */
    public enum Kind {
        /**
         * 陆行类：走在方块上，Y 轴由地面吸附决定。
         */
        WALKER,
        /**
         * 飞行类：浮在主人头部上方，不做地面吸附，带轻微上下浮动。
         */
        FLYER
    }

    /**
     * 硬钳制的最大距离（格）。
     * <p>
     * 只作为传送 / 换维度 / 重生时的兜底，<b>不参与正常追随</b>：
     * 正常追随由指数平滑完成，随从会自己跑过来。
     * 这里保持 5 格是因为皮肤目录里 {@code orbitRadius} 的合法性依赖它。
     */
    public static final float MAX_DISTANCE = 5.0F;

    /**
     * 陆行随从默认参数：绕得远一点，贴地。
     * <p>
     * {@code stiffness} 决定追随的「手感速度」。指数平滑每 tick 的逼近比例是
     * {@code 1 - e^(-k·0.05)}：k=10 时约 39%，也就是 2~3 tick 就把几格的差距走完，
     * 视觉上接近闪现。这里取 4.0（约 18%/tick），十几 tick 跑完一段距离，
     * 既跟得上跑动的玩家，又看得出是在「走过来」。
     */
    public static final PetMotionProfile WALKER = new PetMotionProfile(Kind.WALKER, 2.2F, 0.0F, 4.0F, 0.0F);
    /** 飞行随从默认参数：贴着主人转小圈，浮在头顶；比陆行稍快一点，但仍需平滑。 */
    public static final PetMotionProfile FLYER = new PetMotionProfile(Kind.FLYER, 1.0F, 0.55F, 6.0F, 0.06F);

    public PetMotionProfile {
        if (kind == null) {
            throw new IllegalArgumentException("kind 不能为 null");
        }
        if (!Float.isFinite(orbitRadius) || orbitRadius < 0.0F) {
            throw new IllegalArgumentException("orbitRadius 必须是有限非负数: " + orbitRadius);
        }
        if (!Float.isFinite(hoverHeight)) {
            throw new IllegalArgumentException("hoverHeight 必须是有限数: " + hoverHeight);
        }
        if (!Float.isFinite(stiffness) || stiffness <= 0.0F) {
            throw new IllegalArgumentException("stiffness 必须是有限正数: " + stiffness);
        }
        if (!Float.isFinite(bobAmplitude) || bobAmplitude < 0.0F) {
            throw new IllegalArgumentException("bobAmplitude 必须是有限非负数: " + bobAmplitude);
        }
        // 环绕半径本身就不该越过硬钳制距离，否则随从会一直贴着钳制边界抖动
        if (orbitRadius > MAX_DISTANCE) {
            throw new IllegalArgumentException("orbitRadius 不能超过 " + MAX_DISTANCE + ": " + orbitRadius);
        }
    }

    /** 内置类型的默认参数。 */
    public static PetMotionProfile defaults(Kind kind) {
        return kind == Kind.FLYER ? FLYER : WALKER;
    }

    public boolean isFlyer() {
        return kind == Kind.FLYER;
    }

    /** 在默认参数基础上只改环绕半径（供皮肤目录覆盖单项）。 */
    public PetMotionProfile withOrbitRadius(float radius) {
        return new PetMotionProfile(kind, radius, hoverHeight, stiffness, bobAmplitude);
    }

    /** 在默认参数基础上只改悬浮高度。 */
    public PetMotionProfile withHoverHeight(float height) {
        return new PetMotionProfile(kind, orbitRadius, height, stiffness, bobAmplitude);
    }
}
