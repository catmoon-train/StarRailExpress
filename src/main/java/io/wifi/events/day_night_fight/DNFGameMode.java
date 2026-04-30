package io.wifi.events.day_night_fight;

import io.wifi.events.day_night_fight.cca.DNFPlayerComponent;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameTimeComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SRETrainWorldComponent;
import io.wifi.starrailexpress.game.modes.SREMurderGameMode;
import io.wifi.starrailexpress.network.original.AnnounceWelcomePayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.events.ModdedRoleAssigned;
import org.agmas.harpymodloader.modded_murder.PlayerRoleWeightManager;
import org.agmas.noellesroles.utils.RoleUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.HashMap;

public class DNFGameMode extends SREMurderGameMode {
    private static final int TICKS_PER_DNF_DAY = 24000;
    private static final int DAYLIGHT_TICKS = 12000;

    @Override
    public boolean canPickBodyContent() {
        return true;
    };

    @Override
    public boolean canSeeBodyContent() {
        return true;
    };

    @Override
    public boolean shouldRecordPlayerStats() {
        return false;
    }

    private int currentDay = -1;

    public DNFGameMode(ResourceLocation identifier) {
        super(identifier, 140, 6);
    }

    @Override
    public void initializeGame(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent,
            List<ServerPlayer> players) {
        Harpymodloader.refreshRoles();
        SRETrainWorldComponent.KEY.get(serverWorld).setTimeOfDay(SRETrainWorldComponent.TimeOfDay.DAY);
        gameWorldComponent.clearRoleMap();
        addPlayersToTeam(serverWorld.getServer().createCommandSourceStack(), players, "harpymodloader_game");
        executeFunction(serverWorld.getServer().createCommandSourceStack(), "harpymodloader:start_game");

        Map<Player, SRERole> roleAssignments = assignDnfRoles(serverWorld, players);
        long killerCount = roleAssignments.values().stream().filter(DNF::isDNFKiller).count();
        for (Map.Entry<Player, SRERole> entry : roleAssignments.entrySet()) {
            Player player = entry.getKey();
            SRERole role = entry.getValue();
            gameWorldComponent.addRole(player, role, false);
            role.getDefaultItems().forEach(item -> player.getInventory().placeItemBackInInventory(item));
        }
        gameWorldComponent.syncRoles();

        for (ServerPlayer player : players) {
            SRERole role = gameWorldComponent.getRole(player);
            PlayerRoleWeightManager.addWeight(player, PlayerRoleWeightManager.getRoleType(role), 1);
            ServerPlayNetworking.send(player, new AnnounceWelcomePayload(role.getIdentifier().toString(),
                    (int) killerCount, players.size() - (int) killerCount));
            ModdedRoleAssigned.EVENT.invoker().assignModdedRole(player, role);
        }

        for (ServerPlayer player : players) {
            SRERole role = gameWorldComponent.getRole(player);
            DNFPlayerComponent component = DNFPlayerComponent.KEY.get(player);
            component.init();
            component.startDnfDay(player, 0, role == DNFRoles.CHEF);
            if (role == DNFRoles.CHEF) {
                component.giveInitialCafeteriaFood(player);
            }
        }
        currentDay = 0;
        Harpymodloader.FORCED_MODDED_ROLE.clear();
        Harpymodloader.FORCED_MODDED_ROLE_FLIP.clear();
        Harpymodloader.FORCED_MODDED_MODIFIER.clear();
        PlayerRoleWeightManager.ForcePlayerTeam.clear();
    }

    @Override
    public void tickServerGameLoop(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent) {
        tickDayNightCycle(serverWorld, gameWorldComponent);
        super.tickServerGameLoop(serverWorld, gameWorldComponent);
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider wrapperLookup) {
        tag.putInt("CurrentDnfDay", currentDay);
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider wrapperLookup) {
        currentDay = tag.getInt("CurrentDnfDay");
    }

    private Map<Player, SRERole> assignDnfRoles(ServerLevel serverWorld, List<ServerPlayer> players) {
        ArrayList<ServerPlayer> unassigned = new ArrayList<>(players);
        Collections.shuffle(unassigned);

        int maxSpecials = Math.max(1, Math.min(13, players.size() - Math.max(1, players.size() / 2)));

        Map<Player, SRERole> roleAssignments = new HashMap<>();
        for (Player player : players) {
            roleAssignments.put(player, null);
        }
        Map<UUID, SRERole> forcedRoles = new HashMap<>(Harpymodloader.FORCED_MODDED_ROLE_FLIP);

        int killerCount = Math.min(4, Math.max(1, players.size() / 10));
        int chefCount = players.size() >= 6 ? 1 : 0;
        int soldierCount = 2;
        int psychologistCount = 2;
        int locksmithCount = 4;
        // DNFRoles.KILLER;
        // DNFRoles.CHEF;
        // DNFRoles.SOLDIER;
        // DNFRoles.PSYCHOLOGIST;
        // DNFRoles.LOCKSMITH;
        for (Map.Entry<UUID, SRERole> entry : forcedRoles.entrySet()) {
            Player player = serverWorld.getPlayerByUUID(entry.getKey());
            if (player != null) {
                SRERole role = entry.getValue();
                if (role != null) {
                    roleAssignments.put(player, role);
                    if (RoleUtils.compareRole(role, DNFRoles.KILLER)) {
                        killerCount--;
                    } else if (RoleUtils.compareRole(role, DNFRoles.CHEF)) {
                        chefCount--;
                    } else if (RoleUtils.compareRole(role, DNFRoles.SOLDIER)) {
                        soldierCount--;
                    } else if (RoleUtils.compareRole(role, DNFRoles.PSYCHOLOGIST)) {
                        psychologistCount--;
                    } else if (RoleUtils.compareRole(role, DNFRoles.LOCKSMITH)) {
                        locksmithCount--;
                    }
                }
            }
        }
        unassigned.removeIf((p) -> {
            return (roleAssignments.getOrDefault(p, null) != null);
        });
        ArrayList<SRERole> roles = new ArrayList<>();
        addRoles(roles, DNFRoles.KILLER, killerCount, maxSpecials);
        addRoles(roles, DNFRoles.CHEF, chefCount, maxSpecials);
        addRoles(roles, DNFRoles.SOLDIER, soldierCount, maxSpecials);
        addRoles(roles, DNFRoles.PSYCHOLOGIST, psychologistCount, maxSpecials);
        addRoles(roles, DNFRoles.LOCKSMITH, locksmithCount, maxSpecials);
        while (roles.size() < unassigned.size()) {
            roles.add(DNFRoles.CIVILIAN);
        }
        Collections.shuffle(roles);

        HashMap<Player, SRERole> assignments = new HashMap<>();
        for (int i = 0; i < unassigned.size(); i++) {
            assignments.put(unassigned.get(i), roles.get(i));
        }
        return assignments;
    }

    private static void addRoles(ArrayList<SRERole> roles, SRERole role, int count, int maxSpecials) {
        for (int i = 0; i < count && roles.size() < maxSpecials; i++) {
            roles.add(role);
        }
    }

    private void tickDayNightCycle(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent) {
        SREGameTimeComponent time = SREGameTimeComponent.KEY.get(serverWorld);
        int elapsed = Math.max(0, time.getResetTime() - time.getTime());
        int day = elapsed / TICKS_PER_DNF_DAY;
        int dayTick = elapsed % TICKS_PER_DNF_DAY;
        SRETrainWorldComponent.KEY.get(serverWorld).setTimeOfDay(dayTick < DAYLIGHT_TICKS
                ? SRETrainWorldComponent.TimeOfDay.DAY
                : SRETrainWorldComponent.TimeOfDay.NIGHT);

        if (day == currentDay) {
            return;
        }
        currentDay = day;
        for (ServerPlayer player : serverWorld.players()) {
            SRERole role = gameWorldComponent.getRole(player);
            if (role == null) {
                continue;
            }
            DNFPlayerComponent component = DNFPlayerComponent.KEY.get(player);
            component.startDnfDay(player, day, role == DNFRoles.CHEF);
            if (role == DNFRoles.CHEF) {
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                        "message.dnf.chef.new_day", day + 1).withStyle(net.minecraft.ChatFormatting.DARK_GREEN),
                        false);
            }
        }
    }
}
