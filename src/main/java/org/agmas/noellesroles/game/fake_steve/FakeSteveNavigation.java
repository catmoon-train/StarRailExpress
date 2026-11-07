package org.agmas.noellesroles.game.fake_steve;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** 唯一的路线执行器；巡逻、任务、逃跑和追踪不能各自再模拟一份移动。 */
final class FakeSteveNavigation {
    private FakeSteveNavigation() {
    }

    static void follow(ServerLevel level, ServerPlayer body, BlockPos goal,
            FakeSteveAgentState state, double speed) {
        long now = level.getGameTime();
        boolean pursuit = state.mode == AgentMode.HUNT || state.mode == AgentMode.STALK
                || state.mode == AgentMode.ASSIMILATE;
        BlockPos destination = FakeSteveNavigator.normalize(level, goal);
        state.pathGoal = destination;
        Vec3 end = FakeSteveNavigator.point(level, destination);
        Vec3 remaining = end.subtract(body.position());
        if (FakeSteveTraversalPolicy.reached(remaining.x, remaining.y, remaining.z)) {
            state.path.clear();
            state.pathReached = true;
            state.pathFailureCount = 0;
            hold(body, state);
            return;
        }
        boolean changed = state.plannedGoal == null || (pursuit
                ? state.plannedGoal.distSqr(destination) > 4.0D
                        || state.plannedGoal.getY() != destination.getY()
                : !state.plannedGoal.equals(destination));
        BlockPos next = state.path.peekFirst();
        boolean invalid = next != null && (!FakeSteveNavigator.standable(level, next)
                || body.distanceToSqr(FakeSteveNavigator.point(level, next)) > 36.0D);
        if (FakeSteveTraversalPolicy.repath(state.path.isEmpty(), invalid, changed, now, state.nextPathTick)) {
            state.path.clear();
            FakeSteveNavigator.Result result =
                    FakeSteveNavigator.search(level, body, destination, pursuit);
            state.path.addAll(result.path());
            // 记录「是否真的到达」：false 时 path 只是离目标最近的一段，不能当成已到达。
            state.pathReached = result.reached();
            state.pathSearchVisited = result.visited();
            state.plannedGoal = destination.immutable();
            state.nextPathTick = now + (pursuit ? 12L : 30L);
            invalid = false;
            if (state.path.isEmpty()) fail(body, state, now);
        }
        if (state.path.isEmpty() || invalid && now < state.nextPathTick) {
            hold(body, state);
            return;
        }
        // 到达判定基于实际脚底坐标，而不是 blockPosition 的整格距离。
        // 楼梯半高和电梯垂直节点也必须真的走到，不能提前弹出。
        while ((next = state.path.peekFirst()) != null) {
            FakeSteveAi.openDoorsOnApproach(level, body, next);
            Vec3 delta = FakeSteveNavigator.point(level, next).subtract(body.position());
            if (!FakeSteveTraversalPolicy.reached(delta.x, delta.y, delta.z)) break;
            state.path.removeFirst();
            state.navigationNode = null;
            state.pathFailureCount = 0;
        }
        if (next == null) {
            hold(body, state);
            return;
        }
        Vec3 target = FakeSteveNavigator.point(level, next);
        Vec3 delta = target.subtract(body.position());
        double distance = delta.lengthSqr();
        if (state.lastMoveTick == 0L || body.distanceToSqr(state.lastMoveX, state.lastMoveY, state.lastMoveZ) > 0.04D) {
            state.lastMoveTick = now;
            state.lastMoveX = body.getX();
            state.lastMoveY = body.getY();
            state.lastMoveZ = body.getZ();
        } else if (now - state.lastMoveTick >= 60L) {
            state.path.clear();
            state.nextPathTick = now + 5;
            state.lastMoveTick = now;
            fail(body, state, now);
            hold(body, state);
            return;
        }
        if (!next.equals(state.navigationNode)) {
            state.navigationNode = next;
            state.navigationProgressTick = now;
            state.navigationBestDistance = distance;
        } else if (distance < state.navigationBestDistance - 0.035D) {
            state.navigationBestDistance = distance;
            state.navigationProgressTick = now;
        } else if (now - state.navigationProgressTick >= 35L) {
            state.path.clear();
            state.nextPathTick = now + 5L;
            fail(body, state, now);
            hold(body, state);
            return;
        }
        // 跳跃与水中上浮共用同一个按键，必须服从同一份地图约束（FakeSteveNavRules）：
        // 禁跳地图不按跳跃键；禁游地图不为了浮起来按跳跃键（否则会一直保持在水面，
        // 眼睛不入水，CANNOT_SWIM 的环境判死永远不触发）。
        FakeSteveNavRules rules = FakeSteveNavRules.of(level, body);
        boolean wet = body.isInWater() || body.isUnderWater();
        boolean climbing = body.onClimbable();
        int lift = FakeSteveNavigator.lift(level, body.blockPosition());
        boolean jump = wet && rules.canSwim()
                && FakeSteveTraversalPolicy.swimUp(lift, body.getY(), target.y);
        boolean crouch = wet && FakeSteveTraversalPolicy.swimDown(lift, body.getY(), target.y);
        if (!wet && !climbing && delta.y > 0.6D && body.onGround()
                && rules.canJump() && now >= state.nextJumpTick) {
            jump = true;
            state.nextJumpTick = now + 10L;
        }
        if (climbing) jump = delta.y > 0.2D;
        double horizontal = delta.horizontalDistance();
        if (horizontal < 0.12D && wet) {
            // 气泡柱只居中并交给原版浮力，不按住跳跃对抗下降柱。
            FakeSteveMotionController.drive(body, state, 0, 0, jump, false, crouch,
                    body.getYRot(), 0, next);
            return;
        }
        Vec3 direction = horizontal > 0.001D
                ? new Vec3(delta.x / horizontal, 0, delta.z / horizontal)
                : Vec3.directionFromRotation(0, body.getYRot());
        if (!wet && !FakeSteveNavigator.corridor(level, body.position(), target)) {
            state.path.clear();
            state.nextPathTick = Math.min(state.nextPathTick, now + 5L);
            hold(body, state);
            return;
        }
        List<FakeSteveCrowdAvoidance.NearbyPlayer> players = level.players().stream()
                .filter(p -> p != body && p.isAlive() && !p.isSpectator())
                .filter(p -> Math.abs(p.getY() - body.getY()) < 1.7D && p.distanceToSqr(body) < 16.0D)
                .map(p -> new FakeSteveCrowdAvoidance.NearbyPlayer(p.getX(), p.getZ())).toList();
        var avoidance = FakeSteveCrowdAvoidance.decide(body.getX(), body.getZ(), target.x, target.z,
                players, state.crowdedTicks);
        float strafe = 0;
        float forward = 1;
        if (avoidance.crowded()) {
            state.crowdedTicks += state.tickStep;
            if (state.crowdStrafe == 0) state.crowdStrafe = avoidance.strafe();
            strafe = state.crowdStrafe;
            if (!laneClear(level, body, direction, strafe, players)) {
                if (laneClear(level, body, direction, -strafe, players)) {
                    strafe = -strafe;
                    state.crowdStrafe = strafe;
                } else strafe = 0;
            }
            forward = strafe == 0 ? 0 : avoidance.forwardScale();
            if (avoidance.shouldRepath()) {
                state.path.clear();
                state.nextPathTick = Math.min(state.nextPathTick, now + 4L + Math.floorMod(body.getId(), 7));
                state.crowdedTicks = 0;
            }
            if (strafe == 0 && state.crowdedTicks > 12) {
                // 狭道无侧向空间时先后退让路，绝不继续挤进对方身体。
                if (FakeSteveNavigator.stepSafe(level, body.position(), direction.scale(-0.7D))) forward = -0.35F;
            }
        } else {
            state.crowdedTicks = 0;
            state.crowdStrafe = 0;
        }
        float yaw = FakeSteveMotionPolicy.yawTo(body.getX(), body.getZ(), target.x, target.z);
        if (horizontal < 0.12D) yaw = body.getYRot();
        state.stableRouteYaw = yaw;
        state.hasStableRouteYaw = true;
        float turn = Math.abs(net.minecraft.util.Mth.wrapDegrees(yaw - body.getYRot()));
        if (turn > 65.0F) forward *= 0.3F;
        if (horizontal < 0.7D && !wet && !climbing) forward *= 0.8F;
        double worldX = direction.x * forward + direction.z * strafe;
        double worldZ = direction.z * forward - direction.x * strafe;
        var local = FakeSteveMotionPolicy.toLocal(yaw, worldX, worldZ);
        boolean sprint = !avoidance.crowded() && !wet && turn < 35.0F
                && (pursuit || speed >= 0.22D || now < state.sprintUntilTick);
        float pitch = wet ? (target.y < body.getY() - 0.3D ? 65.0F : target.y > body.getY() + 0.3D ? -55.0F : 0)
                : FakeSteveMotionPolicy.walkingPitch(now, body.getUUID().hashCode(), true);
        FakeSteveMotionController.drive(body, state, local.forward(), local.strafe(), jump, sprint,
                crouch, yaw, pitch, next);
    }

    private static boolean laneClear(ServerLevel level, ServerPlayer body, Vec3 direction, float strafe,
            List<FakeSteveCrowdAvoidance.NearbyPlayer> players) {
        Vec3 offset = new Vec3(direction.z, 0, -direction.x).scale(Math.copySign(0.75D, strafe));
        Vec3 side = body.position().add(offset);
        if (!FakeSteveNavigator.stepSafe(level, body.position(), offset)
                || !level.noCollision(body, body.getBoundingBox().move(offset))) return false;
        return players.stream().noneMatch(p -> {
            double dx = p.x() - side.x, dz = p.z() - side.z;
            return dx * dx + dz * dz < 0.65D * 0.65D;
        });
    }

    private static void fail(ServerPlayer body, FakeSteveAgentState state, long now) {
        state.pathFailureCount++;
        if (state.pathFailureCount < 3) return;
        // 只暂时跳过本次不可达的追踪对象。
        state.unreachableTarget = state.committedTarget != null ? state.committedTarget : state.focusTarget;
        state.unreachableTargetUntil = now + 160L;
        state.committedTarget = null;
        state.focusTarget = null;
        state.pendingEngagement = false;
        state.brain.disengage();
        if (state.pathGoal != null) state.unreachableGoals.put(state.pathGoal.immutable(), now + 200L);
        if (state.taskType != null) {
            state.taskBackoffUntil.put(state.taskType, now + 200L);
            FakeSteveTaskPlanner.abandon(body, state);
        }
        state.pathGoal = null;
        state.plannedGoal = null;
        state.nextDecisionTick = now;
        state.patrolUntilTick = 0;
    }

    private static void hold(ServerPlayer body, FakeSteveAgentState state) {
        FakeSteveMotionController.hold(body, state, body.getYRot(), body.getXRot());
    }
}
