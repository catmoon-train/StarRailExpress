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

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * An immutable, in-memory snapshot of one server player.
 *
 * <p>Snapshots are tied to a running server and are not a persistent save format.
 */
public final class TimeRewindSnapshot {
    enum ComponentFormat {
        CUSTOM,
        ROLE_SYNC,
        PERSISTENT_NBT
    }

    record ComponentState(ComponentFormat format, CompoundTag data) {
        ComponentState {
            data = data.copy();
        }

        @Override
        public CompoundTag data() {
            return data.copy();
        }

        CompoundTag rawData() {
            return data;
        }
    }

    public record Warning(ResourceLocation componentId, String message) {
    }

    private final UUID playerId;
    private final ResourceKey<Level> dimension;
    private final long capturedAtGameTime;
    private final CompoundTag vanillaState;
    private final Map<ResourceLocation, ComponentState> componentStates;
    private final List<Warning> warnings;

    TimeRewindSnapshot(UUID playerId, ResourceKey<Level> dimension, long capturedAtGameTime,
            CompoundTag vanillaState, Map<ResourceLocation, ComponentState> componentStates,
            List<Warning> warnings) {
        this.playerId = playerId;
        this.dimension = dimension;
        this.capturedAtGameTime = capturedAtGameTime;
        this.vanillaState = vanillaState.copy();
        this.componentStates = copyStates(componentStates);
        this.warnings = List.copyOf(warnings);
    }

    private static Map<ResourceLocation, ComponentState> copyStates(
            Map<ResourceLocation, ComponentState> states) {
        Map<ResourceLocation, ComponentState> copy = new LinkedHashMap<>();
        states.forEach((id, state) -> copy.put(id,
                new ComponentState(state.format(), state.rawData())));
        // Component factories have a stable iteration order. Keep it so related
        // components are restored in the same order in which they were captured.
        return Collections.unmodifiableMap(copy);
    }

    public UUID playerId() {
        return playerId;
    }

    public ResourceKey<Level> dimension() {
        return dimension;
    }

    public long capturedAtGameTime() {
        return capturedAtGameTime;
    }

    public int componentCount() {
        return componentStates.size();
    }

    public List<Warning> warnings() {
        return warnings;
    }

    public CompoundTag vanillaState() {
        return vanillaState.copy();
    }

    /** Position of the rewind anchor. */
    public Vec3 position() {
        ListTag pos = vanillaState.getList("Pos", Tag.TAG_DOUBLE);
        if (pos.size() < 3) {
            throw new IllegalStateException("snapshot has no valid position");
        }
        return new Vec3(pos.getDouble(0), pos.getDouble(1), pos.getDouble(2));
    }

    /** Horizontal view rotation at the rewind anchor. */
    public float yRot() {
        ListTag rotation = vanillaState.getList("Rotation", Tag.TAG_FLOAT);
        return rotation.size() >= 1 ? rotation.getFloat(0) : 0.0f;
    }

    /** Vertical view rotation at the rewind anchor. */
    public float xRot() {
        ListTag rotation = vanillaState.getList("Rotation", Tag.TAG_FLOAT);
        return rotation.size() >= 2 ? rotation.getFloat(1) : 0.0f;
    }

    public boolean containsComponent(ResourceLocation componentId) {
        return componentStates.containsKey(componentId);
    }

    public List<ResourceLocation> componentIds() {
        return List.copyOf(componentStates.keySet());
    }

    CompoundTag rawVanillaState() {
        return vanillaState;
    }

    Map<ResourceLocation, ComponentState> rawComponentStates() {
        return componentStates;
    }
}
