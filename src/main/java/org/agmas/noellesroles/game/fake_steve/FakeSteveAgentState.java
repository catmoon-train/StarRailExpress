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

package org.agmas.noellesroles.game.fake_steve;

import java.util.UUID;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import io.wifi.starrailexpress.cca.SREPlayerTaskComponent;

public final class FakeSteveAgentState {
    public final UUID playerId;
    public final ReplacementCause cause;
    public AgentMode mode = AgentMode.DISGUISE_IDLE;
    public final FakeSteveBrain brain = new FakeSteveBrain();
    public long modeStartedTick;
    public long lastTickAt;
    public int tickStep = 5;
    public UUID focusTarget;
    /** Committed prey: stops the body from flip-flopping between two humans. */
    public UUID committedTarget;
    public long committedUntilTick;
    /** Cached ambush position so a turning target cannot make the body spin. */
    public BlockPos ambushGoal;
    public UUID ambushTarget;
    public long ambushGoalTick;
    public boolean pendingEngagement;
    public int faceTicks;
    public int assimilationTicks;
    public long nextDecisionTick;
    public long nextPathTick;
    public long pathRetryAfterTick;
    public long nextJumpTick;
    public long lastPathProgressTick;
    public double lastPathDistanceSqr = Double.MAX_VALUE;
    public int pathFailureCount;
    public int stuckTicks;
    public double lastMoveX;
    public double lastMoveY;
    public double lastMoveZ;
    public long lastMoveTick;
    public BlockPos pathGoal;
    public BlockPos lastWanderGoal;
    public final ArrayDeque<BlockPos> path = new ArrayDeque<>();
    public long motionSequence;
    public FakeSteveMotionPolicy.Lease motionLease;
    public boolean motionSprint;
    public boolean motionCrouch;
    public int rejectedMotionPackets;
    // Server-authoritative movement intentions, refreshed by drive()/hold()/clear().
    public boolean moveActive;
    public long moveExpiresAtTick;
    public float moveForward;
    public float moveStrafe;
    public boolean moveJump;
    public boolean moveSprint;
    public boolean moveCrouch;
    public float moveYaw;
    public float movePitch;
    public SREPlayerTaskComponent.Task taskType;
    public BlockPos taskGoal;
    public BlockPos taskInteractTarget;
    public long taskStartedTick;
    public final Map<SREPlayerTaskComponent.Task, Long> taskBackoffUntil =
            new EnumMap<>(SREPlayerTaskComponent.Task.class);
    public long taskRetryTick;
    public long nextTaskInteractionTick;
    public float stableRouteYaw;
    public boolean hasStableRouteYaw;
    public int crowdedTicks;
    public float crowdStrafe;
    public int idleTicks;
    public long sprintUntilTick;
    public long nextDialogueTick;
    public boolean directedReplyPending;
    public long nextShopTick;
    public long nextTacticalItemTick;
    public long nextSkillTick;
    public UUID knifeChargeTarget;
    public long knifeChargedAtTick;
    public long knifeChargeStartedTick;
    public long holsterAtTick;
    public int holsterSlot = -1;
    public UUID grenadeChargeTarget;
    public long grenadeChargedAtTick;
    public long nextSnackTick;
    public BlockPos lastSnackPlate;
    public boolean taskConsumeStarted;

    // ==================== 生前移动轨迹（巡逻路线） ====================
    /**
     * 被替换玩家「生前」走出去的路线（由 {@link FakeSteveTrailRecorder} 记录）。
     * 空闲时优先沿它巡逻：不需要 A*，也就不会寻路失败卡在原地打转。
     */
    public final List<BlockPos> trail = new ArrayList<>();
    /** 轨迹点是否足够多、可以当巡逻路线用。 */
    public boolean trailEnabled;
    /** 当前目标轨迹点下标。 */
    public int trailIndex;
    /** 巡逻方向：+1 向后、-1 向前；到达端点后折返，实现「重复此行为」。 */
    public int trailDirection = 1;
    /** 当前轨迹点开始尝试的时间（tick），用于卡住时跳过该点。 */
    public long trailWaypointTick;
    /**
     * 上一次「确实取得进展」时身体所在的位置。
     * 用身体的实际位移判断有没有被卡住，而不是「到路点的距离」——贴墙时的位置抖动会把后者反复刷成有进展。
     */
    public double trailRefX;
    public double trailRefZ;
    /** {@link #trailRefX} / {@link #trailRefZ} 的时间戳；0 表示尚未初始化。 */
    public long trailRefTick;
    /** 上一次真正取得进展的时间（tick）。 */
    public long trailProgressTick;
    /** 掉头冷却结束时间（tick）：防止在两个方向之间来回抖。 */
    public long trailReverseCooldownUntilTick;
    /**
     * 撞墙后「重新找可抵达记录点」的冷却结束时间（tick）。
     * 一个可用的记录点都找不到时用它兜底，避免每 tick 都去扫一遍路线。
     */
    public long trailRescanCooldownUntilTick;
    /** 已完成的折返次数（调试用）。 */
    public int trailLoops;
    /** 记录这条路线时身体所在的维度：保底传送前用它确认坐标还对得上。 */
    public ResourceKey<Level> trailDimension;
    /** 卡死保底锚点：身体没离开该点 {@code STUCK_RADIUS} 格时保持不动，用来给「原地卡死」计时。 */
    public double stuckAnchorX;
    public double stuckAnchorZ;
    /** {@link #stuckAnchorX} 的起始时间（tick）；0 表示尚未初始化。 */
    public long stuckAnchorTick;

    FakeSteveAgentState(UUID playerId, ReplacementCause cause) {
        this.playerId = playerId;
        this.cause = cause;
    }
}
