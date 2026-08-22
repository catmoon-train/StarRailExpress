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

import net.minecraft.resources.ResourceLocation;
import org.ladysnake.cca.api.v3.component.ComponentKey;

import java.util.HashSet;
import java.util.Set;

/** Options applied while capturing a time-rewind snapshot. */
public final class TimeRewindOptions {
    private static final Set<ResourceLocation> DEFAULT_EXCLUDED_COMPONENTS = Set.of(
            ResourceLocation.fromNamespaceAndPath("starrailexpress", "player_skins"),
            ResourceLocation.fromNamespaceAndPath("starrailexpress", "player_progression"),
            ResourceLocation.fromNamespaceAndPath("starrailexpress", "nametag_inventory"));
    public static final TimeRewindOptions DEFAULT = builder().build();

    private final Set<ResourceLocation> excludedComponents;

    private TimeRewindOptions(Set<ResourceLocation> excludedComponents) {
        this.excludedComponents = Set.copyOf(excludedComponents);
    }

    public static Builder builder() {
        return new Builder();
    }

    boolean includes(ComponentKey<?> key) {
        return !excludedComponents.contains(key.getId());
    }

    public Set<ResourceLocation> excludedComponents() {
        return excludedComponents;
    }

    public static Set<ResourceLocation> defaultExcludedComponents() {
        return DEFAULT_EXCLUDED_COMPONENTS;
    }

    public static final class Builder {
        private final Set<ResourceLocation> excludedComponents = new HashSet<>(DEFAULT_EXCLUDED_COMPONENTS);

        /**
         * Keeps the current value of this component when the snapshot is restored.
         * This is useful for the component that owns the rewind state machine or its
         * triggering cooldown.
         */
        public Builder excludeComponent(ComponentKey<?> key) {
            excludedComponents.add(key.getId());
            return this;
        }

        public Builder excludeComponent(ResourceLocation componentId) {
            excludedComponents.add(componentId);
            return this;
        }

        /** Explicitly allows a component that is excluded by the default policy. */
        public Builder includeComponent(ComponentKey<?> key) {
            excludedComponents.remove(key.getId());
            return this;
        }

        public Builder includeComponent(ResourceLocation componentId) {
            excludedComponents.remove(componentId);
            return this;
        }

        public TimeRewindOptions build() {
            return new TimeRewindOptions(excludedComponents);
        }
    }
}
