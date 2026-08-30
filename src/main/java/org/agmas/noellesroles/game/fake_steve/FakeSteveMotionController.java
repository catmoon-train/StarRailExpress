package org.agmas.noellesroles.game.fake_steve;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import org.agmas.noellesroles.packet.FakeSteveControlS2CPacket;

/**
 * Bridges server route intentions to an authoritative server movement loop.
 *
 * <p>The possessed body is a real {@link ServerPlayer}, but instead of trusting
 * the client's simulated keyboard input (which caused stalls, rubber-banding and
 * desync), the server now drives the body every tick through the vanilla
 * movement analog fields ({@code xxa}/{@code zza}, sprint, jump, rotation). The
 * client still receives the same input so it can play the walk animation and the
 * feet keep stepping naturally.</p>
 */
public final class FakeSteveMotionController {
    private static final int LEASE_TICKS = 10;
    /** How often the owning client is re-anchored to the authoritative position. */
    private static final long SYNC_INTERVAL_TICKS = 5L;

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
        ServerPlayNetworking.send(player, new FakeSteveControlS2CPacket(
                ++state.motionSequence, LEASE_TICKS, forward, strafe, jump, sprint,
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
        ServerPlayNetworking.send(player, new FakeSteveControlS2CPacket(
                ++state.motionSequence, 0, 0.0F, 0.0F,
                false, false, false, player.getYRot(), player.getXRot(), false));
    }

    /**
     * Applies the stored intention every server tick. This is the "move" half of
     * the movement pipeline: the vanilla {@code travel()} will translate the body,
     * resolve collision and gravity, and advance the walk distance so the legs
     * swing for every observer.
     */
    public static void applyServerMotion(ServerPlayer player, FakeSteveAgentState state) {
        if (player == null || state == null || player.isSpectator() || player.isRemoved()) {
            return;
        }
        long now = player.serverLevel().getGameTime();
        boolean active = state.moveActive && now <= state.moveExpiresAtTick;
        if (!active) {
            player.xxa = 0.0F;
            player.zza = 0.0F;
            player.setJumping(false);
            player.setSprinting(false);
            return;
        }
        float yaw = FakeSteveMotionPolicy.turnToward(player.getYRot(), state.moveYaw);
        float pitch = FakeSteveMotionPolicy.turnToward(player.getXRot(), state.movePitch);
        player.setYRot(yaw);
        player.setYHeadRot(yaw);
        player.setXRot(pitch);
        player.xxa = state.moveStrafe;
        player.zza = state.moveForward;
        player.setJumping(state.moveJump);
        player.setSprinting(state.moveSprint);
        player.setShiftKeyDown(state.moveCrouch);
        // Re-anchor the owning client so its locally animated body never drifts
        // away from the authoritative position for long.
        if (Math.floorMod(now, SYNC_INTERVAL_TICKS) == 0L
                && (state.moveForward != 0.0F || state.moveStrafe != 0.0F)) {
            player.connection.teleport(player.getX(), player.getY(), player.getZ(), yaw, pitch);
        }
    }

    /** The server owns the body now: client position packets are ignored. */
    public static boolean acceptsMove(ServerPlayer player, ServerboundMovePlayerPacket packet) {
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
