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
