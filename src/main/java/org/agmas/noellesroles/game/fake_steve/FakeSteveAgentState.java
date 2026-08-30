package org.agmas.noellesroles.game.fake_steve;

import java.util.UUID;
import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.Map;

import net.minecraft.core.BlockPos;
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

    FakeSteveAgentState(UUID playerId, ReplacementCause cause) {
        this.playerId = playerId;
        this.cause = cause;
    }
}
