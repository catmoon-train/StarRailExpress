package net.exmo.sre.repair.logic;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.game.data.MapConfig;
import io.wifi.starrailexpress.network.original.AnnounceWelcomePayload;
import net.exmo.sre.repair.arena.RepairArenaBuilder;
import net.exmo.sre.repair.role.RepairForcedRoleState;
import net.exmo.sre.repair.role.RepairRoleDatabase;
import net.exmo.sre.repair.role.RepairRoleDefinition;
import net.exmo.sre.repair.state.RepairMapRuntimeConfig;
import net.exmo.sre.repair.state.RepairModeState;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.init.ModBlocks;
import org.agmas.noellesroles.init.ModItems;
import net.exmo.sre.repair.network.OpenRepairRoleSelectionS2CPacket;
import net.exmo.sre.repair.role.RepairRoles;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 修机模式的阵营分配与角色选择流程：
 * 开局随机分配阵营 → 30 秒选角窗口（周期性重开界面）→ 场地就绪后锁定角色并传送。
 */
public class RepairRoleSelection {
    public static final long SELECTION_TICKS = 30 * 20L;

    private long selectionEndTick;
    private boolean rolesFinalized;

    public boolean isFinalized() {
        return rolesFinalized;
    }

    public void reset() {
        rolesFinalized = false;
    }

    /** 开局：随机分配阵营（尊重强制角色）、发初始物资、打开选角界面。 */
    public void begin(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent,
            List<ServerPlayer> players) {
        rolesFinalized = false;
        selectionEndTick = serverWorld.getGameTime() + SELECTION_TICKS;

        ArrayList<ServerPlayer> shuffled = new ArrayList<>(players);
        Collections.shuffle(shuffled);
        int hunterCount = hunterCount(shuffled.size());
        int neutralCount = 0;
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
            player.addItem(Items.BUNDLE.getDefaultInstance());
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
                case HUNTER -> RepairRoles.REPAIR_HUNTER;
                case NEUTRAL -> RepairRoles.REPAIR_NEUTRAL;
                case SURVIVOR -> RepairRoles.REPAIR_SURVIVOR;
            }, false);
            SREPlayerShopComponent.KEY.get(player).setBalance(startingCoins(faction));
            giveModeItems(player, faction, serverWorld.random);
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

    /** 选角阶段每 tick 驱动：场地准备、周期性重开选角界面、超时后锁定角色。 */
    public void tick(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent) {
        if (rolesFinalized) {
            return;
        }
        RepairArenaBuilder.tickSelection(serverWorld);
        if (serverWorld.getGameTime() % 40L == 0L) {
            reopenRoleSelection(serverWorld, gameWorldComponent);
        }
        if (serverWorld.getGameTime() >= selectionEndTick && RepairArenaBuilder.isReady(serverWorld)) {
            finalizeSelectedRoles(serverWorld, gameWorldComponent);
        }
    }

    private static int hunterCount(int playerCount) {
        return playerCount >= 11 ? 2 : 1;
    }

    private static void giveModeItems(ServerPlayer player, RepairRoleDefinition.Faction faction, RandomSource random) {
        switch (faction) {
            case HUNTER -> {
                // 追捕者初始获得基础利刃；新庄园场景主通道无锁，不再需要开门钥匙
                player.addItem(new ItemStack(ModItems.HUNTER_WEAPON));
                // 追捕者开局12s缓慢5，给幸存者散开的时间
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 12 * 20, 5, false, false, true));
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

    private static RepairRoleDefinition.Faction selectionFaction(ServerPlayer player,
            SREGameWorldComponent gameWorldComponent) {
        if (gameWorldComponent.isRole(player, RepairRoles.REPAIR_HUNTER)) {
            return RepairRoleDefinition.Faction.HUNTER;
        }
        if (gameWorldComponent.isRole(player, RepairRoles.REPAIR_NEUTRAL)) {
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
            RepairRoleDefinition.Faction faction = gameWorldComponent.isRole(player, RepairRoles.REPAIR_HUNTER)
                    ? RepairRoleDefinition.Faction.HUNTER
                    : gameWorldComponent.isRole(player, RepairRoles.REPAIR_NEUTRAL)
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
                if (repairConfig == null) {
                    RepairArenaBuilder.teleportToDefaultGameplaySpawn(player, true, hunterSpawnIndex++);
                } else {
                    teleportToConfiguredSpawn(player, repairConfig.hunterSpawns, hunterSpawnIndex++);
                }
            } else {
                if (repairConfig == null) {
                    RepairArenaBuilder.teleportToDefaultGameplaySpawn(player, false, survivorSpawnIndex++);
                } else {
                    teleportToConfiguredSpawn(player, repairConfig.survivorSpawns, survivorSpawnIndex++);
                }
            }
            player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
            player.displayClientMessage(
                    Component.translatable("message.noellesroles.repair.role_locked", role.displayName())
                            .withStyle(ChatFormatting.GREEN),
                    false);
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
            }
            case "mechanic" -> player.addItem(new ItemStack(ModItems.REPAIR_TOOLBOX));
            case "medic" -> player.addItem(new ItemStack(ModItems.RESCUE_FLARE));
            case "runner" -> player.addItem(new ItemStack(ModItems.ESCAPE_GRAPPLE));
            default -> {
            }
        }
    }
}
