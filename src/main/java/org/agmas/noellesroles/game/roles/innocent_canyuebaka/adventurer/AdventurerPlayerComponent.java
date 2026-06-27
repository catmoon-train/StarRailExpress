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

    /** 节流提示信息的间隔（仅用于避免持续性危害刷屏，不影响免疫本身）。 */
    private static final int MESSAGE_COOLDOWN_TICKS = 70; // 3.5 s
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
    public int waypointCooldown;
    public int waypointTimer; // remaining ticks until auto-off, 0 = inactive
    /** Per-death-reason message throttle remaining (ticks); immunity itself is unconditional. */
    public final Map<ResourceLocation, Integer> messageCooldowns = new HashMap<>();

    public AdventurerPlayerComponent(Player player) {
        this.player = player;
    }

    @Override public Player getPlayer() { return player; }
    @Override public boolean shouldSyncWith(ServerPlayer p) { return p == player; }
    public void sync() { KEY.sync(player); }

    @Override
    public void init() {
        waypointCooldown = 0;
        waypointTimer = 0;
        messageCooldowns.clear();
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

        messageCooldowns.replaceAll((k, v) -> Math.max(0, v - 1));
        messageCooldowns.values().removeIf(v -> v <= 0);

        if (player.tickCount % 20 == 0) sync();
    }

    /**
     * 冒险家无条件免疫场景方块的环境致死：本方法始终返回 true（阻止该死亡）。
     * 仅按死因节流提示信息，避免持续性危害（如喷火装置、雾区）反复触发刷屏。
     * @return 始终为 true，表示应阻止此环境死亡。
     */
    public boolean blockEnvironmentalDeath(ResourceLocation deathReason) {
        if (!messageCooldowns.containsKey(deathReason)) {
            messageCooldowns.put(deathReason, MESSAGE_COOLDOWN_TICKS);
            if (player instanceof ServerPlayer sp) {
                sp.displayClientMessage(
                        Component.translatable("message.noellesroles.adventurer.immunity")
                                .withStyle(ChatFormatting.GREEN), true);
            }
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
        tag.putInt("wpCd", waypointCooldown);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider p) {
        waypointCooldown = tag.getInt("wpCd");
    }

    @Override public void writeToNbt(CompoundTag tag, HolderLookup.Provider p) {}
    @Override public void readFromNbt(CompoundTag tag, HolderLookup.Provider p) {}
}
