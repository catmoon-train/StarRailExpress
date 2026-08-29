package org.agmas.noellesroles.game.fake_steve;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.packet.FakeSteveControlS2CPacket;

/** Bridges server route intentions to vanilla client movement and validates the result. */
public final class FakeSteveMotionController {
    private static final int LEASE_TICKS = 10;
    private static final double CORRIDOR_RADIUS = 1.75D;
    private static final double MAX_PACKET_STEP = 0.85D;

    private FakeSteveMotionController() {
    }

    static void drive(ServerPlayer player, FakeSteveAgentState state,
            float forward, float strafe, boolean jump, boolean sprint,
            boolean crouch, float targetYaw, float targetPitch, BlockPos routePoint) {
        long now = player.serverLevel().getGameTime();
        BlockPos route = routePoint == null ? player.blockPosition() : routePoint;
        long sequence = ++state.motionSequence;
        state.motionLease = new FakeSteveMotionPolicy.Lease(sequence, now + LEASE_TICKS,
                route.getX() + 0.5D, route.getZ() + 0.5D,
                CORRIDOR_RADIUS, MAX_PACKET_STEP);
        state.motionSprint = sprint;
        state.motionCrouch = crouch;
        ServerPlayNetworking.send(player, new FakeSteveControlS2CPacket(sequence,
                LEASE_TICKS, forward, strafe, jump, sprint, crouch,
                targetYaw, targetPitch, true));
    }

    static void hold(ServerPlayer player, FakeSteveAgentState state,
            float targetYaw, float targetPitch) {
        drive(player, state, 0.0F, 0.0F, false, false, false,
                targetYaw, targetPitch, player.blockPosition());
    }

    static void clear(ServerPlayer player, FakeSteveAgentState state) {
        if (state == null) {
            return;
        }
        state.motionLease = null;
        state.motionSprint = false;
        state.motionCrouch = false;
        ServerPlayNetworking.send(player, new FakeSteveControlS2CPacket(
                ++state.motionSequence, 0, 0.0F, 0.0F,
                false, false, false, player.getYRot(), player.getXRot(), false));
    }

    public static boolean acceptsMove(ServerPlayer player, ServerboundMovePlayerPacket packet) {
        FakeSteveAgentState state = FakeSteveDirector.agent(player.serverLevel(), player.getUUID());
        if (state == null || state.motionLease == null) {
            return false;
        }
        long now = player.serverLevel().getGameTime();
        if (!packet.hasPosition()) {
            return now <= state.motionLease.expiresAtTick();
        }
        double nextX = packet.getX(player.getX());
        double nextY = packet.getY(player.getY());
        double nextZ = packet.getZ(player.getZ());
        boolean accepted = FakeSteveMotionPolicy.accepts(state.motionLease, now,
                player.getX(), player.getZ(), nextX, nextZ)
                && Math.abs(nextY - player.getY()) <= 1.25D;
        if (accepted) {
            state.rejectedMotionPackets = 0;
            return true;
        }
        state.rejectedMotionPackets++;
        double dx = nextX - player.getX();
        double dz = nextZ - player.getZ();
        if (FakeSteveMotionPolicy.shouldCorrect(state.rejectedMotionPackets,
                dx * dx + dz * dz)) {
            state.rejectedMotionPackets = 0;
            player.connection.teleport(player.getX(), player.getY(), player.getZ(),
                    player.getYRot(), player.getXRot());
        }
        return false;
    }

    public static boolean acceptsCommand(ServerPlayer player,
            ServerboundPlayerCommandPacket.Action action) {
        FakeSteveAgentState state = FakeSteveDirector.agent(player.serverLevel(), player.getUUID());
        if (state == null || state.motionLease == null
                || player.serverLevel().getGameTime() > state.motionLease.expiresAtTick()) {
            return false;
        }
        return switch (action) {
            case START_SPRINTING -> state.motionSprint;
            case STOP_SPRINTING -> !state.motionSprint;
            case PRESS_SHIFT_KEY -> state.motionCrouch;
            case RELEASE_SHIFT_KEY -> !state.motionCrouch;
            default -> false;
        };
    }
}
