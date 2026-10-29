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
     * 追随的<b>最大速度</b>（格/秒）。
     * <p>
     * 参考玩家原版速度：步行约 {@code 4.317} 格/秒、疾跑约 {@code 5.612} 格/秒。
     * 随从取 {@code 5.0}——比步行略快，才能追上一直走的主人；
     * 但<b>这个上限只有在距离足够远时才会达到</b>，见 {@link #ARRIVE_DAMPING}。
     * <p>
     * 早期实现没有速度上限，只用指数平滑（{@code stiffness = 4}）逼近目标：
     * 从 5 格外起，第一 tick 就走掉 18% 的距离 ≈ 0.9 格，
     * 换算成每秒约 18 格——是玩家步行的 4 倍，视觉上就是「闪现」。
     */
    public static final double WALK_MAX_SPEED = 5.0D;

    /** 飞行类随从的最大速度（格/秒）：略快于陆行，符合「飘着追」的手感。 */
    public static final double FLY_MAX_SPEED = 6.0D;

    /**
     * 追随加速度（格/秒²）。
     * <p>
     * 只限速度不限加速度，随从会在 1 tick 内从静止变成满速——看起来像被弹射。
     * 取 {@code 6.0}：从静止到 5 格/秒需要约 {@code 0.83} 秒，
     * 起步过程肉眼可见，符合「小跑跟上来」的手感。
     * <p>
     * 早期取 {@code 16}，0.31 秒就加到满速，几乎等于没有加速过程。
     */
    public static final double FOLLOW_ACCELERATION = 6.0D;

    /** 到达目标点的停止半径（格）：进入该范围就把期望速度归零，避免在目标点反复抖动。 */
    public static final double ARRIVE_RADIUS = 0.25D;

    /**
     * 距离→速度的换算系数：期望速度 = {@code min(maxSpeed, 距离 × 该系数)}。
     * <p>
     * <b>这是「不再瞬移」的关键。</b>取 {@code 1.2} 的含义是：
     * 距离每多 1 格，速度上限只提高 1.2 格/秒。于是——
     * <ul>
     * <li>差 1 格（刚越过 5 格触发距离）：速度上限仅 {@code 1.2} 格/秒，
     *     约为玩家步行的 28%，肉眼是「慢慢走过来」；</li>
     * <li>差 5 格：速度上限 {@code 6.0}，被 {@code maxSpeed} 截到 5.0，
     *     比步行略快，能追上；</li>
     * <li>差 10 格以上：恒定 {@code maxSpeed}，不会越远越快。</li>
     * </ul>
     * <p>
     * <b>早期取 {@code 4.0}</b>，导致差 1 格时速度上限就有 {@code 4.0} 格/秒
     * （接近步行全速），0.3 秒就到位——这正是用户反馈的「6 格距离只差 1 格，
     * 却像瞬间移动」的成因。
     * <p>
     * 没有这一项，随从会以满速冲过目标点再折返，表现为「来回抽动」。
     */
    private static final double ARRIVE_DAMPING = 1.2D;

    /**
     * 速度限幅的追随一步（就地修改 {@code pos} 与 {@code vel}）。
     * <p>
     * 这是「平滑移动」的核心：<b>先按距离算期望速度，再限加速度，最后才积分位置</b>。
     * 与指数平滑（{@link #approach}）的区别是位移天然有上限，
     * 不会因为距离远就产生一次巨大的跳跃。
     *
     * @param pos      当前位置 {@code [x, y, z]}，会被改写
     * @param vel      当前速度 {@code [vx, vy, vz]}（格/秒），会被改写
     * @param maxSpeed 本步允许的最大速度（格/秒）
     * @param accel    本步允许的最大加速度（格/秒²）
     * @param dt       步长（秒）
     */
    public static void followStep(double[] pos, double[] vel, double targetX, double targetY, double targetZ,
            double maxSpeed, double accel, double dt) {
        if (dt <= 0.0D) {
            return;
        }
        double dx = targetX - pos[0];
        double dy = targetY - pos[1];
        double dz = targetZ - pos[2];
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

        // 期望速度：够近就停，否则朝目标、按距离阻尼降速、再受 maxSpeed 上限
        double desiredX = 0.0D;
        double desiredY = 0.0D;
        double desiredZ = 0.0D;
        if (distance > ARRIVE_RADIUS) {
            double speed = Math.min(maxSpeed, distance * ARRIVE_DAMPING);
            double inv = speed / distance;
            desiredX = dx * inv;
            desiredY = dy * inv;
            desiredZ = dz * inv;
        }

        // 限加速度：速度只能朝期望值「挪」有限的一小步
        double maxDelta = accel * dt;
        vel[0] = approachClamped(vel[0], desiredX, maxDelta);
        vel[1] = approachClamped(vel[1], desiredY, maxDelta);
        vel[2] = approachClamped(vel[2], desiredZ, maxDelta);

        // 积分位置（速度单位是格/秒，dt 是秒）
        pos[0] += vel[0] * dt;
        pos[1] += vel[1] * dt;
        pos[2] += vel[2] * dt;
    }

    /** 把 {@code current} 朝 {@code target} 移动，单步不超过 {@code maxDelta}。 */
    private static double approachClamped(double current, double target, double maxDelta) {
        double delta = target - current;
        if (delta > maxDelta) {
            return current + maxDelta;
        }
        if (delta < -maxDelta) {
            return current - maxDelta;
        }
        return target;
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
     * 由「方向角」求水平偏移量（单位：格）。
     * <p>
     * <b>不要用 {@code cos/sin(angle)} 直接算：</b>Minecraft 的 yaw 与数学极角不同源。
     * 实体 yaw=0 朝 +Z（南），yaw=90 朝 -X（西），因此前方向量是
     * {@code (-sin(yaw), cos(yaw))}，而不是 {@code (cos, sin)}。
     * 早期版本直接用极角公式，会让「身后」偏移落到侧面。
     *
     * @param angle 方向角（度），与实体 yaw 同一约定
     * @param out   长度 ≥ 2 的输出数组，写回 {@code [dx, dz]}，不分配对象
     */
    public static void offsetFromAngle(float angle, float radius, double[] out) {
        double radians = Math.toRadians(angle);
        out[0] = -Math.sin(radians) * radius;
        out[1] = Math.cos(radians) * radius;
    }

    /**
     * 随从相对主人的「背后偏移」方向角（度）。
     * <p>
     * 用主人身体朝向 {@code bodyYaw} 反推出背后方向，而不是让随从绕圈：
     * 用户要求随从<b>始终跟在身后</b>，绕圈会让它跑到正面挡住视线。
     * <p>
     * 坐标系与 Minecraft 一致：yaw=0 时玩家正面朝 +Z，此时背后是 -Z，
     * 对应方向角 {@code bodyYaw + 180}。
     *
     * @param bodyYaw 主人身体朝向（度）
     */
    public static float behindAngle(float bodyYaw) {
        return wrapDegrees(bodyYaw + 180.0F);
    }

    /**
     * 随从开始「追主人」的触发距离（格）。
     * <p>
     * 用户要的语义不是「永远贴在背后」，而是<b>留有余量</b>：
     * 玩家静止、或只是小幅走动时，随从原地待着（玩家可以转身看着它）；
     * 只有当它被落下超过这个距离，才开始追到主人身后。
     */
    public static final double FOLLOW_TRIGGER_DISTANCE = 5.0D;

    /**
     * 判定随从是否需要开始追随。
     *
     * @param distanceSqr 随从与主人的水平距离平方
     */
    public static boolean shouldStartFollowing(double distanceSqr) {
        return distanceSqr > FOLLOW_TRIGGER_DISTANCE * FOLLOW_TRIGGER_DISTANCE;
    }
    /**
     * 「强制瞬移」的判定阈值（格）：超过这个距离才认为发生了传送 / 换维度 /
     * 重生这类异常，直接把随从拉到主人身边。
     * <p>
     * 用户要求：<b>5 格内平滑跟随，只有超过 30 格才强制瞬移。</b>
     * 因此这个值必须显著大于 {@link #FOLLOW_TRIGGER_DISTANCE}——两者之间的
     * 整个区间都交给指数平滑跑过去，不能一超阈值就把位置拉回边界，
     * 否则视觉上就是「闪现」。
     */
    public static final double TELEPORT_THRESHOLD = 30.0D;

    /**
     * 是否已经远到「只能是传送导致的」，需要强制瞬移。
     *
     * @param pos 随从位置 {@code [x, y, z]}
     */
    public static boolean exceedsTeleportThreshold(double[] pos, double ownerX, double ownerY, double ownerZ) {
        double dx = pos[0] - ownerX;
        double dy = pos[1] - ownerY;
        double dz = pos[2] - ownerZ;
        return dx * dx + dy * dy + dz * dz > TELEPORT_THRESHOLD * TELEPORT_THRESHOLD;
    }

    /**
     * 强制瞬移：把随从直接放到主人身后 {@code radius} 格处。
     * <p>
     * 与 {@link #clampToOwner} 的区别：钳制是把位置「拉回到某个半径的球面上」，
     * 保留原本方向（可能仍在半空、或落在主人正前方）；瞬移则按主人身体朝向
     * 放到背后，语义上等价于「重新出现在该出现的位置」。
     * <p>
     * 只在 {@link #exceedsTeleportThreshold} 成立时调用。
     *
     * @param out    长度 ≥ 3 的输出数组，写回 {@code [x, y, z]}
     * @param radius 落在主人身后的水平距离（格）
     */
    public static void teleportBehindOwner(double[] out, float bodyYaw, double ownerX, double ownerY, double ownerZ,
            double radius, double[] scratch) {
        offsetFromAngle(behindAngle(bodyYaw), (float) radius, scratch);
        out[0] = ownerX + scratch[0];
        out[1] = ownerY;
        out[2] = ownerZ + scratch[1];
    }

    /**
     * 期望位置的 Y 分量。
     *
     * @param ownerY       主人脚部 Y
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
     * 硬钳制：把随从位置拉回主人 {@code maxDistance} 格内（保留方向）。
     * <p>
     * <b>正常追随不该用它。</b>追随由指数平滑完成；这里只作为
     * 「位置数据明显不合理」时的兜底。半径由调用方传入，
     * 不再固定取 {@link PetMotionProfile#MAX_DISTANCE}——
     * 那个 5 格是「环绕半径的合法上限」，不是「随从允许离主人多远」。
     *
     * @return 是否发生了钳制
     */
    public static boolean clampToOwner(double[] out, double ownerX, double ownerY, double ownerZ,
            double maxDistance) {
        double dx = out[0] - ownerX;
        double dy = out[1] - ownerY;
        double dz = out[2] - ownerZ;
        double distanceSqr = dx * dx + dy * dy + dz * dz;
        if (distanceSqr <= maxDistance * maxDistance) {
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
        double scale = maxDistance / distance;
        out[0] = ownerX + dx * scale;
        out[1] = ownerY + dy * scale;
        out[2] = ownerZ + dz * scale;
        return true;
    }

    /**
     * 兼容旧签名的钳制：固定使用 {@link PetMotionProfile#MAX_DISTANCE}。
     *
     * @deprecated 正常追随路径请改用 {@link #teleportBehindOwner}；
     *             这个重载只服务于单元测试与历史调用点。
     */
    @Deprecated
    public static boolean clampToOwner(double[] out, double ownerX, double ownerY, double ownerZ) {
        return clampToOwner(out, ownerX, ownerY, ownerZ, PetMotionProfile.MAX_DISTANCE);
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
