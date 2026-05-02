package io.wifi.events.day_night_fight;

import io.wifi.events.day_night_fight.cca.DNFPlayerComponent;
import io.wifi.events.day_night_fight.cca.DNFWorldComponent;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameRoundEndComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SRETrainWorldComponent;
import io.wifi.starrailexpress.game.modes.SREMurderGameMode;
import io.wifi.starrailexpress.network.original.AnnounceWelcomePayload;
import io.wifi.starrailexpress.util.SREItemUtils;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.events.ModdedRoleAssigned;
import org.agmas.harpymodloader.modded_murder.PlayerRoleWeightManager;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.utils.RoleUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.HashMap;

public class DNFGameMode extends SREMurderGameMode {
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

    private int currentDay = 0;
    private Phase currentPhase = Phase.DAY;
    private int phaseTicks = 0;

    public DNFGameMode(ResourceLocation identifier) {
        super(identifier, 1000, 6);
    }

    @Override
    public void initializeGame(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent,
            List<ServerPlayer> players) {
        Harpymodloader.refreshRoles();
        SRETrainWorldComponent.KEY.get(serverWorld).setTimeOfDay(SRETrainWorldComponent.TimeOfDay.DAY);
        DNFWorldComponent dnfWorld = DNFWorldComponent.KEY.get(serverWorld);
        dnfWorld.resetRoundState();
        dnfWorld.setPhase(0, false);
        gameWorldComponent.clearRoleMap();
        addPlayersToTeam(serverWorld.getServer().createCommandSourceStack(), players, "harpymodloader_game");
        executeFunction(serverWorld.getServer().createCommandSourceStack(), "harpymodloader:start_game");

        Map<Player, SRERole> roleAssignments = assignDnfRoles(serverWorld, players);
        long killerCount = roleAssignments.values().stream().filter(DNF::isDNFAntagonist).count();
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
            ItemStack defaultInstance = Items.BUNDLE.getDefaultInstance();
            player.addItem(defaultInstance);
            player.addEffect(new MobEffectInstance(ModEffects.MOOD_DRAIN_IMMUNITY, Integer.MAX_VALUE, 10, false, false,false));
            DNFPlayerComponent component = DNFPlayerComponent.KEY.get(player);
            component.init();
            component.startDnfDay(player, 0, role == DNFRoles.CHEF);
            DNFItems.ensureDefaultClothes(player);
            DNF.applyPhaseState(player, false);
        }
        if (!players.isEmpty()) {
            DNFItems.seedInitialFood(players.get(0));
        }
        currentDay = 0;
        currentPhase = Phase.DAY;
        phaseTicks = 0;
        updateWorldTime(serverWorld);
        Harpymodloader.FORCED_MODDED_ROLE.clear();
        Harpymodloader.FORCED_MODDED_ROLE_FLIP.clear();
        Harpymodloader.FORCED_MODDED_MODIFIER.clear();
        PlayerRoleWeightManager.ForcePlayerTeam.clear();
    }

    @Override
    public void tickServerGameLoop(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent) {
        tickDayNightCycle(serverWorld, gameWorldComponent);
        checkDnfEndings(serverWorld, gameWorldComponent);
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider wrapperLookup) {
        tag.putInt("CurrentDnfDay", currentDay);
        tag.putString("CurrentDnfPhase", currentPhase.name());
        tag.putInt("DnfPhaseTicks", phaseTicks);
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider wrapperLookup) {
        currentDay = tag.getInt("CurrentDnfDay");
        currentPhase = tag.contains("CurrentDnfPhase")
                ? Phase.valueOf(tag.getString("CurrentDnfPhase"))
                : Phase.DAY;
        phaseTicks = tag.getInt("DnfPhaseTicks");
    }

    private Map<Player, SRERole> assignDnfRoles(ServerLevel serverWorld, List<ServerPlayer> players) {
        ArrayList<ServerPlayer> unassigned = new ArrayList<>(players);
        Collections.shuffle(unassigned);

        Map<Player, SRERole> roleAssignments = new HashMap<>();
        for (Player player : players) {
            roleAssignments.put(player, null);
        }
        Map<UUID, SRERole> forcedRoles = new HashMap<>(Harpymodloader.FORCED_MODDED_ROLE_FLIP);

        int playerCount = players.size();
        double scale = playerCount >= 48 ? 1.0 : Math.max(0.0, playerCount / 48.0);
        int maniacCount = scaledCount(playerCount, 2, 12, scale);
        int killerCount = scaledCount(playerCount, 4, 8, scale);
        int soldierCount = scaledCount(playerCount, 2, 8, scale);
        int chefCount = playerCount >= 6 ? 1 : 0;
        int poisonerCount = playerCount >= 8 ? 1 : 0;
        int psychologistCount = scaledCount(playerCount, 2, 12, scale);
        int locksmithCount = scaledCount(playerCount, 4, 10, scale);
        for (Map.Entry<UUID, SRERole> entry : forcedRoles.entrySet()) {
            Player player = serverWorld.getPlayerByUUID(entry.getKey());
            if (player != null) {
                SRERole role = entry.getValue();
                if (role != null) {
                    roleAssignments.put(player, role);
                    if (RoleUtils.compareRole(role, DNFRoles.MANIAC)) {
                        maniacCount--;
                    } else if (RoleUtils.compareRole(role, DNFRoles.KILLER)) {
                        killerCount--;
                    } else if (RoleUtils.compareRole(role, DNFRoles.POISONER)) {
                        poisonerCount--;
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
        int maxSpecials = Math.min(unassigned.size(), Math.max(0,
                maniacCount + killerCount + soldierCount + chefCount + poisonerCount + psychologistCount
                        + locksmithCount));
        addRoles(roles, DNFRoles.MANIAC, Math.max(0, maniacCount), maxSpecials);
        addRoles(roles, DNFRoles.KILLER, killerCount, maxSpecials);
        addRoles(roles, DNFRoles.POISONER, Math.max(0, poisonerCount), maxSpecials);
        addRoles(roles, DNFRoles.CHEF, chefCount, maxSpecials);
        addRoles(roles, DNFRoles.SOLDIER, soldierCount, maxSpecials);
        addRoles(roles, DNFRoles.PSYCHOLOGIST, psychologistCount, maxSpecials);
        addRoles(roles, DNFRoles.LOCKSMITH, locksmithCount, maxSpecials);
        while (roles.size() < unassigned.size()) {
            roles.add(DNFRoles.CIVILIAN);
        }
        Collections.shuffle(roles);

        HashMap<Player, SRERole> assignments = new HashMap<>(roleAssignments);
        for (int i = 0; i < unassigned.size(); i++) {
            assignments.put(unassigned.get(i), roles.get(i));
        }
        return assignments;
    }

    private static int scaledCount(int playerCount, int baseline, int minPlayers, double scale) {
        if (playerCount < minPlayers) {
            return 0;
        }
        if (playerCount >= 48) {
            return baseline;
        }
        return Math.max(1, (int) Math.round(baseline * scale));
    }

    private static void addRoles(ArrayList<SRERole> roles, SRERole role, int count, int maxSpecials) {
        for (int i = 0; i < count && roles.size() < maxSpecials; i++) {
            roles.add(role);
        }
    }

    private void tickDayNightCycle(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent) {
        if (serverWorld.getServer().tickRateManager().isFrozen()) {
            return;
        }

        phaseTicks++;
        if (phaseTicks < getCurrentPhaseLength()) {
            return;
        }

        phaseTicks = 0;
        DNFWorldComponent dnfWorld = DNFWorldComponent.KEY.get(serverWorld);
        if (currentPhase == Phase.DAY) {
            currentPhase = Phase.NIGHT;
            dnfWorld.setPhase(currentDay, true);
            updateWorldTime(serverWorld);
            announcePhase(serverWorld, "message.dnf.phase.night", currentDay + 1);
            for (ServerPlayer player : serverWorld.players()) {
                DNFPlayerComponent.KEY.get(player).startDnfNight(player, currentDay);
                DNF.updateNightTools(player);
                DNF.applyPhaseState(player, true);
            }
            return;
        }

        if (currentDay >= 6 && !dnfWorld.isTrueEndingTriggered()) {
            for (ServerPlayer player : serverWorld.players()) {
                if (DNF.isDnfAlive(player)) {
                    DNFItems.settleClothesEndOfDay(player);
                }
            }
            triggerDnfEnding(serverWorld, "dnf_survival", "game.win.star.dnf_survival",
                    "message.dnf.ending.survival", 0x719E5B);
            return;
        }

        currentPhase = Phase.DAY;
        currentDay++;
        dnfWorld.setPhase(currentDay, false);
        dnfWorld.rollWaterPoisonToToday();
        dnfWorld.clearVote();
        updateWorldTime(serverWorld);
        announcePhase(serverWorld, "message.dnf.phase.day", currentDay + 1);
        for (ServerPlayer player : serverWorld.players()) {
            SRERole role = gameWorldComponent.getRole(player);
            if (role == null) {
                continue;
            }
            DNFPlayerComponent component = DNFPlayerComponent.KEY.get(player);
            component.startDnfDay(player, currentDay, role == DNFRoles.CHEF);
            if (DNF.isDnfAlive(player)) {
                DNFItems.settleClothesEndOfDay(player);
            }
            if (role == DNFRoles.CHEF) {
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                        "message.dnf.chef.new_day", currentDay + 1).withStyle(net.minecraft.ChatFormatting.DARK_GREEN),
                        false);
            }
            DNF.updateNightTools(player);
            DNF.applyPhaseState(player, false);
        }
    }

    private void checkDnfEndings(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent) {
        if (gameWorldComponent.getGameStatus() != SREGameWorldComponent.GameStatus.ACTIVE) {
            return;
        }
        boolean nonAntagonistAlive = false;
        for (ServerPlayer player : serverWorld.players()) {
            if (DNF.isDnfAlive(player) && !DNF.isDNFAntagonist(gameWorldComponent.getRole(player))) {
                nonAntagonistAlive = true;
                break;
            }
        }
        if (!nonAntagonistAlive) {
            triggerDnfEnding(serverWorld, "dnf_wipeout", "game.win.star.dnf_wipeout",
                    "message.dnf.ending.wipeout", 0x7A1414);
        }
    }

    private void triggerDnfEnding(ServerLevel serverWorld, String id, String titleKey, String subtitleKey, int color) {
        SREGameRoundEndComponent roundEnd = SREGameRoundEndComponent.KEY.get(serverWorld);
        roundEnd.CustomWinnerTitle = net.minecraft.network.chat.Component.translatable(titleKey);
        roundEnd.CustomWinnerSubtitle = net.minecraft.network.chat.Component.translatable(subtitleKey);
        RoleUtils.customWinnerWin(serverWorld, id, color);
    }

    private int getCurrentPhaseLength() {
        if (currentPhase == Phase.NIGHT) {
            return DNF.NIGHT_TICKS;
        }
        return currentDay == 0 ? DNF.FIRST_DAYLIGHT_TICKS : DNF.DAYLIGHT_TICKS;
    }

    private void updateWorldTime(ServerLevel serverWorld) {
        SRETrainWorldComponent.KEY.get(serverWorld).setTimeOfDay(currentPhase == Phase.DAY
                ? SRETrainWorldComponent.TimeOfDay.DAY
                : SRETrainWorldComponent.TimeOfDay.NIGHT);
    }

    private void announcePhase(ServerLevel serverWorld, String key, int displayDay) {
        serverWorld.getServer().getPlayerList().broadcastSystemMessage(
                net.minecraft.network.chat.Component.translatable(key, displayDay), false);
    }

    private enum Phase {
        DAY,
        NIGHT
    }
}
