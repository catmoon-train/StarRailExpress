package org.agmas.noellesroles.game.modes.fourthroom.effect;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.Optional;
import java.util.UUID;

/**
 * A timed effect event that can execute on client and/or server.
 * Inspired by minocode's EffectEvent system.
 */
public interface EffectEvent {

    /** Millisecond offset from the base time when this event should fire. */
    long timeOffset();

    /** Optional target player UUID. */
    default Optional<UUID> target() {
        return Optional.empty();
    }

    /** Execute on the client side (rendering, sounds, particles). */
    default void executeClient(BlockPos origin) {}

    /** Execute on the server side (game effects). */
    default void executeServer(ServerLevel level, BlockPos origin) {}
}
