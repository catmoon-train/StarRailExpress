package net.exmo.sre.repair.state;

import net.exmo.sre.repair.*;
import net.exmo.sre.repair.role.*;
import net.exmo.sre.repair.arena.*;
import net.exmo.sre.repair.event.*;
import net.exmo.sre.repair.util.*;

import io.wifi.starrailexpress.api.SREGameModes;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Pose;
import org.agmas.noellesroles.component.ModComponents;
import net.exmo.sre.repair.content.block_entity.RepairStationBlockEntity;
import org.agmas.noellesroles.init.ModEffects;
import net.exmo.sre.repair.network.RepairCoinRewardS2CPacket;
import net.exmo.sre.repair.network.RepairCombatFeedbackS2CPacket;
import net.exmo.sre.repair.role.RepairRoles;

import java.util.Map;
import java.util.WeakHashMap;

public final class RepairModeState {
    public static final String ESCAPED_TAG = "noellesroles_repair_escaped";
    public static final String NEUTRAL_WIN_TAG = "noellesroles_repair_neutral_win";
    public static final int REQUIRED_REPAIRED_STATIONS = 3;
    public static final int REPAIR_STATION_MAX_PROGRESS = 500;
    public static final int TRIAL_EXECUTION_TICKS = 20 * 100;
    public static final float REVIVE_HEALTH = 8.0F;
    public static final int REPAIR_ACTION_COOLDOWN_TICKS = 8;
    public static final int ARCHIVIST_TASK_NEEDED = 8;
    public static final int SABOTEUR_TASK_NEEDED = 10;
    public static final int COLLECTOR_TASK_NEEDED = 4;

    private static final Map<ServerLevel, Integer> COMPLETED_STATIONS = new WeakHashMap<>();

    private RepairModeState() {
    }

    public static void reset(ServerLevel level) {
        COMPLETED_STATIONS.put(level, 0);
        level.players().forEach(player -> {
            player.removeTag(ESCAPED_TAG);
            player.removeTag(NEUTRAL_WIN_TAG);
            var component = ModComponents.REPAIR_ROLES.get(player);
            component.activeRole = "";
            component.neutralTaskProgress = 0;
            component.neutralTaskCompleted = false;
            component.selectionEndTick = 0L;
            component.downed = false;
            component.carriedBy = null;
            component.carrying = null;
            component.carryBlockedTicks = 0;
            component.trialStand = net.exmo.sre.repair.component.RepairRolePlayerComponent.BlockPosTag.NONE;
            component.completedStations = 0;
            component.gatesPowered = false;
            component.downedAllies = 0;
            component.activeTrialPrisoners = 0;
            component.nearestTrialProgress = 0;
            component.currentEventKey = "";
            component.currentEventRewardKey = "";
            component.currentEventTicks = 0;
            component.currentEventDanger = 0;
            component.lastRepairActionTick = -100L;
            component.neutralTaskNeeded = 0;
            component.repairInjuryLevel = 0;
            component.lastHunterHitTick = -1000L;
            component.activeSkillCooldownEndTick = 0L;
            component.hunterWeaponCooldownEndTick = 0L;
            component.selectedSkillState = "";
            component.carryStruggleProgress = 0;
            component.downedStruggleProgress = 0;
            component.downedLastStruggleTick = -1000L;
            component.lastStruggleSide = "";
            component.lastStruggleTick = -1000L;
            component.activeAttackPlugin = "";
            component.forcedRole = "";
            component.searchTarget = net.exmo.sre.repair.component.RepairRolePlayerComponent.BlockPosTag.NONE;
            component.searchStartTick = 0L;
            component.searchTotalTicks = 0;
            component.searchPromptKey = "";
            component.lockPromptKey = "";
            component.escapedRouteId = "";
            player.setPose(Pose.STANDING);
            player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
            player.removeEffect(MobEffects.WEAKNESS);
            player.removeEffect(MobEffects.DARKNESS);
            player.removeEffect(MobEffects.MOVEMENT_SPEED);
            player.removeEffect(MobEffects.DAMAGE_BOOST);
            player.removeEffect(MobEffects.GLOWING);
            player.removeEffect(MobEffects.NIGHT_VISION);
            player.removeEffect(MobEffects.REGENERATION);
            player.removeEffect(MobEffects.DIG_SPEED);
            player.removeEffect(MobEffects.SLOW_FALLING);
            player.removeEffect(MobEffects.DAMAGE_RESISTANCE);
            player.removeEffect(MobEffects.BLINDNESS);
            player.removeEffect(MobEffects.INVISIBILITY);
            player.removeEffect(MobEffects.POISON);
            player.removeEffect(MobEffects.JUMP);
            player.removeEffect(ModEffects.NO_COLLIDE);
            component.sync();
        });
    }

    public static boolean isRepairGameRunning(ServerPlayer player) {
        return player != null && player.level() instanceof ServerLevel level && isRepairGameRunning(level);
    }

    public static boolean isRepairGameRunning(ServerLevel level) {
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(level);
        return game != null && game.isRunning() && game.getGameMode() == SREGameModes.REPAIR_ESCAPE_MODE;
    }

    public static void stationCompleted(ServerLevel level) {
        COMPLETED_STATIONS.put(level, getCompletedStationCount(level) + 1);
    }

    public static void stationCompleted(ServerLevel level, BlockPos pos) {
        stationCompleted(level);
        RepairGameplayEffects.burst(level, pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D, 2);
        for (ServerPlayer player : level.players()) {
            if (RepairGameplayEffects.isSurvivor(player)) {
                awardCoins(player, 35, "repair_coin_source.station");
                player.displayClientMessage(Component.translatable("message.noellesroles.repair.team_station_reward", 35)
                        .withStyle(ChatFormatting.GOLD), true);
            } else if (RepairGameplayEffects.isHunter(player)) {
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20 * 12, 0, false, true, true));
                player.displayClientMessage(Component.translatable("message.noellesroles.repair.hunter_frenzy")
                        .withStyle(ChatFormatting.RED), true);
            }
        }
    }

    public static int getCompletedStationCount(ServerLevel level) {
        return COMPLETED_STATIONS.getOrDefault(level, 0);
    }

    public static boolean areExitGatesPowered(ServerLevel level) {
        return getCompletedStationCount(level) >= REQUIRED_REPAIRED_STATIONS;
    }

    public static void addNeutralTaskProgress(ServerPlayer player, String roleId, int amount, int needed) {
        var component = ModComponents.REPAIR_ROLES.get(player);
        if (!roleId.equals(component.activeRole) || component.neutralTaskCompleted) {
            return;
        }
        int goal = neutralTaskGoal(roleId, needed);
        component.neutralTaskNeeded = goal;
        component.neutralTaskProgress = Math.min(goal, component.neutralTaskProgress + amount);
        if (component.neutralTaskProgress >= goal) {
            component.neutralTaskCompleted = true;
            player.addTag(NEUTRAL_WIN_TAG);
            player.displayClientMessage(Component.translatable("message.noellesroles.repair.neutral_complete")
                    .withStyle(ChatFormatting.GOLD), false);
        } else {
            player.displayClientMessage(Component.translatable("message.noellesroles.repair.neutral_progress",
                    component.neutralTaskProgress, goal).withStyle(ChatFormatting.YELLOW), true);
        }
        component.sync();
    }

    private static int neutralTaskGoal(String roleId, int fallback) {
        return switch (roleId) {
            case "archivist" -> ARCHIVIST_TASK_NEEDED;
            case "saboteur" -> SABOTEUR_TASK_NEEDED;
            case "collector" -> COLLECTOR_TASK_NEEDED;
            default -> fallback;
        };
    }

    public static void awardCoins(ServerPlayer player, int amount, String sourceKey) {
        SREPlayerShopComponent.KEY.get(player).addToBalance(amount);
        ServerPlayNetworking.send(player, new RepairCoinRewardS2CPacket(amount, sourceKey));
    }

    public static void startSkillCooldown(ServerPlayer player, int ticks, String state) {
        var component = ModComponents.REPAIR_ROLES.get(player);
        component.activeSkillCooldownEndTick = Math.max(component.activeSkillCooldownEndTick,
                player.level().getGameTime() + ticks);
        component.selectedSkillState = state == null ? "" : state;
        component.sync();
    }

    public static int skillCooldownSeconds(ServerPlayer player) {
        long remaining = ModComponents.REPAIR_ROLES.get(player).activeSkillCooldownEndTick - player.level().getGameTime();
        return remaining <= 0L ? 0 : (int) Math.ceil(remaining / 20.0D);
    }

    public static boolean isHunter(ServerPlayer player) {
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(player.level());
        var role = ModComponents.REPAIR_ROLES.get(player).activeRole;
        return RepairRoleDefinition.byId(role).map(def -> def.faction == RepairRoleDefinition.Faction.HUNTER)
                .orElse(game != null && game.isRole(player, RepairRoles.REPAIR_HUNTER));
    }

    public static boolean isNonHunterRepairPlayer(ServerPlayer player) {
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(player.level());
        if (game == null || !isRepairGameRunning(player)) {
            return false;
        }
        var component = ModComponents.REPAIR_ROLES.get(player);
        return RepairRoleDefinition.byId(component.activeRole)
                .map(def -> def.faction != RepairRoleDefinition.Faction.HUNTER)
                .orElse(game.isRole(player, RepairRoles.REPAIR_SURVIVOR) || game.isRole(player, RepairRoles.REPAIR_NEUTRAL));
    }

    public static boolean canRepair(ServerPlayer player) {
        if (!isNonHunterRepairPlayer(player) || GameUtils.isPlayerEliminated(player)
                || player.getTags().contains(ESCAPED_TAG)) {
            return false;
        }
        var component = ModComponents.REPAIR_ROLES.get(player);
        return !component.activeRole.isEmpty() && !component.downed && component.carriedBy == null && component.carrying == null
                && !component.trialStand.present();
    }

    public static boolean canUseSurvivorUtility(ServerPlayer player) {
        return canRepair(player);
    }

    public static boolean canUseHunterUtility(ServerPlayer player) {
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(player.level());
        return !ModComponents.REPAIR_ROLES.get(player).activeRole.isEmpty()
                && game != null && isRepairGameRunning(player)
                && isHunter(player) && !GameUtils.isPlayerEliminated(player)
                && !player.getTags().contains(ESCAPED_TAG);
    }

    public static boolean downPlayer(ServerPlayer player) {
        if (!isNonHunterRepairPlayer(player) || GameUtils.isPlayerEliminated(player)) {
            return false;
        }
        var component = ModComponents.REPAIR_ROLES.get(player);
        if (component.downed) {
            player.setHealth(Math.max(1.0F, player.getHealth()));
            player.addEffect(new MobEffectInstance(ModEffects.NO_COLLIDE, 40, 0, false, false, true));
            return true;
        }
        component.downed = true;
        component.carriedBy = null;
        component.trialStand = net.exmo.sre.repair.component.RepairRolePlayerComponent.BlockPosTag.NONE;
        component.repairInjuryLevel = 0;
        component.lastHunterHitTick = -1000L;
        component.carryStruggleProgress = 0;
        component.downedStruggleProgress = 0;
        component.downedLastStruggleTick = -1000L;
        component.lastStruggleSide = "";
        component.lastStruggleTick = -1000L;
        player.setHealth(1.0F);
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20 * 60, 8, false, false, true));
        player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 20 * 60, 3, false, false, true));
        player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 20 * 10, 0, false, false, true));
        player.addEffect(new MobEffectInstance(ModEffects.NO_COLLIDE, 20 * 60, 0, false, false, true));
        component.sync();
        broadcastCombatFeedback((ServerLevel) player.level(), RepairCombatFeedbackS2CPacket.DOWNED, player, player.getX(),
                player.getY() + 0.8D, player.getZ(), 24.0D);
        player.displayClientMessage(Component.translatable("message.noellesroles.repair.downed").withStyle(ChatFormatting.DARK_RED), false);
        return true;
    }


    public static void startTrial(ServerPlayer hunter, ServerPlayer prisoner, BlockPos cagePos) {
        var hunterComponent = ModComponents.REPAIR_ROLES.get(hunter);
        var prisonerComponent = ModComponents.REPAIR_ROLES.get(prisoner);
        hunterComponent.carrying = null;
        prisonerComponent.carriedBy = null;
        prisonerComponent.carryStruggleProgress = 0;
        prisonerComponent.downedStruggleProgress = 0;
        prisonerComponent.downedLastStruggleTick = -1000L;
        prisonerComponent.lastStruggleSide = "";
        prisonerComponent.lastStruggleTick = -1000L;
        prisonerComponent.trialStand = net.exmo.sre.repair.component.RepairRolePlayerComponent.BlockPosTag.of(cagePos);
        prisoner.setPose(Pose.STANDING);
        prisoner.teleportTo(cagePos.getX() + 0.5D, cagePos.getY(), cagePos.getZ() + 0.5D);
        prisoner.setDeltaMovement(0.0D, 0.0D, 0.0D);
        prisoner.resetFallDistance();
        prisoner.addEffect(new MobEffectInstance(ModEffects.NO_COLLIDE, 40, 0, false, false, true));
        prisonerComponent.sync();
        hunterComponent.sync();
    }

    public static void revivePlayer(ServerPlayer medic, ServerPlayer target) {
        var component = ModComponents.REPAIR_ROLES.get(target);
        releaseFromCarrier(target);
        component.downed = false;
        component.carriedBy = null;
        component.carrying = null;
        component.trialStand = net.exmo.sre.repair.component.RepairRolePlayerComponent.BlockPosTag.NONE;
        component.repairInjuryLevel = 0;
        component.lastHunterHitTick = -1000L;
        component.carryStruggleProgress = 0;
        component.downedStruggleProgress = 0;
        component.downedLastStruggleTick = -1000L;
        component.lastStruggleSide = "";
        component.lastStruggleTick = -1000L;
        target.setPose(Pose.STANDING);
        target.setHealth(Math.min(target.getMaxHealth(), REVIVE_HEALTH));
        target.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        target.removeEffect(MobEffects.WEAKNESS);
        target.removeEffect(MobEffects.DARKNESS);
        target.removeEffect(ModEffects.NO_COLLIDE);
        component.sync();
        awardCoins(medic, 40, "repair_coin_source.revive");
        broadcastCombatFeedback((ServerLevel) target.level(), RepairCombatFeedbackS2CPacket.REVIVED, target, target.getX(),
                target.getY() + 0.8D, target.getZ(), 24.0D);
        target.displayClientMessage(Component.translatable("message.noellesroles.repair.revived").withStyle(ChatFormatting.GREEN), false);
    }

    public static void clearRestraints(ServerPlayer player) {
        releaseCarried(player);
        releaseFromCarrier(player);
        var component = ModComponents.REPAIR_ROLES.get(player);
        component.downed = false;
        component.carriedBy = null;
        component.carrying = null;
        component.carryBlockedTicks = 0;
        component.trialStand = net.exmo.sre.repair.component.RepairRolePlayerComponent.BlockPosTag.NONE;
        component.repairInjuryLevel = 0;
        component.lastHunterHitTick = -1000L;
        component.activeSkillCooldownEndTick = 0L;
        component.hunterWeaponCooldownEndTick = 0L;
        component.selectedSkillState = "";
        component.carryStruggleProgress = 0;
        component.downedStruggleProgress = 0;
        component.downedLastStruggleTick = -1000L;
        component.lastStruggleSide = "";
        component.lastStruggleTick = -1000L;
        component.activeAttackPlugin = "";
        component.searchTarget = net.exmo.sre.repair.component.RepairRolePlayerComponent.BlockPosTag.NONE;
        component.searchStartTick = 0L;
        component.searchTotalTicks = 0;
        component.searchPromptKey = "";
        component.lockPromptKey = "";
        player.setPose(Pose.STANDING);
        player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        player.removeEffect(MobEffects.WEAKNESS);
        player.removeEffect(MobEffects.DARKNESS);
        player.removeEffect(ModEffects.NO_COLLIDE);
        component.sync();
    }

    private static void releaseFromCarrier(ServerPlayer carried) {
        var carriedComponent = ModComponents.REPAIR_ROLES.get(carried);
        if (carriedComponent.carriedBy == null || !(carried.level() instanceof ServerLevel level)) {
            return;
        }
        if (level.getPlayerByUUID(carriedComponent.carriedBy) instanceof ServerPlayer hunter) {
            var hunterComponent = ModComponents.REPAIR_ROLES.get(hunter);
            if (carried.getUUID().equals(hunterComponent.carrying)) {
                hunterComponent.carrying = null;
                hunterComponent.sync();
            }
        }
        carriedComponent.carriedBy = null;
    }

    public static void releaseCarried(ServerPlayer hunter) {
        var hunterComponent = ModComponents.REPAIR_ROLES.get(hunter);
        if (hunterComponent.carrying == null || !(hunter.level() instanceof ServerLevel level)) {
            return;
        }
        if (level.getPlayerByUUID(hunterComponent.carrying) instanceof ServerPlayer carried) {
            var carriedComponent = ModComponents.REPAIR_ROLES.get(carried);
            carriedComponent.carriedBy = null;
            carriedComponent.carryStruggleProgress = 0;
            carriedComponent.downedStruggleProgress = 0;
            carriedComponent.downedLastStruggleTick = -1000L;
            carriedComponent.lastStruggleSide = "";
            carriedComponent.lastStruggleTick = -1000L;
            BlockPos releasePos = findNearbySafeRelease(level, hunter.blockPosition());
            carried.teleportTo(releasePos.getX() + 0.5D, releasePos.getY(), releasePos.getZ() + 0.5D);
            carried.setDeltaMovement(0.0D, 0.0D, 0.0D);
            carried.resetFallDistance();
            carried.addEffect(new MobEffectInstance(ModEffects.NO_COLLIDE, 40, 0, false, false, true));
            carriedComponent.sync();
        }
        hunterComponent.carrying = null;
        hunterComponent.sync();
    }


    private static BlockPos findNearbySafeRelease(ServerLevel level, BlockPos origin) {
        for (BlockPos candidate : BlockPos.betweenClosed(origin.offset(-2, -1, -2), origin.offset(2, 1, 2))) {
            BlockPos feet = candidate.immutable();
            if (!level.getBlockState(feet.below()).isAir()
                    && level.getBlockState(feet).canBeReplaced()
                    && level.getBlockState(feet.above()).canBeReplaced()) {
                return feet;
            }
        }
        return origin;
    }

    public static void broadcastCombatFeedback(ServerLevel level, int kind, net.minecraft.world.entity.Entity entity, double x,
            double y, double z, double radius) {
        broadcastCombatFeedback(level, kind, entity, x, y, z, radius, "");
    }




    public static void broadcastCombatFeedback(ServerLevel level, int kind, net.minecraft.world.entity.Entity entity, double x,
            double y, double z, double radius, String weaponId) {
        double radiusSqr = radius * radius;
        RepairCombatFeedbackS2CPacket packet = new RepairCombatFeedbackS2CPacket(kind, entity.getId(), x, y, z, weaponId == null ? "" : weaponId);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(x, y, z) <= radiusSqr) {
                ServerPlayNetworking.send(player, packet);
            }
        }
    }

    public static void highlightActiveStationsFor(ServerPlayer hunter, int ticks) {
        if (!(hunter.level() instanceof ServerLevel level)) {
            return;
        }
        int radius = 32;
        int highlighted = 0;
        for (BlockPos pos : BlockPos.betweenClosed(hunter.blockPosition().offset(-radius, -8, -radius),
                hunter.blockPosition().offset(radius, 8, radius))) {
            if (!(level.getBlockEntity(pos) instanceof RepairStationBlockEntity station)
                    || station.isCompleted()
                    || station.getAnimationTicks() <= 0) {
                continue;
            }
            highlighted++;
            level.sendParticles(hunter, ParticleTypes.NOTE, true,
                    pos.getX() + 0.5D, pos.getY() + 1.3D, pos.getZ() + 0.5D,
                    8, 0.2D, 0.35D, 0.2D, 0.02D);
            level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 0.9F, 1.35F);
        }
        if (highlighted > 0) {
            hunter.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, Math.max(40, ticks), 0, false, false, true));
            hunter.displayClientMessage(Component.translatable("message.noellesroles.repair.tracker_station_ping", highlighted)
                    .withStyle(ChatFormatting.AQUA), true);
        }
    }

    public static void blockHunterCarry(ServerPlayer hunter, int ticks) {
        releaseCarried(hunter);
        var component = ModComponents.REPAIR_ROLES.get(hunter);
        component.carryBlockedTicks = Math.max(component.carryBlockedTicks, ticks);
        component.sync();
    }

    public static void dropCarried(ServerPlayer hunter, int carryBlockTicks) {
        releaseCarried(hunter);
        var component = ModComponents.REPAIR_ROLES.get(hunter);
        component.carryBlockedTicks = Math.max(component.carryBlockedTicks, carryBlockTicks);
        component.sync();
    }
}
