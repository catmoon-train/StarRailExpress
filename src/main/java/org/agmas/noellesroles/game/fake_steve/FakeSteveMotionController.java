package org.agmas.noellesroles.game.fake_steve;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import org.agmas.noellesroles.packet.FakeSteveControlS2CPacket;

/**
 * Bridges server route intentions to one client-predicted, server-validated
 * movement stream.
 *
 * <p>The owning client applies the leased input through vanilla player physics
 * and sends the usual movement packets. The server validates that one stream.
 * It must not also translate the body itself: two physics simulations were the
 * source of the visible position split and periodic rubber-banding.</p>
 */
public final class FakeSteveMotionController {
    private static final int LEASE_TICKS = 10;
    private static final double ROUTE_CORRIDOR_RADIUS = 2.25D;
    private static final double MAX_CLIENT_STEP = 1.50D;

    private FakeSteveMotionController() {
    }

    static void drive(ServerPlayer player, FakeSteveAgentState state,
            float forward, float strafe, boolean jump, boolean sprint,
            boolean crouch, float targetYaw, float targetPitch, BlockPos routePoint) {
        long now = player.serverLevel().getGameTime();
        state.moveActive = true;
        state.moveExpiresAtTick = now + LEASE_TICKS;
        state.moveForward = Mth.clamp(forward, -1.0F, 1.0F);
        state.moveStrafe = Mth.clamp(strafe, -1.0F, 1.0F);
        state.moveJump = jump;
        state.moveSprint = sprint;
        state.moveCrouch = crouch;
        state.moveYaw = targetYaw;
        state.movePitch = targetPitch;
        state.motionSprint = sprint;
        state.motionCrouch = crouch;
        state.motionLease = new FakeSteveMotionPolicy.Lease(++state.motionSequence,
                state.moveExpiresAtTick, routePoint.getX() + 0.5D, routePoint.getZ() + 0.5D,
                ROUTE_CORRIDOR_RADIUS, MAX_CLIENT_STEP);
        ServerPlayNetworking.send(player, new FakeSteveControlS2CPacket(
                state.motionSequence, LEASE_TICKS, forward, strafe, jump, sprint,
                crouch, targetYaw, targetPitch, true));
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
        state.moveActive = false;
        state.moveForward = 0.0F;
        state.moveStrafe = 0.0F;
        state.moveJump = false;
        state.moveSprint = false;
        state.moveCrouch = false;
        state.motionSprint = false;
        state.motionCrouch = false;
        state.motionLease = null;
        ServerPlayNetworking.send(player, new FakeSteveControlS2CPacket(
                ++state.motionSequence, 0, 0.0F, 0.0F,
                false, false, false, player.getYRot(), player.getXRot(), false));
    }

    /**
     * Expires a stale lease. Translation is intentionally client-predicted;
     * normal server packet handling remains the only position authority.
     */
    public static void applyServerMotion(ServerPlayer player, FakeSteveAgentState state) {
        if (player == null || state == null || player.isSpectator() || player.isRemoved()) {
            return;
        }
        long now = player.serverLevel().getGameTime();
        if (!state.moveActive || now > state.moveExpiresAtTick) {
            state.moveActive = false;
            state.motionLease = null;
        }
    }

    /** Accept exactly the vanilla packets produced by a live AI input lease. */
    public static boolean acceptsMove(ServerPlayer player, ServerboundMovePlayerPacket packet) {
        FakeSteveAgentState state = FakeSteveDirector.agent(player.serverLevel(), player.getUUID());
        if (state == null || !state.moveActive || state.motionLease == null) {
            return false;
        }
        long now = player.serverLevel().getGameTime();
        double nextX = packet.getX(player.getX());
        double nextZ = packet.getZ(player.getZ());
        boolean accepted = FakeSteveMotionPolicy.accepts(state.motionLease, now,
                player.getX(), player.getZ(), nextX, nextZ);
        if (accepted) {
            state.rejectedMotionPackets = 0;
            return true;
        }
        state.rejectedMotionPackets++;
        // A correction is reserved for a real drift, never issued periodically.
        if (FakeSteveMotionPolicy.shouldCorrect(state.rejectedMotionPackets,
                player.distanceToSqr(nextX, player.getY(), nextZ))) {
            player.connection.teleport(player.getX(), player.getY(), player.getZ(),
                    player.getYRot(), player.getXRot());
            state.rejectedMotionPackets = 0;
        }
        return false;
    }

    /** Sprint/shift commands are cosmetic hints; the server sets them itself. */
    public static boolean acceptsCommand(ServerPlayer player,
            ServerboundPlayerCommandPacket.Action action) {
        return switch (action) {
            case START_SPRINTING, STOP_SPRINTING, PRESS_SHIFT_KEY, RELEASE_SHIFT_KEY -> true;
            default -> false;
        };
    }
}
