package org.agmas.noellesroles.game.modes.repair;

import io.wifi.starrailexpress.api.GameMode;
import io.wifi.starrailexpress.cca.SREGameRoundEndComponent;
import io.wifi.starrailexpress.cca.SREGameTimeComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.data.MapConfig;
import io.wifi.starrailexpress.network.original.AnnounceWelcomePayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.content.block_entity.HunterCageBlockEntity;
import org.agmas.noellesroles.init.ModBlocks;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.packet.OpenRepairRoleSelectionS2CPacket;
import org.agmas.noellesroles.role.ModRoles;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RepairEscapeGameMode extends GameMode {
    private long selectionEndTick;
    private boolean rolesFinalized;

    public RepairEscapeGameMode(ResourceLocation identifier) {
        super(identifier, 12, 2);
    }

    @Override
    public boolean shouldRecordPlayerStats() {
        return true;
    }

    @Override
    public boolean hasMood() {
        return false;
    }

    @Override
    public void initializeGame(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent,
            List<ServerPlayer> players) {
        RepairModeState.reset(serverWorld);
        RepairEventSystem.reset(serverWorld);
        rolesFinalized = false;
        selectionEndTick = serverWorld.getGameTime() + 30 * 20L;

        ArrayList<ServerPlayer> shuffled = new ArrayList<>(players);
        Collections.shuffle(shuffled);
        int hunterCount = hunterCount(shuffled.size());
        int neutralCount = neutralCount(shuffled.size());
        int forcedHunters = 0;
        int forcedNeutrals = 0;
        for (ServerPlayer player : shuffled) {
            var forced = RepairForcedRoleState.forcedRole(player.getUUID());
            if (forced.isPresent()) {
                if (forced.get().faction == RepairRoleDefinition.Faction.HUNTER) {
                    forcedHunters++;
                } else if (forced.get().faction == RepairRoleDefinition.Faction.NEUTRAL) {
                    forcedNeutrals++;
                }
            }
        }
        int remainingHunters = Math.max(0, hunterCount - forcedHunters);
        int remainingNeutrals = Math.max(0, neutralCount - forcedNeutrals);
        List<String> playerNames = shuffled.stream().map(player -> player.getGameProfile().getName()).toList();

        for (ServerPlayer player : shuffled) {
            RepairRoleDatabase.loadInto(player);
            var component = ModComponents.REPAIR_ROLES.get(player);
            component.init();
            var forcedRole = RepairForcedRoleState.forcedRole(player.getUUID()).orElse(null);
            if (forcedRole != null) {
                component.forcedRole = forcedRole.id;
                component.setSelectedRole(forcedRole);
            }
            RepairRoleDefinition.Faction faction;
            if (forcedRole != null) {
                faction = forcedRole.faction;
            } else if (remainingHunters > 0) {
                remainingHunters--;
                faction = RepairRoleDefinition.Faction.HUNTER;
            } else if (remainingNeutrals > 0) {
                remainingNeutrals--;
                faction = RepairRoleDefinition.Faction.NEUTRAL;
            } else {
                faction = RepairRoleDefinition.Faction.SURVIVOR;
            }
            component.selectionEndTick = selectionEndTick;
            component.sync();

            gameWorldComponent.addRole(player, switch (faction) {
                case HUNTER -> ModRoles.REPAIR_HUNTER;
                case NEUTRAL -> ModRoles.REPAIR_NEUTRAL;
                case SURVIVOR -> ModRoles.REPAIR_SURVIVOR;
            }, false);
            SREPlayerShopComponent.KEY.get(player).setBalance(startingCoins(faction));
            giveModeItems(player, faction, serverWorld.random);
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40 * 20, 10, false, false, true));
            ServerPlayNetworking.send(player,
                    new AnnounceWelcomePayload(gameWorldComponent.getRole(player).getIdentifier().toString(),
                            hunterCount, shuffled.size() - hunterCount));
            ServerPlayNetworking.send(player,
                    new OpenRepairRoleSelectionS2CPacket(faction.id(), selectionEndTick, playerNames));
            player.displayClientMessage(Component.translatable("message.noellesroles.repair.select_role", 40)
                    .withStyle(ChatFormatting.GOLD), false);
        }
        RepairArenaBuilder.prepare(serverWorld, shuffled);
        gameWorldComponent.syncRoles();
    }

    private static int hunterCount(int playerCount) {
        return playerCount >= 11 ? 2 : 1;
    }

    private static int neutralCount(int playerCount) {
        if (playerCount >= 11) {
            return 2;
        }
        return playerCount >= 6 ? 1 : 0;
    }

    private static void giveModeItems(ServerPlayer player, RepairRoleDefinition.Faction faction, RandomSource random) {
        switch (faction) {
            case HUNTER -> {
                player.addItem(new ItemStack(ModItems.HUNTER_WEAPON));

            }
            case NEUTRAL -> {
                player.addItem(new ItemStack(ModItems.SPARE_PARTS));
            }
            case SURVIVOR -> {
                player.addItem(new ItemStack(ModItems.REPAIR_TOOLBOX));
                ItemStack parts = new ItemStack(ModItems.SPARE_PARTS);
                parts.setCount(1 + random.nextInt(2));
                player.addItem(parts);
                if (random.nextBoolean()) {
                    player.addItem(new ItemStack(ModItems.RESCUE_FLARE));
                }
            }
        }
    }

    private static int startingCoins(RepairRoleDefinition.Faction faction) {
        return switch (faction) {
            case HUNTER -> 60;
            case NEUTRAL -> 45;
            case SURVIVOR -> 35;
        };
    }

    @Override
    public void tickServerGameLoop(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent) {
        if (!rolesFinalized) {
            RepairArenaBuilder.tickSelection(serverWorld);
            if (serverWorld.getGameTime() % 40L == 0L) {
                reopenRoleSelection(serverWorld, gameWorldComponent);
            }
        }
        if (!rolesFinalized && serverWorld.getGameTime() >= selectionEndTick) {
            finalizeSelectedRoles(serverWorld, gameWorldComponent);
        }

        applyRoleTickEffects(serverWorld, gameWorldComponent);
        if (rolesFinalized) {
            RepairEventSystem.tick(serverWorld);
            RepairSearchState.tick(serverWorld);
        }
        tickDownedAndCarriedPlayers(serverWorld);
        if (serverWorld.getGameTime() % 20 == 0) {
            updateRepairHudState(serverWorld, gameWorldComponent);
        }
        if (!rolesFinalized) {
            return;
        }

        GameUtils.WinStatus winStatus = GameUtils.WinStatus.NONE;
        int activeSurvivors = 0;
        int escapedSurvivors = 0;
        int livingHunters = 0;

        for (ServerPlayer player : serverWorld.players()) {
            var component = ModComponents.REPAIR_ROLES.get(player);
            if (player.getTags().contains(RepairModeState.NEUTRAL_WIN_TAG)) {
                var roundEnd = SREGameRoundEndComponent.KEY.get(serverWorld);
                roundEnd.CustomWinnerID = component.activeRole;
                roundEnd.CustomWinnerPlayers.add(player.getUUID());
                winStatus = GameUtils.WinStatus.CUSTOM;
                break;
            }

            boolean hunter = RepairRoleDefinition.byId(component.activeRole)
                    .map(role -> role.faction == RepairRoleDefinition.Faction.HUNTER)
                    .orElse(gameWorldComponent.isRole(player, ModRoles.REPAIR_HUNTER));
            if (hunter) {
                if (!GameUtils.isPlayerEliminated(player)) {
                    livingHunters++;
                }
                continue;
            }

            boolean survivor = RepairRoleDefinition.byId(component.activeRole)
                    .map(role -> role.faction == RepairRoleDefinition.Faction.SURVIVOR)
                    .orElse(gameWorldComponent.isRole(player, ModRoles.REPAIR_SURVIVOR));
            if (!survivor) {
                continue;
            }
            if (player.getTags().contains(RepairModeState.ESCAPED_TAG)) {
                escapedSurvivors++;
            } else if (!GameUtils.isPlayerEliminated(player)) {
                activeSurvivors++;
            }
        }

        if (winStatus == GameUtils.WinStatus.NONE) {
            if (escapedSurvivors > 0 && activeSurvivors == 0) {
                winStatus = GameUtils.WinStatus.PASSENGERS;
            } else if (activeSurvivors == 0) {
                winStatus = GameUtils.WinStatus.KILLERS;
            } else if (livingHunters == 0) {
                winStatus = GameUtils.WinStatus.PASSENGERS;
            } else if (!SREGameTimeComponent.KEY.get(serverWorld).hasTime()) {
                winStatus = GameUtils.WinStatus.KILLERS;
            }
        }

        if (winStatus != GameUtils.WinStatus.NONE
                && gameWorldComponent.getGameStatus() == SREGameWorldComponent.GameStatus.ACTIVE) {
            SREGameRoundEndComponent.KEY.get(serverWorld).setRoundEndData(serverWorld.players(), winStatus);
            GameUtils.stopGame(serverWorld);
        }
    }

    private void reopenRoleSelection(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent) {
        List<String> playerNames = serverWorld.players().stream()
                .map(player -> player.getGameProfile().getName())
                .toList();
        for (ServerPlayer player : serverWorld.players()) {
            ServerPlayNetworking.send(player,
                    new OpenRepairRoleSelectionS2CPacket(selectionFaction(player, gameWorldComponent).id(),
                            selectionEndTick, playerNames));
        }
    }

    private static RepairRoleDefinition.Faction selectionFaction(ServerPlayer player, SREGameWorldComponent gameWorldComponent) {
        if (gameWorldComponent.isRole(player, ModRoles.REPAIR_HUNTER)) {
            return RepairRoleDefinition.Faction.HUNTER;
        }
        if (gameWorldComponent.isRole(player, ModRoles.REPAIR_NEUTRAL)) {
            return RepairRoleDefinition.Faction.NEUTRAL;
        }
        return RepairRoleDefinition.Faction.SURVIVOR;
    }

    private void finalizeSelectedRoles(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent) {
        rolesFinalized = true;
        RepairArenaBuilder.finishSelection(serverWorld);
        MapConfig.RepairConfig repairConfig = RepairMapRuntimeConfig.current(serverWorld).orElse(null);
        int hunterSpawnIndex = 0;
        int survivorSpawnIndex = 0;
        for (ServerPlayer player : serverWorld.players()) {
            var component = ModComponents.REPAIR_ROLES.get(player);
            RepairRoleDefinition.Faction faction = gameWorldComponent.isRole(player, ModRoles.REPAIR_HUNTER)
                    ? RepairRoleDefinition.Faction.HUNTER
                    : gameWorldComponent.isRole(player, ModRoles.REPAIR_NEUTRAL)
                            ? RepairRoleDefinition.Faction.NEUTRAL
                            : RepairRoleDefinition.Faction.SURVIVOR;
            RepairRoleDefinition role = RepairForcedRoleState.forcedRole(player.getUUID())
                    .orElseGet(() -> component.selectedRole(faction));
            component.activeRole = role.id;
            component.neutralTaskProgress = 0;
            component.neutralTaskCompleted = false;
            component.neutralTaskNeeded = neutralTaskGoal(role.id);
            component.sync();
            gameWorldComponent.addRole(player, role.sreRole(), false);
            giveRoleSkillItem(player, role);
            if (role.faction == RepairRoleDefinition.Faction.HUNTER) {
                teleportToConfiguredSpawn(player, repairConfig == null ? null : repairConfig.hunterSpawns, hunterSpawnIndex++);
            } else {
                teleportToConfiguredSpawn(player, repairConfig == null ? null : repairConfig.survivorSpawns, survivorSpawnIndex++);
            }
            player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
            player.displayClientMessage(Component.translatable("message.noellesroles.repair.role_locked", role.displayName())
                    .withStyle(ChatFormatting.GREEN), false);
        }
        gameWorldComponent.syncRoles();
    }

    private static void teleportToConfiguredSpawn(ServerPlayer player, List<MapConfig.Pos> spawns, int index) {
        if (spawns == null || spawns.isEmpty()) {
            return;
        }
        BlockPos pos = spawns.get(Math.floorMod(index, spawns.size())).toBlockPos();
        player.teleportTo(player.serverLevel(), pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D,
                player.getYRot(), player.getXRot());
    }

    private static int neutralTaskGoal(String roleId) {
        return switch (roleId) {
            case "archivist" -> RepairModeState.ARCHIVIST_TASK_NEEDED;
            case "saboteur" -> RepairModeState.SABOTEUR_TASK_NEEDED;
            case "collector" -> RepairModeState.COLLECTOR_TASK_NEEDED;
            default -> 0;
        };
    }

    private static void giveRoleSkillItem(ServerPlayer player, RepairRoleDefinition role) {
        switch (role.id) {
            case "warden" -> {
                player.addItem(new ItemStack(ModItems.HUNTER_JAMMER));
                player.addItem(new ItemStack(ModBlocks.HUNTER_SNARE.asItem(), 2));
            }
            case "brute" -> {
                player.addItem(new ItemStack(ModItems.HUNTER_BLINK));
                player.addItem(new ItemStack(ModItems.HUNTER_HAMMER));
            }
            case "tracker" -> {
                player.addItem(new ItemStack(ModItems.HUNTER_PULSE));
                player.addItem(new ItemStack(ModItems.HUNTER_HOOK));
            }
            case "mechanic" -> player.addItem(new ItemStack(ModItems.REPAIR_TOOLBOX));
            case "medic" -> player.addItem(new ItemStack(ModItems.RESCUE_FLARE));
            case "runner" -> player.addItem(new ItemStack(ModItems.ESCAPE_GRAPPLE));
            default -> {
            }
        }
    }

    private void applyRoleTickEffects(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent) {
        if (!rolesFinalized) {
            return;
        }
        for (ServerPlayer player : serverWorld.players()) {
            String active = ModComponents.REPAIR_ROLES.get(player).activeRole;
            if ("runner".equals(active)) {
                if (serverWorld.getGameTime() % 8 == 0) {
                    serverWorld.sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD,
                            player.getX(), player.getY() + 0.1D, player.getZ(),
                            1, 0.08D, 0.02D, 0.08D, 0.005D);
                }
            } else if ("brute".equals(active)) {
                player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 40, 0, false, false, true));
                if (serverWorld.getGameTime() % 12 == 0) {
                    serverWorld.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT,
                            player.getX(), player.getY() + 1.0D, player.getZ(),
                            4, 0.25D, 0.35D, 0.25D, 0.02D);
                }
            } else if ("tracker".equals(active) && serverWorld.getGameTime() % (20 * 12) == 0) {
                ServerPlayer target = nearestTrackableTarget(serverWorld, gameWorldComponent, player, 28.0D);
                if (target != null) {
                    target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 60, 0, false, false, true));
                    serverWorld.sendParticles(net.minecraft.core.particles.ParticleTypes.SCULK_SOUL,
                            target.getX(), target.getY() + 1.0D, target.getZ(),
                            8, 0.35D, 0.45D, 0.35D, 0.02D);
                }
            }
        }
    }

    private static ServerPlayer nearestTrackableTarget(ServerLevel level, SREGameWorldComponent gameWorldComponent,
            ServerPlayer hunter, double radius) {
        double bestDistance = radius * radius;
        ServerPlayer best = null;
        for (ServerPlayer target : level.players()) {
            if (target == hunter || GameUtils.isPlayerEliminated(target)
                    || target.getTags().contains(RepairModeState.ESCAPED_TAG)
                    || RepairModeState.isHunter(target)) {
                continue;
            }
            double distance = target.distanceToSqr(hunter);
            if (distance <= bestDistance) {
                bestDistance = distance;
                best = target;
            }
        }
        return best;
    }

    private void tickDownedAndCarriedPlayers(ServerLevel serverWorld) {
        for (ServerPlayer player : serverWorld.players()) {
            var component = ModComponents.REPAIR_ROLES.get(player);
            if (component.carryBlockedTicks > 0) {
                component.carryBlockedTicks--;
                if (component.carryBlockedTicks == 0) {
                    component.sync();
                }
            }
            if (component.carrying != null) {
                if (serverWorld.getPlayerByUUID(component.carrying) instanceof ServerPlayer carried
                        && ModComponents.REPAIR_ROLES.get(carried).downed
                        && !GameUtils.isPlayerEliminated(carried)
                        && !carried.getTags().contains(RepairModeState.ESCAPED_TAG)) {
                    carried.teleportTo(player.getX() - Math.sin(Math.toRadians(player.getYRot())) * 0.75D,
                            player.getY(), player.getZ() + Math.cos(Math.toRadians(player.getYRot())) * 0.75D);
                    carried.addEffect(new MobEffectInstance(ModEffects.NO_COLLIDE, 20, 0, false, false, true));
                    player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 10, 1, false, false, true));
                    player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 10, 4, false, false, true));
                } else {
                    component.carrying = null;
                    component.sync();
                }
            }
            if (component.carriedBy != null) {
                player.addEffect(new MobEffectInstance(ModEffects.NO_COLLIDE, 10, 0, false, false, true));
                if (!(serverWorld.getPlayerByUUID(component.carriedBy) instanceof ServerPlayer carrier)
                        || !player.getUUID().equals(ModComponents.REPAIR_ROLES.get(carrier).carrying)) {
                    component.carriedBy = null;
                    component.sync();
                }
            }
            if (component.downed) {
                player.setHealth(Math.max(1.0F, player.getHealth()));
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 8, false, false, true));
                player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 3, false, false, true));
                player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 80, 0, false, false, true));
                player.addEffect(new MobEffectInstance(ModEffects.NO_COLLIDE, 40, 0, false, false, true));
            }
        }
    }

    private void updateRepairHudState(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent) {
        int completed = RepairModeState.getCompletedStationCount(serverWorld);
        boolean gatesPowered = RepairModeState.areExitGatesPowered(serverWorld);
        int activeTrialPrisoners = 0;
        for (ServerPlayer player : serverWorld.players()) {
            if (ModComponents.REPAIR_ROLES.get(player).trialStand.present()) {
                activeTrialPrisoners++;
            }
        }
        for (ServerPlayer player : serverWorld.players()) {
            var component = ModComponents.REPAIR_ROLES.get(player);
            component.completedStations = completed;
            component.gatesPowered = gatesPowered;
            component.activeTrialPrisoners = activeTrialPrisoners;
            component.downedAllies = countDownedAllies(serverWorld, gameWorldComponent, player);
            component.nearestTrialProgress = component.trialStand.present()
                    && serverWorld.getBlockEntity(component.trialStand.toBlockPos()) instanceof HunterCageBlockEntity cage
                            ? cage.getProgress(player.getUUID())
                            : 0;
            component.sync();
        }
    }

    private static int countDownedAllies(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent,
            ServerPlayer viewer) {
        boolean viewerHunter = RepairModeState.isHunter(viewer);
        int count = 0;
        for (ServerPlayer other : serverWorld.players()) {
            if (other == viewer || GameUtils.isPlayerEliminated(other)) {
                continue;
            }
            var otherComponent = ModComponents.REPAIR_ROLES.get(other);
            if (!otherComponent.downed) {
                continue;
            }
            boolean otherHunter = RepairModeState.isHunter(other);
            if (viewerHunter == otherHunter || (!viewerHunter && !otherHunter)) {
                count++;
            }
        }
        return count;
    }

    @Override
    public void stopGame(ServerLevel world) {
        RepairArenaBuilder.restoreAll(world);
        RepairLootSpawner.reset(world);
        RepairLockedDoorState.reset(world);
        RepairModeState.reset(world);
        RepairEventSystem.reset(world);
        RepairForcedRoleState.clearAll();
        rolesFinalized = false;
    }
}
