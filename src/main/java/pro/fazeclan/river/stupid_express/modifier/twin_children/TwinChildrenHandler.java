/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package pro.fazeclan.river.stupid_express.modifier.twin_children;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
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

/** Server-side pairing, mounting, and scale lifecycle for Twin Children. */
public final class TwinChildrenHandler {
    public static final AttributeModifier HALF_SCALE = new AttributeModifier(
            StupidExpress.id("twin_children_half_scale"), -0.5D, AttributeModifier.Operation.ADD_VALUE);

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

    private static void assign(ServerPlayer first) {
        if (PAIRS.containsKey(first.getUUID())) {
            applyHalfScale(first);
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
        applyHalfScale(first);
        applyHalfScale(second);
        updateMount(pair, level);
    }

    /** Called by the modifier tick; only the lower twin performs the pair update. */
    public static void serverTick(ServerPlayer player) {
        Pair pair = PAIRS.get(player.getUUID());
        if (pair != null && pair.lower().equals(player.getUUID())) {
            updateMount(pair, player.serverLevel());
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
            return;
        }

        applyHalfScale(lower);
        applyHalfScale(upper);
        if (lower.isPassenger()) {
            lower.stopRiding();
        }
        if (upper.getVehicle() != lower) {
            upper.stopRiding();
            upper.startRiding(lower, true);
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
        if (lower != null) removeHalfScale(lower);
        if (upper != null) removeHalfScale(upper);
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
        var scale = player.getAttribute(Attributes.SCALE);
        if (scale != null && !scale.hasModifier(HALF_SCALE.id())) {
            scale.addPermanentModifier(HALF_SCALE);
        }
    }

    private static void removeHalfScale(ServerPlayer player) {
        var scale = player.getAttribute(Attributes.SCALE);
        if (scale != null) scale.removeModifier(HALF_SCALE);
    }

    private static void dismount(ServerPlayer lower, ServerPlayer upper) {
        if (upper != null && (lower == null || upper.getVehicle() == lower)) upper.stopRiding();
    }

    private static boolean isAlivePlayer(ServerPlayer player) {
        return player.isAlive() && !player.isSpectator() && !player.isCreative();
    }

    static Faction factionOf(SRERole role) {
        if (role == null) return Faction.INDEPENDENT_NEUTRAL;
        if (role.isNeutrals() && !role.isNeutralForKiller()) return Faction.INDEPENDENT_NEUTRAL;
        if (role.isNeutralForKiller() || SREGameWorldComponent.isKillerTeamRoleStatic(role)) return Faction.KILLER;
        if (role.isInnocent()) return Faction.INNOCENT;
        return Faction.INDEPENDENT_NEUTRAL;
    }

    enum Faction { INNOCENT, KILLER, INDEPENDENT_NEUTRAL }

    private record Pair(UUID lower, UUID upper) {
    }
}
