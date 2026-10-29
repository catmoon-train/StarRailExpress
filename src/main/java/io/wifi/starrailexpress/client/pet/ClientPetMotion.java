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

package io.wifi.starrailexpress.client.pet;

import io.wifi.starrailexpress.pet.PetMotion;
import io.wifi.starrailexpress.pet.PetMotionProfile;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 客户端随从运动状态（每名玩家至多一个）。
 * <p>
 * 纯客户端推导：位置只依赖「主人当前坐标 + 身体朝向」，不接收任何位置包。
 * 每 tick 推进一次，渲染时按帧插值。
 * <p>
 * 运动策略：
 * <ul>
 * <li><b>待机</b>：主人静止或只小幅走动时，随从<b>原地不动</b>，玩家可以转身看着它；</li>
 * <li><b>追随</b>：一旦被主人落下超过
 * {@link PetMotion#FOLLOW_TRIGGER_DISTANCE} 格，才追到主人身后
 * （按 {@code bodyYaw} 反推背后点，不会跑到正面挡视线）；</li>
 * <li><b>陆行类</b>：追随过程中做地面吸附，永远踩在方块上；</li>
 * <li><b>飞行类</b>：浮在主人头部上方偏后，带轻微上下浮动；</li>
 * <li>两者都受 {@link PetMotionProfile#MAX_DISTANCE} 硬钳制——主人传送 / 换维度 /
 * 重生时随从不会留在原地，最迟 1 tick 就被拉回 5 格内。</li>
 * </ul>
 */
@Environment(EnvType.CLIENT)
public final class ClientPetMotion {

    /** 每名玩家的运动状态。 */
    /**
     * 皮肤 ID → 运动类型 的缓存。
     * <p>
     * 皮肤 ID 只有个位数种，而 {@link #kindOf} 在「每 tick × 每个可见随从」
     * 的路径上被调用；缓存后既省掉 {@code toLowerCase} 的字符串分配，
     * 也省掉一次 {@code contains} 扫描。
     */

    /** 地面采样器缓存（按 level 复用，避免每 tick 每随从新建 lambda）。 */
    private static PetMotion.GroundSampler GROUND_SAMPLER;
    private static ClientLevel GROUND_SAMPLER_LEVEL;
    private static final Map<String, PetMotionProfile.Kind> KIND_CACHE = new ConcurrentHashMap<>();
    private static final Map<UUID, State> STATES = new ConcurrentHashMap<>();

    private ClientPetMotion() {
    }

    /**
     * 每客户端 tick 推进一次。由 {@code PetClientDriver} 统一调用，
     * 内部只为「当前可见」的随从做计算，不可见的直接丢弃状态。
     */
    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            STATES.clear();
            return;
        }
        long tick = level.getGameTime();
        // 单次遍历同时完成「清理失效状态」与「推进可见随从」。
        // 早期实现先 removeIf 再循环，每个玩家每 tick 要算两遍
        // ClientPetCache.getVisibleSkinId（内部还有组件查询与配置读取），
        // 多人场景下这正好是两倍的无效开销。
        Set<UUID> alive = new HashSet<>(level.players().size() * 2);
        for (AbstractClientPlayer player : level.players()) {
            UUID uuid = player.getUUID();
            alive.add(uuid);
            String skinId = ClientPetCache.getVisibleSkinId(player);
            if (skinId.isBlank()) {
                // 不可见就丢弃状态，下次重新出现时从头平滑（避免从旧位置飞过来）
                STATES.remove(uuid);
                continue;
            }
            PetMotionProfile profile = PetMotionProfile.defaults(kindOf(skinId));
            State state = STATES.computeIfAbsent(uuid, id -> new State());
            advance(state, player, profile, level, tick);
        }
        // 清掉已经离开视野/退出游戏的残留状态（例如玩家被卸载出客户端世界）
        if (!STATES.isEmpty()) {
            STATES.keySet().retainAll(alive);
        }
    }

    /**
     * 取某玩家的随从渲染位置（已做帧间插值）。
     *
     * @return 长度为 3 的数组 {@code [x, y, z]}；无状态时返回 {@code null}
     */
    public static double[] getRenderPosition(AbstractClientPlayer player, float partialTick) {
        State state = player == null ? null : STATES.get(player.getUUID());
        if (state == null) {
            return null;
        }
        // 帧间线性插值：客户端 tick 20Hz，渲染帧率更高，不插值会明显抖动
        state.out[0] = state.prevX + (state.x - state.prevX) * partialTick;
        state.out[1] = state.prevY + (state.y - state.prevY) * partialTick;
        state.out[2] = state.prevZ + (state.z - state.prevZ) * partialTick;
        return state.out;
    }

    /** 取某玩家随从的朝向（度），同样做帧间插值。 */
    public static float getRenderYaw(AbstractClientPlayer player, float partialTick) {
        State state = player == null ? null : STATES.get(player.getUUID());
        if (state == null) {
            return 0.0F;
        }
        return PetMotion.lerpAngle(state.prevYaw, state.yaw, partialTick);
    }

    /** 取某玩家随从当前的运动强度（0~1），供行走动画混合。 */
    public static float getMovementIntensity(AbstractClientPlayer player) {
        State state = player == null ? null : STATES.get(player.getUUID());
        return state == null ? 0.0F : state.intensity;
    }

    /** 断开连接时清空。 */
    public static void clear() {
        STATES.clear();
    }

    /**
     * 由皮肤 ID 推断运动类型。
     * <p>
     * 皮肤 ID 里含 {@code fly} 视为飞行类，其余按陆行类处理；
     * 真正权威的类型来自 sre-skin 的目录，但这里只决定运动曲线，
     * 猜错的代价仅仅是「随从浮起来或贴地」，不影响正确性。
     * <p>
     * 结果按 ID 缓存：{@code toLowerCase} 每次都会新建字符串，而本方法在
     * 「每 tick × 每个可见随从」的路径上，缓存后彻底消除这份分配。
     */
    private static PetMotionProfile.Kind kindOf(String skinId) {
        PetMotionProfile.Kind cached = KIND_CACHE.get(skinId);
        if (cached != null) {
            return cached;
        }
        PetMotionProfile.Kind kind = skinId.toLowerCase(java.util.Locale.ROOT).contains("fly")
                ? PetMotionProfile.Kind.FLYER
                : PetMotionProfile.Kind.WALKER;
        KIND_CACHE.put(skinId, kind);
        return kind;
    }

    private static void advance(State state, AbstractClientPlayer owner, PetMotionProfile profile,
            ClientLevel level, long tick) {
        // 记录上一 tick 位置，供渲染插值
        state.prevX = state.x;
        state.prevY = state.y;
        state.prevZ = state.z;
        state.prevYaw = state.yaw;

        double ownerX = owner.getX();
        double ownerY = owner.getY();
        double ownerZ = owner.getZ();

        // 首次出现（或刚被清过状态）：直接落到目标位置，避免从原点飞过来
        if (!state.initialized) {
            state.initialized = true;
            state.x = ownerX;
            state.y = PetMotion.desiredY(profile, ownerY, owner.getBbHeight(), tick);
            state.z = ownerZ;
            state.prevX = state.x;
            state.prevY = state.y;
            state.prevZ = state.z;
            // 新出现的随从从静止开始，避免带着上一位主人的残余速度
            state.vel[0] = 0.0D;
            state.vel[1] = 0.0D;
            state.vel[2] = 0.0D;
        }
        // 期望位置：
        // 一旦被落下超过触发距离，目标点就是主人身后 orbitRadius 处。
        // 注意：这里只给出「目标点」，真正的位移由 PetMotion.followStep 以
        // 有上限的速度积分出来——不再用指数平滑直接改坐标，否则距离越远
        // 单 tick 位移越大（18%/tick × 远距离），视觉上就是闪现。
        double currentDistanceSqr = PetMotion.horizontalDistanceSqr(state.x, state.z, ownerX, ownerZ);
        boolean chasing = PetMotion.shouldStartFollowing(currentDistanceSqr);
        double targetX;
        double targetZ;
        if (chasing) {
            double[] offset = state.offset;
            PetMotion.offsetFromAngle(PetMotion.behindAngle(owner.yBodyRot), profile.orbitRadius(), offset);
            targetX = ownerX + offset[0];
            targetZ = ownerZ + offset[1];
        } else {
            // 未超出范围：目标点就是当前位置，随从待在原地
            targetX = state.x;
            targetZ = state.z;
        }
        double targetY = PetMotion.desiredY(profile, ownerY, owner.getBbHeight(), tick);

        // 速度限幅的平滑追随（20 TPS → dt = 0.05s）
        // 参考原版实体：位移有上限才是「平滑移动」，帧间插值只负责抹平 20Hz 台阶。
        float dt = 0.05F;
        double maxSpeed = profile.isFlyer() ? PetMotion.FLY_MAX_SPEED : PetMotion.WALK_MAX_SPEED;
        state.pos[0] = state.x;
        state.pos[1] = state.y;
        state.pos[2] = state.z;
        PetMotion.followStep(state.pos, state.vel, targetX, targetY, targetZ,
                maxSpeed, PetMotion.FOLLOW_ACCELERATION, dt);
        double newX = state.pos[0];
        double newY = state.pos[1];
        double newZ = state.pos[2];
        if (!profile.isFlyer()) {
            // 陆行类：贴地。snapToGround 只会把 Y 拉到该列地面高度，
            // 幅度最多 GROUND_SCAN_DEPTH 格；配合速度上限不会产生跳跃。
            newY = PetMotion.snapToGround(groundSampler(level), newX, newY, newZ, targetY);
        }

        state.x = newX;
        state.y = newY;
        state.z = newZ;

        // 唯一的强制瞬移：只有超过 TELEPORT_THRESHOLD（30 格）才认为发生了
        // 传送 / 换维度 / 重生这类异常。5~30 格之间全部交给上面的速度积分，
        // 绝不能在正常追随路径上钳制位置，否则就是「闪现」。
        state.out[0] = state.x;
        state.out[1] = state.y;
        state.out[2] = state.z;
        if (PetMotion.exceedsTeleportThreshold(state.out, ownerX, ownerY, ownerZ)) {
            PetMotion.teleportBehindOwner(state.out, owner.yBodyRot, ownerX, ownerY, ownerZ,
                    profile.orbitRadius(), state.offset);
            state.x = state.out[0];
            state.y = state.out[1];
            state.z = state.out[2];
            // 瞬移时同步上一帧位置，避免渲染端插值出一条横穿屏幕的长线
            state.prevX = state.x;
            state.prevY = state.y;
            state.prevZ = state.z;
            // 速度必须一起清零：否则瞬移后残余速度会把随从又带出去
            state.vel[0] = 0.0D;
            state.vel[1] = 0.0D;
            state.vel[2] = 0.0D;
        }

        // 朝向：朝运动方向；几乎静止时保持上一帧朝向，避免抽搐
        double dx = state.x - state.prevX;
        double dz = state.z - state.prevZ;
        double movedSqr = dx * dx + dz * dz;
        if (movedSqr > 1.0E-5D) {
            state.yaw = PetMotion.lerpAngle(state.yaw, PetMotion.yawFromDelta(dx, dz), 0.35F);
        }
        // 运动强度用于动画混合：走动快时摆腿幅度大
        state.intensity = (float) Math.min(1.0D, Math.sqrt(movedSqr) / 0.12D);
    }

    /**
     * 地面查询：返回该列「可站立面的 Y」（方块顶面），否则 {@code null}。
     * <p>
     * 只认有碰撞体积的方块；水 / 空气 / 草等无碰撞方块视为「没有地面」，
     * 于是随从会停在上一格高度，不会沉进水里。
     */
    private static PetMotion.GroundSampler groundSampler(ClientLevel level) {
        // 复用：level 未变时直接返回上一次的采样器。
        // 早期实现每次调用都新建一个 lambda 实例，而这条路径在
        // 「每 tick × 每个陆行随从」上，纯属无意义的分配。
        PetMotion.GroundSampler cached = GROUND_SAMPLER;
        if (cached != null && GROUND_SAMPLER_LEVEL == level) {
            return cached;
        }
        PetMotion.GroundSampler sampler = (x, y, z) -> {
            BlockPos pos = BlockPos.containing(x, y, z);
            BlockState state = level.getBlockState(pos);
            VoxelShape shape = state.getCollisionShape(level, pos);
            if (shape.isEmpty()) {
                return null;
            }
            // 只有「站在方块顶面上」才算地面：顶面高度 + 1 是实体的落脚 Y
            double top = pos.getY() + shape.max(net.minecraft.core.Direction.Axis.Y);
            // 允许一点点容差，避免 0.9999 之类的浮点误差导致来回抖
            if (top > y + 1.0D + 1.0E-3D) {
                return null;
            }
            return top;
        };
        GROUND_SAMPLER = sampler;
        GROUND_SAMPLER_LEVEL = level;
        return sampler;
    }

    /** 单名玩家的运动状态。 */
    private static final class State {
        double x;
        double y;
        double z;
        double prevX;
        double prevY;
        double prevZ;
        float yaw;
        float prevYaw;
        float intensity;
        boolean initialized;
        /** 复用同一个数组返回，避免每帧分配。 */
        final double[] out = new double[3];
        /** 复用同一个数组承载「背后偏移」，避免每 tick 分配。 */
        final double[] offset = new double[2];
        /** 位置暂存，供 {@link PetMotion#followStep} 就地积分，避免每 tick 分配。 */
        final double[] pos = new double[3];
        /** 当前速度（格/秒），速度限幅平滑追随的状态量。 */
        final double[] vel = new double[3];
    }
}

