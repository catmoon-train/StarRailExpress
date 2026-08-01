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

package io.wifi.starrailexpress.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Unified passive registry. A role may expose multiple passive definitions.
 * The component tick invokes them only while the player owns that role.
 */
public final class RolePassive {
    @FunctionalInterface
    public interface TickHandler {
        void tick(ServerPlayer player);
    }

    public record Definition(ResourceLocation id, String nameKey, int intervalTicks, TickHandler handler) {
        public Definition {
            if (id == null || nameKey == null || handler == null) {
                throw new IllegalArgumentException("Passive id, name key and handler are required");
            }
            intervalTicks = Math.max(1, intervalTicks);
        }
    }

    private static final Map<ResourceLocation, List<Definition>> PASSIVES = new HashMap<>();

    private RolePassive() {
    }

    public static Definition passive(ResourceLocation id, String nameKey, int intervalTicks, TickHandler handler) {
        return new Definition(id, nameKey, intervalTicks, handler);
    }

    public static void register(SRERole role, Definition... definitions) {
        register(role.identifier(), definitions);
    }

    public static void register(ResourceLocation role, Definition... definitions) {
        PASSIVES.put(role, List.of(definitions));
    }

    public static List<Definition> getDefinitions(SRERole role) {
        return role == null ? List.of() : PASSIVES.getOrDefault(role.identifier(), List.of());
    }

    public static void tick(ServerPlayer player, SRERole role) {
        long gameTime = player.level().getGameTime();
        for (Definition passive : getDefinitions(role)) {
            if (gameTime % passive.intervalTicks() == 0) {
                passive.handler().tick(player);
            }
        }
    }
}
