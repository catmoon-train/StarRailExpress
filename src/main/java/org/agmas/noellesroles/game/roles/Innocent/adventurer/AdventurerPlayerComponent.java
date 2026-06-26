package org.agmas.noellesroles.game.roles.innocent.adventurer;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.data.WaypointVisibilityManager;
import io.wifi.starrailexpress.network.PacketTracker;
import io.wifi.starrailexpress.network.packet.SyncWaypointVisibilityPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.*;

public final class AdventurerPlayerComponent implements RoleComponent, ServerTickingComponent {

    public static final ComponentKey<AdventurerPlayerComponent> KEY =
            ComponentRegistry.getOrCreate(Noellesroles.id("adventurer"), AdventurerPlayerComponent.class);

    private static final int MAX_IMMUNITIES = 3;
    private static final int IMMUNITY_COOLDOWN_TICKS = 70; // 3.5 s
    private static final int WAYPOINT_COOLDOWN_TICKS = 120 * 20;
    private static final int WAYPOINT_DURATION_TICKS = 180 * 20; // 3 minutes

    /** Death reasons the adventurer is immune to. */
    public static final Set<ResourceLocation> ENVIRONMENTAL_DEATHS = Set.of(
            GameConstants.DeathReasons.MANHOLE_SUFFOCATION,
            GameConstants.DeathReasons.STALACTITE_IMPALE,
            GameConstants.DeathReasons.FLAMETHROWER_BURNED,
            GameConstants.DeathReasons.BOULDER_CRUSH,
            GameConstants.DeathReasons.INCINERATOR_PUSHED,
            GameConstants.DeathReasons.FROZEN,
            GameConstants.DeathReasons.THIRST,
            GameConstants.DeathReasons.STARVED,
            GameConstants.DeathReasons.DROWNED,
            GameConstants.DeathReasons.SELF_LOST,
            GameConstants.DeathReasons.ANCIENT_BITE);

    public static boolean isEnvironmentalDeath(ResourceLocation deathReason) {
        return ENVIRONMENTAL_DEATHS.contains(deathReason);
    }

    private final Player player;
    public int immunities;
    public int waypointCooldown;
    public int waypointTimer; // remaining ticks until auto-off, 0 = inactive
    /** Per-death-reason cooldown remaining (ticks). */
    public final Map<ResourceLocation, Integer> immunityCooldowns = new HashMap<>();

    public AdventurerPlayerComponent(Player player) {
        this.player = player;
    }

    @Override public Player getPlayer() { return player; }
    @Override public boolean shouldSyncWith(ServerPlayer p) { return p == player; }
    public void sync() { KEY.sync(player); }

    @Override
    public void init() {
        immunities = MAX_IMMUNITIES;
        waypointCooldown = 0;
        waypointTimer = 0;
        immunityCooldowns.clear();
        sync();
    }

    @Override
    public void clear() { init(); }

    @Override
    public void serverTick() {
        if (!SREGameWorldComponent.KEY.get(player.level()).isRole(player, ModRoles.ADVENTURER)) return;

        if (waypointCooldown > 0) waypointCooldown--;

        // Waypoint auto-off after 3 minutes
        if (waypointTimer > 0) {
            waypointTimer--;
            if (waypointTimer == 0) {
                waypointOff(false);
            }
        }

        immunityCooldowns.replaceAll((k, v) -> Math.max(0, v - 1));
        immunityCooldowns.values().removeIf(v -> v <= 0);

        if (player.tickCount % 20 == 0) sync();
    }

    /**
     * Attempt to consume an immunity charge.
     * @return true if death should be blocked, false otherwise.
     */
    public boolean consumeImmunity(ResourceLocation deathReason) {
        if (immunities <= 0 || immunityCooldowns.containsKey(deathReason)) return false;
        immunities--;
        immunityCooldowns.put(deathReason, IMMUNITY_COOLDOWN_TICKS);
        sync();
        if (player instanceof ServerPlayer sp) {
            sp.displayClientMessage(
                    Component.translatable("message.noellesroles.adventurer.immunity", immunities)
                            .withStyle(ChatFormatting.GREEN), true);
        }
        return true;
    }

    /** Toggle map waypoints. */
    public void useWaypointAbility() {
        if (!(player instanceof ServerPlayer sp)) return;
        if (waypointCooldown > 0) {
            sp.displayClientMessage(
                    Component.translatable("message.noellesroles.adventurer.waypoint_cooldown",
                            (waypointCooldown + 19) / 20).withStyle(ChatFormatting.RED), true);
            return;
        }

        waypointCooldown = WAYPOINT_COOLDOWN_TICKS;
        boolean currentlyVisible = WaypointVisibilityManager.get(sp.serverLevel())
                .getWaypointsVisibility();

        if (!currentlyVisible) {
            // Turn waypoints ON
            setWaypointVisibility(sp, true);
            waypointTimer = WAYPOINT_DURATION_TICKS;
            sp.displayClientMessage(
                    Component.translatable("message.noellesroles.adventurer.waypoint_on")
                            .withStyle(ChatFormatting.GOLD), true);
        } else {
            // Turn waypoints OFF
            waypointOff(true);
        }
        sync();
    }

    /** Turn waypoints off and optionally notify the player. */
    private void waypointOff(boolean notify) {
        waypointTimer = 0;
        if (player instanceof ServerPlayer sp) {
            setWaypointVisibility(sp, false);
            if (notify) {
                sp.displayClientMessage(
                        Component.translatable("message.noellesroles.adventurer.waypoint_off")
                                .withStyle(ChatFormatting.GRAY), true);
            }
        }
    }

    private static void setWaypointVisibility(ServerPlayer sp, boolean visible) {
        WaypointVisibilityManager.get(sp.serverLevel()).setWaypointsVisibility(visible);
        for (ServerPlayer target : sp.serverLevel().players()) {
            PacketTracker.sendToClient(target, new SyncWaypointVisibilityPacket(visible));
        }
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider p) {
        tag.putInt("immunities", immunities);
        tag.putInt("wpCd", waypointCooldown);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider p) {
        immunities = tag.getInt("immunities");
        waypointCooldown = tag.getInt("wpCd");
    }

    @Override public void writeToNbt(CompoundTag tag, HolderLookup.Provider p) {}
    @Override public void readFromNbt(CompoundTag tag, HolderLookup.Provider p) {}
}
