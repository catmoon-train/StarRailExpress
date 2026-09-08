/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package pro.fazeclan.river.stupid_express.modifier.twin_children;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.harpymodloader.events.GameInitializeEvent;
import org.agmas.harpymodloader.events.ModifierAssigned;
import org.agmas.harpymodloader.events.ModifierRemoved;
import org.jetbrains.annotations.Nullable;
import pro.fazeclan.river.stupid_express.StupidExpress;
import pro.fazeclan.river.stupid_express.constants.SEModifiers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side pairing, mounting, and scale lifecycle for Twin Children.
 *
 * <p>Half-scale is only applied while both twins are alive and stacked. Vanilla
 * player passenger attachments sit the rider inside the vehicle, so the upper
 * twin is placed on the visual head and other clients are told that position
 * every tick.
 */
public final class TwinChildrenHandler {
    public static final AttributeModifier HALF_SCALE = new AttributeModifier(
            StupidExpress.id("twin_children_half_scale"), -0.5D, AttributeModifier.Operation.ADD_VALUE);

    public static final float VISUAL_STANDING_HEIGHT = TwinChildrenHitbox.VISUAL_STANDING_HEIGHT;
    public static final float STACKED_UNSCALED_HEIGHT = TwinChildrenHitbox.STACKED_UNSCALED_HEIGHT;

    private static final float HEIGHT_REFRESH_EPSILON = 0.05F;

    private static final Map<UUID, Pair> PAIRS = new ConcurrentHashMap<>();

    private TwinChildrenHandler() {
    }

    public static void init() {
        ModifierAssigned.EVENT.register((player, modifier) -> {
            if (modifier.equals(SEModifiers.TWIN_CHILDREN) && player instanceof ServerPlayer serverPlayer) {
                assign(serverPlayer);
            }
        });
        ModifierRemoved.EVENT.register((player, modifier) -> {
            if (modifier.equals(SEModifiers.TWIN_CHILDREN) && player instanceof ServerPlayer serverPlayer) {
                removePair(serverPlayer, true);
            }
        });
        GameInitializeEvent.EVENT.register((level, game, players) -> PAIRS.clear());
    }

    public static float stackedHeightScale(float currentUnscaledHeight) {
        return TwinChildrenHitbox.stackedHeightScale(currentUnscaledHeight);
    }

    public static float upperHeightScale(float currentUnscaledHeight) {
        return TwinChildrenHitbox.upperHeightScale(currentUnscaledHeight);
    }

    public static double headPassengerAttachmentY(float vehicleScale, double passengerVehicleAttachY) {
        return TwinChildrenHitbox.headPassengerAttachmentY(vehicleScale, passengerVehicleAttachY);
    }

    public static boolean hasHalfScale(Player player) {
        if (player == null) {
            return false;
        }
        AttributeInstance scale = player.getAttribute(Attributes.SCALE);
        return scale != null && scale.hasModifier(HALF_SCALE.id());
    }

    public static boolean isStackedLower(Player player) {
        return hasHalfScale(player)
                && player.getFirstPassenger() instanceof Player passenger
                && hasHalfScale(passenger);
    }

    public static boolean isStackedUpper(Player player) {
        return hasHalfScale(player)
                && player.getVehicle() instanceof Player vehicle
                && hasHalfScale(vehicle);
    }

    public static boolean shouldStayRiding(Player player) {
        return isStackedUpper(player);
    }

    private static void assign(ServerPlayer first) {
        Pair existing = PAIRS.get(first.getUUID());
        if (existing != null) {
            updateMount(existing, first.serverLevel());
            return;
        }

        ServerLevel level = first.serverLevel();
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(level);
        SRERole firstRole = game.getRole(first);
        if (factionOf(firstRole) == Faction.INDEPENDENT_NEUTRAL) {
            rejectAssignment(first);
            return;
        }

        ArrayList<ServerPlayer> candidates = new ArrayList<>(level.players());
        Collections.shuffle(candidates);
        ServerPlayer second = candidates.stream()
                .filter(candidate -> !candidate.equals(first))
                .filter(TwinChildrenHandler::isAlivePlayer)
                .filter(candidate -> !PAIRS.containsKey(candidate.getUUID()))
                .filter(candidate -> !WorldModifierComponent.KEY.get(level)
                        .isModifier(candidate, SEModifiers.TWIN_CHILDREN))
                .filter(candidate -> factionOf(game.getRole(candidate)) == factionOf(firstRole))
                .findFirst().orElse(null);

        if (second == null) {
            rejectAssignment(first);
            return;
        }

        removeSizeConflicts(first);
        removeSizeConflicts(second);
        WorldModifierComponent modifiers = WorldModifierComponent.KEY.get(level);
        modifiers.addModifier(second, SEModifiers.TWIN_CHILDREN);

        Pair pair = new Pair(first.getUUID(), second.getUUID());
        PAIRS.put(first.getUUID(), pair);
        PAIRS.put(second.getUUID(), pair);
        updateMount(pair, level);
    }

    /** Called by the modifier tick for either twin. */
    public static void serverTick(ServerPlayer player) {
        Pair pair = PAIRS.get(player.getUUID());
        if (pair == null) {
            removeHalfScale(player);
            return;
        }
        if (!player.getUUID().equals(pair.lower())
                && player.server.getPlayerList().getPlayer(pair.lower()) != null) {
            return;
        }
        updateMount(pair, player.serverLevel());
    }

    public static void clientTick(Player player) {
        if (isStackedLower(player) && player.getFirstPassenger() instanceof Player upper) {
            player.positionRider(upper);
        }
        if (isStackedUpper(player) && player.getVehicle() instanceof Player lower) {
            lower.positionRider(player);
        }
    }

    /** The other twin in this player's pair, or null if unpaired / offline. */
    @Nullable
    public static ServerPlayer getPartner(ServerPlayer player) {
        Pair pair = PAIRS.get(player.getUUID());
        if (pair == null) {
            return null;
        }
        UUID partnerId = pair.lower().equals(player.getUUID()) ? pair.upper() : pair.lower();
        return player.server.getPlayerList().getPlayer(partnerId);
    }

    public static boolean isPairedAlivePlayer(ServerPlayer player) {
        return isAlivePlayer(player) && PAIRS.containsKey(player.getUUID());
    }

    private static void updateMount(Pair pair, ServerLevel level) {
        ServerPlayer lower = level.getServer().getPlayerList().getPlayer(pair.lower());
        ServerPlayer upper = level.getServer().getPlayerList().getPlayer(pair.upper());
        if (lower == null || upper == null || lower.serverLevel() != upper.serverLevel()
                || !isAlivePlayer(lower) || !isAlivePlayer(upper)) {
            dismount(lower, upper);
            if (lower != null) {
                removeHalfScale(lower);
            }
            if (upper != null) {
                removeHalfScale(upper);
            }
            return;
        }

        applyHalfScale(lower);
        applyHalfScale(upper);
        if (lower.isPassenger()) {
            lower.stopRiding();
        }
        boolean remounted = false;
        if (upper.getVehicle() != lower) {
            upper.stopRiding();
            upper.startRiding(lower, true);
            remounted = true;
        }
        if (upper.getVehicle() != lower) {
            removeHalfScale(lower);
            removeHalfScale(upper);
            return;
        }

        refreshStackedCollision(lower, upper);
        syncUpperPosition(lower, upper, remounted);
    }

    private static void refreshStackedCollision(ServerPlayer lower, ServerPlayer upper) {
        float expectedLower = TwinChildrenHitbox.STACKED_UNSCALED_HEIGHT * lower.getScale();
        if (Math.abs(lower.getBbHeight() - expectedLower) > HEIGHT_REFRESH_EPSILON) {
            lower.refreshDimensions();
        }
        float expectedUpper = TwinChildrenHitbox.UPPER_UNSCALED_HEIGHT * upper.getScale();
        if (Math.abs(upper.getBbHeight() - expectedUpper) > HEIGHT_REFRESH_EPSILON) {
            upper.refreshDimensions();
        }
    }

    private static void syncUpperPosition(ServerPlayer lower, ServerPlayer upper, boolean remounted) {
        lower.positionRider(upper);
        ClientboundTeleportEntityPacket teleport = new ClientboundTeleportEntityPacket(upper);
        ClientboundSetPassengersPacket passengers = remounted ? new ClientboundSetPassengersPacket(lower) : null;
        for (ServerPlayer viewer : upper.serverLevel().players()) {
            if (passengers != null) {
                viewer.connection.send(passengers);
            }
            if (viewer != upper) {
                viewer.connection.send(teleport);
            }
        }
    }

    public static void removePairForConflictingModifier(net.minecraft.world.entity.player.Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            removePair(serverPlayer, true);
        }
    }

    private static void removePair(ServerPlayer player, boolean removeModifiers) {
        Pair pair = PAIRS.remove(player.getUUID());
        if (pair == null) {
            removeHalfScale(player);
            if (removeModifiers) {
                WorldModifierComponent.KEY.get(player.serverLevel())
                        .removeModifier(player, SEModifiers.TWIN_CHILDREN);
            }
            return;
        }
        PAIRS.remove(pair.lower());
        PAIRS.remove(pair.upper());
        ServerPlayer lower = player.server.getPlayerList().getPlayer(pair.lower());
        ServerPlayer upper = player.server.getPlayerList().getPlayer(pair.upper());
        dismount(lower, upper);
        if (lower != null) {
            removeHalfScale(lower);
        }
        if (upper != null) {
            removeHalfScale(upper);
        }
        if (removeModifiers) {
            WorldModifierComponent modifiers = WorldModifierComponent.KEY.get(player.serverLevel());
            modifiers.removeModifier(pair.lower(), SEModifiers.TWIN_CHILDREN, false);
            modifiers.removeModifier(pair.upper(), SEModifiers.TWIN_CHILDREN, true);
        }
    }

    private static void rejectAssignment(ServerPlayer player) {
        WorldModifierComponent.KEY.get(player.serverLevel())
                .removeModifier(player, SEModifiers.TWIN_CHILDREN);
        removeHalfScale(player);
    }

    private static void removeSizeConflicts(ServerPlayer player) {
        WorldModifierComponent modifiers = WorldModifierComponent.KEY.get(player.serverLevel());
        modifiers.removeModifier(player, SEModifiers.TINY);
        modifiers.removeModifier(player, SEModifiers.TALL);
        player.getAttribute(Attributes.SCALE).removeModifier(SEModifiers.TINY_MODIFIER);
        player.getAttribute(Attributes.SCALE).removeModifier(SEModifiers.TALL_MODIFIER);
    }

    private static void applyHalfScale(ServerPlayer player) {
        AttributeInstance scale = player.getAttribute(Attributes.SCALE);
        if (scale != null && !scale.hasModifier(HALF_SCALE.id())) {
            scale.addPermanentModifier(HALF_SCALE);
            player.refreshDimensions();
        }
    }

    private static void removeHalfScale(ServerPlayer player) {
        AttributeInstance scale = player.getAttribute(Attributes.SCALE);
        if (scale != null && scale.hasModifier(HALF_SCALE.id())) {
            scale.removeModifier(HALF_SCALE);
            player.refreshDimensions();
        }
    }

    private static void dismount(ServerPlayer lower, ServerPlayer upper) {
        if (upper != null && (lower == null || upper.getVehicle() == lower)) {
            upper.stopRiding();
        }
    }

    private static boolean isAlivePlayer(ServerPlayer player) {
        return GameUtils.isPlayerAliveAndSurvival(player);
    }

    static Faction factionOf(SRERole role) {
        if (role == null) {
            return Faction.INDEPENDENT_NEUTRAL;
        }
        if (role.isNeutrals() && !role.isNeutralForKiller()) {
            return Faction.INDEPENDENT_NEUTRAL;
        }
        if (role.isNeutralForKiller() || SREGameWorldComponent.isKillerTeamRoleStatic(role)) {
            return Faction.KILLER;
        }
        if (role.isInnocent()) {
            return Faction.INNOCENT;
        }
        return Faction.INDEPENDENT_NEUTRAL;
    }

    enum Faction { INNOCENT, KILLER, INDEPENDENT_NEUTRAL }

    private record Pair(UUID lower, UUID upper) {
    }
}
