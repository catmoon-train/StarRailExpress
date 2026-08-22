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

package org.agmas.noellesroles.api.time;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.agmas.noellesroles.game.c4.C4Detonation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Immutable, in-memory snapshot of rewindable state inside one game area. */
public final class TimeRewindAreaSnapshot {
    record ItemState(UUID entityId, CompoundTag data) {
        ItemState {
            data = data.copy();
        }

        CompoundTag copyData() {
            return data.copy();
        }
    }

    record DoorState(BlockPos lowerPos, BlockState lowerState, BlockState upperState,
            CompoundTag data, int cooldown) {
        DoorState {
            lowerPos = lowerPos.immutable();
            data = data.copy();
        }

        CompoundTag copyData() {
            return data.copy();
        }
    }

    public record Warning(String scope, String subject, String message) {
    }

    private final ResourceKey<Level> dimension;
    private final AABB area;
    private final List<ItemState> itemStates;
    private final List<DoorState> doorStates;
    private final C4Detonation.TimeState c4State;
    private final List<Warning> warnings;

    TimeRewindAreaSnapshot(ResourceKey<Level> dimension, AABB area,
            List<ItemState> itemStates, List<DoorState> doorStates,
            C4Detonation.TimeState c4State, List<Warning> warnings) {
        this.dimension = dimension;
        this.area = new AABB(area.minX, area.minY, area.minZ, area.maxX, area.maxY, area.maxZ);
        this.itemStates = copyItems(itemStates);
        this.doorStates = copyDoors(doorStates);
        this.c4State = c4State;
        this.warnings = List.copyOf(warnings);
    }

    private static List<ItemState> copyItems(List<ItemState> states) {
        List<ItemState> copy = new ArrayList<>(states.size());
        states.forEach(state -> copy.add(new ItemState(state.entityId(), state.data())));
        return List.copyOf(copy);
    }

    private static List<DoorState> copyDoors(List<DoorState> states) {
        List<DoorState> copy = new ArrayList<>(states.size());
        states.forEach(state -> copy.add(new DoorState(state.lowerPos(), state.lowerState(),
                state.upperState(), state.data(), state.cooldown())));
        return List.copyOf(copy);
    }

    public ResourceKey<Level> dimension() {
        return dimension;
    }

    public AABB area() {
        return area;
    }

    public int itemCount() {
        return itemStates.size();
    }

    public int doorCount() {
        return doorStates.size();
    }

    public List<Warning> warnings() {
        return warnings;
    }

    List<ItemState> rawItemStates() {
        return itemStates;
    }

    List<DoorState> rawDoorStates() {
        return doorStates;
    }

    C4Detonation.TimeState rawC4State() {
        return c4State;
    }
}
