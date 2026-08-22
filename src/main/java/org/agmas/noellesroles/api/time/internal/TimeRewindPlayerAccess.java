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

package org.agmas.noellesroles.api.time.internal;

import net.minecraft.nbt.CompoundTag;

/** Implemented on {@code ServerPlayer} by the time-rewind mixin. */
public interface TimeRewindPlayerAccess {
    CompoundTag noellesroles$captureTimeRewindState();

    void noellesroles$restoreTimeRewindState(CompoundTag state);
}
