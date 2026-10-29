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

import java.util.Map;
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
        // 先把不再可见的随从状态清掉（卸下 / 取消公开 / 开局）
        STATES.keySet().removeIf(uuid -> {
            AbstractClientPlayer player = findPlayer(minecraft, uuid);
            return player == null || ClientPetCache.getVisibleSkinId(player).isBlank();
        });
        for (AbstractClientPlayer player : level.players()) {
            String skinId = ClientPetCache.getVisibleSkinId(player);
            if (skinId.isBlank()) {
                continue;
            }
            PetMotionProfile profile = PetMotionProfile.defaults(kindOf(skinId));
            State state = STATES.computeIfAbsent(player.getUUID(), id -> new State());
            advance(state, player, profile, level, tick);
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
     */
    private static PetMotionProfile.Kind kindOf(String skinId) {
        return skinId.toLowerCase(java.util.Locale.ROOT).contains("fly")
                ? PetMotionProfile.Kind.FLYER
                : PetMotionProfile.Kind.WALKER;
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
        }

        // 期望位置：默认「原地不动」，只有被主人落下超过 5 格时才追到身后。
        // 这样玩家静止、或小幅走动时可以转身看着它，随从不会跟着甩来甩去。
        // 用 bodyYaw 而非 headYaw：玩家转头看四周时随从不应跟着移动。
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

        // 指数平滑逼近（20 TPS → dt = 0.05s）
        float dt = 0.05F;
        double newX = PetMotion.approach(state.x, targetX, profile.stiffness(), dt);
        double newZ = PetMotion.approach(state.z, targetZ, profile.stiffness(), dt);
        double newY;
        if (profile.isFlyer()) {
            newY = PetMotion.approach(state.y, targetY, profile.stiffness(), dt);
        } else {
            // 陆行类：先平滑靠近期望高度，再吸附到实际地面，保证踩在方块上
            double smoothed = PetMotion.approach(state.y, targetY, profile.stiffness(), dt);
            newY = PetMotion.snapToGround(groundSampler(level), newX, smoothed, newZ, targetY);
        }

        state.x = newX;
        state.y = newY;
        state.z = newZ;

        // 硬钳制只在「真正异常」时兜底（传送 / 换维度 / 重生）：
        // 正常追随由上面的指数平滑完成，不能每 tick 把位置直接拉到边界，
        // 否则表现为「瞬移」。这里的阈值取得比跟随触发距离更宽，
        // 只有明显超出（例如主人瞬移 30 格）才触发。
        state.out[0] = state.x;
        state.out[1] = state.y;
        state.out[2] = state.z;
        if (PetMotion.exceedsTeleportThreshold(state.out, ownerX, ownerY, ownerZ)) {
            PetMotion.clampToOwner(state.out, ownerX, ownerY, ownerZ);
            state.x = state.out[0];
            state.y = state.out[1];
            state.z = state.out[2];
            // 传送兜底时同步上一帧位置，避免渲染端插值出一条长直线
            state.prevX = state.x;
            state.prevY = state.y;
            state.prevZ = state.z;
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
        return (x, y, z) -> {
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
    }

    private static AbstractClientPlayer findPlayer(Minecraft minecraft, UUID uuid) {
        if (minecraft.level == null) {
            return null;
        }
        for (AbstractClientPlayer player : minecraft.level.players()) {
            if (player.getUUID().equals(uuid)) {
                return player;
            }
        }
        return null;
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
    }
}

