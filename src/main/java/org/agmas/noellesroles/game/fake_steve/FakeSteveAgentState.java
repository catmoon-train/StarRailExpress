package org.agmas.noellesroles.game.fake_steve;

import java.util.UUID;
import java.util.ArrayDeque;

import net.minecraft.core.BlockPos;
import io.wifi.starrailexpress.cca.SREPlayerTaskComponent;

public final class FakeSteveAgentState {
    public final UUID playerId;
    public final ReplacementCause cause;
    public AgentMode mode = AgentMode.DISGUISE_IDLE;
    public final FakeSteveBrain brain = new FakeSteveBrain();
    public UUID focusTarget;
    public boolean pendingEngagement;
    public int faceTicks;
    public int assimilationTicks;
    public long nextDecisionTick;
    public long nextPathTick;
    public BlockPos pathGoal;
    public final ArrayDeque<BlockPos> path = new ArrayDeque<>();
    public long motionSequence;
    public FakeSteveMotionPolicy.Lease motionLease;
    public boolean motionSprint;
    public boolean motionCrouch;
    public int rejectedMotionPackets;
    public SREPlayerTaskComponent.Task taskType;
    public BlockPos taskGoal;
    public long taskRetryTick;
    public long nextTaskInteractionTick;

    FakeSteveAgentState(UUID playerId, ReplacementCause cause) {
        this.playerId = playerId;
        this.cause = cause;
    }
}
