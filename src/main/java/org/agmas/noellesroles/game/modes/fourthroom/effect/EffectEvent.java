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
