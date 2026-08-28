package org.agmas.noellesroles.game.fake_steve;

import java.util.UUID;
import java.util.ArrayDeque;

import net.minecraft.core.BlockPos;

public final class FakeSteveAgentState {
    public final UUID playerId;
    public final ReplacementCause cause;
    public AgentMode mode = AgentMode.ROAM;
    public UUID focusTarget;
    public int faceTicks;
    public int lostSightTicks;
    public int assimilationTicks;
    public long nextDecisionTick;
    public long nextPathTick;
    public BlockPos pathGoal;
    public final ArrayDeque<BlockPos> path = new ArrayDeque<>();

    FakeSteveAgentState(UUID playerId, ReplacementCause cause) {
        this.playerId = playerId;
        this.cause = cause;
    }
}
