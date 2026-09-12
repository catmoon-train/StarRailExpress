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
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.harpymodloader.events.GameInitializeEvent;
import org.agmas.harpymodloader.events.ModifierAssigned;
import org.agmas.harpymodloader.events.ModifierRemoved;
import org.agmas.harpymodloader.modifiers.SREModifier;
import org.agmas.noellesroles.commands.BroadcastCommand;
import org.agmas.noellesroles.game.modifier.NRModifiers;
import org.agmas.noellesroles.role.TraitorAndModifiers;
import org.jetbrains.annotations.Nullable;
import pro.fazeclan.river.stupid_express.StupidExpress;
import pro.fazeclan.river.stupid_express.constants.SEEntities;
import pro.fazeclan.river.stupid_express.constants.SEModifiers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pairing, invisible-seat stacking, fixed half-scale, and swap lifecycle for
 * Twin Children.
 *
 * <p>Half-scale is only applied while both twins are alive and stacked. An
 * invisible seat stays on the lower twin's head; the upper twin rides that
 * seat. Size-changing modifiers cannot override the locked scale.
 *
 * <p>The upper twin cannot be pushed into a block by its own movement, so the
 * lower twin's jump can shove it into a ceiling; wall suffocation is therefore
 * waived while stacked (drowning is unaffected).
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
        GameInitializeEvent.EVENT.register((level, game, players) -> {
            discardAllSeats(level);
            PAIRS.clear();
        });
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (!(entity instanceof Player player) || !source.is(DamageTypes.IN_WALL)) {
                return true;
            }
            return !isStackedUpper(player);
        });
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, serverPlayer, bound) -> {
            if (!hasTwinChildren(serverPlayer)) {
                return true;
            }
            ServerPlayer partner = getPartner(serverPlayer);
            if (partner == null || !isPairedAlivePlayer(partner)) {
                return true;
            }
            Component broadcastMessage = Component
                    .translatable("message.twin_children.broadcast_prefix",
                            Component.literal("").append(serverPlayer.getDisplayName())
                                    .withStyle(ChatFormatting.AQUA),
                            Component.literal(message.signedContent()).withStyle(ChatFormatting.WHITE))
                    .withStyle(ChatFormatting.GOLD);
            BroadcastCommand.BroadcastMessage(serverPlayer, broadcastMessage);
            BroadcastCommand.BroadcastMessage(partner, broadcastMessage);
            return true;
        });
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

    public static boolean hasTwinChildren(Player player) {
        if (player == null) {
            return false;
        }
        WorldModifierComponent cca = WorldModifierComponent.KEY.maybeGet(player.level()).orElse(null);
        return cca != null && cca.isModifier(player, SEModifiers.TWIN_CHILDREN);
    }

    public static boolean hasHalfScale(Player player) {
        if (player == null) {
            return false;
        }
        AttributeInstance scale = player.getAttribute(Attributes.SCALE);
        return scale != null && scale.hasModifier(HALF_SCALE.id());
    }

    public static boolean isStackedLower(Player player) {
        return hasHalfScale(player) && hasTwinChildren(player) && !isStackedUpper(player);
    }

    public static boolean isStackedUpper(Player player) {
        return player != null && player.getVehicle() instanceof TwinChildrenSeatEntity;
    }

    public static boolean shouldStayRiding(Player player) {
        return isStackedUpper(player);
    }

    /**
     * Interaction / sitting should move the walking twin. The upper twin is
     * already a passenger and cannot mount a chair on their own.
     */
    public static Player stackMover(Player player) {
        if (player.getVehicle() instanceof TwinChildrenSeatEntity seat) {
            Player lower = seat.getLowerPlayer();
            return lower != null ? lower : player;
        }
        return player;
    }

    public static boolean shouldRedirectMount(Entity rider, Entity vehicle) {
        if (!(rider instanceof Player player) || vehicle == null || vehicle instanceof TwinChildrenSeatEntity) {
            return false;
        }
        return TwinChildrenRideLogic.redirectMountToLower(
                isStackedUpper(player),
                vehicle == player.getVehicle(),
                vehicle instanceof Player);
    }

    public static boolean shouldKeepLowerOnVehicle(Entity vehicle) {
        return TwinChildrenRideLogic.keepLowerOnVehicle(
                vehicle instanceof Player || vehicle instanceof TwinChildrenSeatEntity);
    }

    public static void positionStackedRider(Player lower) {
        if (!isStackedLower(lower)) {
            return;
        }
        TwinChildrenSeatEntity seat = findSeatOwnedBy(lower);
        if (seat == null) {
            return;
        }
        seat.snapTo(lower);
        Entity rider = seat.getFirstPassenger();
        if (rider != null) {
            seat.positionRider(rider);
        }
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
        if (!TwinChildrenAssignLogic.canReceive(factionOf(firstRole))) {
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
                .filter(candidate -> TwinChildrenAssignLogic.canReceive(factionOf(game.getRole(candidate))))
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
        pair.nextSwapAt = TwinChildrenSwapLogic.scheduleNext(
                GameUtils.getTicksFromGameStart(level), TwinChildrenSwapLogic.intervalTicks());
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
        if (!player.getUUID().equals(pair.lower)
                && player.server.getPlayerList().getPlayer(pair.lower) != null) {
            return;
        }
        updateMount(pair, player.serverLevel());
    }

    public static void clientTick(Player player) {
        positionStackedRider(player);
        if (isStackedUpper(player) && player.getVehicle() instanceof TwinChildrenSeatEntity seat) {
            Player lower = seat.getLowerPlayer();
            if (lower != null) {
                seat.snapTo(lower);
                seat.positionRider(player);
            }
        }
    }

    /** The other twin in this player's pair, or null if unpaired / offline. */
    @Nullable
    public static ServerPlayer getPartner(ServerPlayer player) {
        Pair pair = PAIRS.get(player.getUUID());
        if (pair == null) {
            return null;
        }
        return player.server.getPlayerList().getPlayer(pair.partnerOf(player.getUUID()));
    }

    public static boolean isPairedAlivePlayer(ServerPlayer player) {
        return isAlivePlayer(player) && PAIRS.containsKey(player.getUUID());
    }

    /**
     * Twin size is locked. Size-changing modifiers are stripped instead of
     * breaking the pair.
     *
     * @return {@code true} if the incoming modifier was rejected
     */
    public static boolean rejectForeignSizeModifier(Player player, SREModifier incoming) {
        if (!hasTwinChildren(player) || incoming == null) {
            return false;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            WorldModifierComponent.KEY.get(serverPlayer.level()).removeModifier(serverPlayer, incoming);
            stripForeignScale(serverPlayer);
            if (PAIRS.containsKey(serverPlayer.getUUID())) {
                ServerPlayer partner = getPartner(serverPlayer);
                if (isAlivePlayer(serverPlayer) && partner != null && isAlivePlayer(partner)) {
                    applyHalfScale(serverPlayer);
                }
            }
        }
        return true;
    }

    private static void updateMount(Pair pair, ServerLevel level) {
        maybeSwap(pair, level);

        ServerPlayer lower = playerOf(level, pair.lower);
        ServerPlayer upper = playerOf(level, pair.upper);
        if (lower == null || upper == null || lower.serverLevel() != upper.serverLevel()
                || !isAlivePlayer(lower) || !isAlivePlayer(upper)) {
            discardSeat(pair, level);
            if (upper != null && upper.getVehicle() instanceof TwinChildrenSeatEntity) {
                upper.stopRiding();
            }
            if (lower != null) {
                removeHalfScale(lower);
            }
            if (upper != null) {
                removeHalfScale(upper);
            }
            return;
        }

        applyFixedScale(lower);
        applyFixedScale(upper);
        if (lower.isPassenger() && !shouldKeepLowerOnVehicle(lower.getVehicle())) {
            lower.stopRiding();
        }

        TwinChildrenSeatEntity seat = ensureSeat(pair, level, lower, upper);
        if (seat == null) {
            return;
        }
        seat.bind(lower, upper);
        if (upper.getVehicle() != seat) {
            Entity vehicle = upper.getVehicle();
            if (vehicle != null && shouldKeepLowerOnVehicle(vehicle) && !lower.isPassenger()) {
                lower.startRiding(vehicle, true);
            }
            if (upper.isPassenger()) {
                upper.stopRiding();
            }
            upper.startRiding(seat, true);
        }
        refreshStackedCollision(lower, upper);
    }

    private static void maybeSwap(Pair pair, ServerLevel level) {
        ServerPlayer lower = playerOf(level, pair.lower);
        ServerPlayer upper = playerOf(level, pair.upper);
        if (lower == null || upper == null || !isAlivePlayer(lower) || !isAlivePlayer(upper)) {
            return;
        }
        long now = GameUtils.getTicksFromGameStart(level);
        if (!TwinChildrenSwapLogic.due(now, pair.nextSwapAt)) {
            return;
        }
        pair.nextSwapAt = TwinChildrenSwapLogic.scheduleNext(now, TwinChildrenSwapLogic.intervalTicks());
        pair.swap();
        ServerPlayer newLower = playerOf(level, pair.lower);
        if (newLower == null) {
            return;
        }
        Component message = Component.translatable("message.twin_children.swap", newLower.getDisplayName())
                .withStyle(ChatFormatting.GOLD);
        BroadcastCommand.BroadcastMessage(lower, message);
        BroadcastCommand.BroadcastMessage(upper, message);
    }

    private static TwinChildrenSeatEntity ensureSeat(Pair pair, ServerLevel level,
            ServerPlayer lower, ServerPlayer upper) {
        if (pair.seatId != null) {
            Entity existing = level.getEntity(pair.seatId);
            if (existing instanceof TwinChildrenSeatEntity seat && seat.isAlive()) {
                return seat;
            }
        }
        TwinChildrenSeatEntity seat = SEEntities.TWIN_CHILDREN_SEAT.create(level);
        if (seat == null) {
            return null;
        }
        seat.bind(lower, upper);
        level.addFreshEntity(seat);
        pair.seatId = seat.getUUID();
        return seat;
    }

    @Nullable
    private static TwinChildrenSeatEntity findSeatOwnedBy(Player lower) {
        if (lower.level() instanceof ServerLevel serverLevel) {
            Pair pair = PAIRS.get(lower.getUUID());
            if (pair != null && pair.seatId != null) {
                Entity existing = serverLevel.getEntity(pair.seatId);
                if (existing instanceof TwinChildrenSeatEntity seat) {
                    return seat;
                }
            }
        }
        for (TwinChildrenSeatEntity seat : lower.level().getEntitiesOfClass(TwinChildrenSeatEntity.class,
                lower.getBoundingBox().inflate(2.0D, 3.0D, 2.0D))) {
            if (lower.getUUID().equals(seat.getLowerUuid())) {
                return seat;
            }
        }
        return null;
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

    private static void discardSeat(Pair pair, ServerLevel level) {
        if (pair.seatId == null) {
            return;
        }
        Entity existing = level.getEntity(pair.seatId);
        if (existing instanceof TwinChildrenSeatEntity seat) {
            seat.ejectPassengers();
            seat.discard();
        }
        pair.seatId = null;
    }

    private static void discardAllSeats(ServerLevel level) {
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof TwinChildrenSeatEntity seat) {
                seat.ejectPassengers();
                seat.discard();
            }
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
        PAIRS.remove(pair.lower);
        PAIRS.remove(pair.upper);
        discardSeat(pair, player.serverLevel());
        ServerPlayer lower = player.server.getPlayerList().getPlayer(pair.lower);
        ServerPlayer upper = player.server.getPlayerList().getPlayer(pair.upper);
        if (upper != null && upper.getVehicle() instanceof TwinChildrenSeatEntity) {
            upper.stopRiding();
        }
        if (lower != null) {
            removeHalfScale(lower);
        }
        if (upper != null) {
            removeHalfScale(upper);
        }
        if (removeModifiers) {
            WorldModifierComponent modifiers = WorldModifierComponent.KEY.get(player.serverLevel());
            modifiers.removeModifier(pair.lower, SEModifiers.TWIN_CHILDREN, false);
            modifiers.removeModifier(pair.upper, SEModifiers.TWIN_CHILDREN, true);
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
        modifiers.removeModifier(player, TraitorAndModifiers.DWARF);
        modifiers.removeModifier(player, NRModifiers.FAT);
        modifiers.removeModifier(player, NRModifiers.SKINNY);
        stripForeignScale(player);
    }

    private static void applyFixedScale(ServerPlayer player) {
        stripForeignScale(player);
        applyHalfScale(player);
    }

    private static void applyHalfScale(ServerPlayer player) {
        AttributeInstance scale = player.getAttribute(Attributes.SCALE);
        if (scale != null && !scale.hasModifier(HALF_SCALE.id())) {
            scale.addPermanentModifier(HALF_SCALE);
            player.refreshDimensions();
        }
    }

    private static void stripForeignScale(ServerPlayer player) {
        AttributeInstance scale = player.getAttribute(Attributes.SCALE);
        if (scale == null) {
            return;
        }
        scale.removeModifier(SEModifiers.TINY_MODIFIER);
        scale.removeModifier(SEModifiers.TALL_MODIFIER);
        scale.removeModifier(TraitorAndModifiers.DWARF_MODIFIER);
    }

    private static void removeHalfScale(ServerPlayer player) {
        AttributeInstance scale = player.getAttribute(Attributes.SCALE);
        if (scale != null && scale.hasModifier(HALF_SCALE.id())) {
            scale.removeModifier(HALF_SCALE);
            player.refreshDimensions();
        }
    }

    @Nullable
    private static ServerPlayer playerOf(ServerLevel level, UUID id) {
        return level.getServer().getPlayerList().getPlayer(id);
    }

    private static boolean isAlivePlayer(ServerPlayer player) {
        return GameUtils.isPlayerAliveAndSurvival(player);
    }

    static TwinChildrenAssignLogic.Faction factionOf(SRERole role) {
        if (role == null) {
            return TwinChildrenAssignLogic.Faction.INDEPENDENT_NEUTRAL;
        }
        if (role.isNeutrals() && !role.isNeutralForKiller()) {
            return TwinChildrenAssignLogic.Faction.INDEPENDENT_NEUTRAL;
        }
        if (role.isNeutralForKiller() || SREGameWorldComponent.isKillerTeamRoleStatic(role)) {
            return TwinChildrenAssignLogic.Faction.KILLER;
        }
        if (role.isInnocent()) {
            return TwinChildrenAssignLogic.Faction.INNOCENT;
        }
        return TwinChildrenAssignLogic.Faction.INDEPENDENT_NEUTRAL;
    }

    private static final class Pair {
        UUID lower;
        UUID upper;
        @Nullable
        UUID seatId;
        long nextSwapAt;

        Pair(UUID lower, UUID upper) {
            this.lower = lower;
            this.upper = upper;
        }

        UUID partnerOf(UUID id) {
            return lower.equals(id) ? upper : lower;
        }

        void swap() {
            UUID previousLower = lower;
            lower = upper;
            upper = previousLower;
        }
    }
}
