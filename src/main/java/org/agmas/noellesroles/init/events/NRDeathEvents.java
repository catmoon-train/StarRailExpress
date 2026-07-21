package org.agmas.noellesroles.init.events;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.SREGameModes;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.api.replay.GameReplayUtils;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerPsychoComponent;
import io.wifi.starrailexpress.content.block.SmallDoorBlock;
import io.wifi.starrailexpress.content.item.api.SREItemProperties.DropRevolverWhenDead;
import io.wifi.starrailexpress.content.item.api.SREItemProperties.DropWhenDead;
import io.wifi.starrailexpress.event.*;
import io.wifi.starrailexpress.content.block_entity.EntityInteractionBlockEntity;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.roles.SpecialGameModeRoles;
import io.wifi.starrailexpress.index.SREDataComponentTypes;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.index.tag.TMMItemTags;
import io.wifi.starrailexpress.network.CloseUiPayload;
import io.wifi.starrailexpress.util.SREItemUtils;
import io.wifi.starrailexpress.util.TrueFalseResult;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.noellesroles.*;
import org.agmas.noellesroles.cca.C4BackComponent;
import org.agmas.noellesroles.component.DeathPenaltyComponent;
import org.agmas.noellesroles.component.DefibrillatorComponent;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.agmas.noellesroles.game.roles.innocence.avenger.AvengerPlayerComponent;
import org.agmas.noellesroles.game.roles.innocence.boxer.BoxerPlayerComponent;
import org.agmas.noellesroles.game.roles.innocence.broadcaster.BroadcasterPlayerComponent;
import org.agmas.noellesroles.game.roles.innocence.cake_maker.CakeMakerComponent;
import org.agmas.noellesroles.game.roles.innocence.fool.TarotAssemblyManager;
import org.agmas.noellesroles.game.roles.innocence.fortuneteller.FortunetellerPlayerComponent;
import org.agmas.noellesroles.game.roles.innocence.glitch_robot.GlitchRobotPlayerComponent;
import org.agmas.noellesroles.game.roles.killer.insane_killer.InsaneKillerPlayerComponent;
import org.agmas.noellesroles.game.roles.killer.manipulator.InControlCCA;
import org.agmas.noellesroles.game.roles.killer.shadow_falcon.ShadowFalconPlayerComponent;
import org.agmas.noellesroles.game.roles.killer.stalker.StalkerPlayerComponent;
import org.agmas.noellesroles.game.roles.killer.watcher.WatcherPlayerComponent;
import org.agmas.noellesroles.game.roles.neutral.mercenary.MercenaryPlayerComponent;
import org.agmas.noellesroles.game.roles.neutral.puppeteer.PuppeteerPlayerComponent;
import org.agmas.noellesroles.game.roles.neutral.raven.RavenPlayerComponent;
import org.agmas.noellesroles.game.roles.neutral.thief.ThiefPlayerComponent;
import org.agmas.noellesroles.game.roles.special.better_vigilante.BetterVigilantePlayerComponent;
import org.agmas.noellesroles.game.roles.vigilante.patroller.PatrollerPlayerComponent;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.init.FunnyItems;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.init.RoleShopHandler;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.utils.MCItemsUtils;
import org.agmas.noellesroles.utils.RoleUtils;
import pro.fazeclan.river.stupid_express.constants.SEModifiers;
import pro.fazeclan.river.stupid_express.modifier.refugee.cca.RefugeeComponent;
import pro.fazeclan.river.stupid_express.modifier.split_personality.cca.SplitPersonalityComponent;

import java.util.*;

/**
 * 死亡、护盾、免死、掉落相关事件处理
 */
public class NRDeathEvents {

    // ==================== 死亡免疫 / 反制辅助方法 ====================

    /**
     * 处理窃皮者死亡免疫 - 有偷来皮肤时被枪击中进入眩晕
     */
    private static boolean handleSkincrawlerDeath(Player victim, ResourceLocation deathReason) {
        if (victim == null || victim.level().isClientSide())
            return false;
        if (!(victim instanceof ServerPlayer sp))
            return false;
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(victim.level());
        if (!gameWorld.isRole(victim, ModRoles.SKINCRAWLER))
            return false;
        if (!GameConstants.DeathReasons.REVOLVER.equals(deathReason))
            return false;
        var comp = org.agmas.noellesroles.game.roles.killer.skincrawler.SkincrawlerPlayerComponent.KEY.get(sp);
        if (comp == null || comp.stolenSkin == null || comp.stolenSkin.equals(sp.getUUID()))
            return false;
        if (comp.blockCharges <= 0)
            return false;
        comp.blockCharges--;
        comp.stolenSkin = null;
        comp.sync();
        for (ServerPlayer p : sp.serverLevel().getPlayers(p2 -> true)) {
            ServerPlayNetworking.send(p,
                    new org.agmas.noellesroles.packet.SkincrawlerSkinS2CPacket(sp.getUUID(), null));
        }
        sp.addEffect(new MobEffectInstance(ModEffects.MOVE_BANED, 100, 0, false, false, false));
        sp.displayClientMessage(
                Component.translatable("message.noellesroles.skincrawler.stunned").withStyle(ChatFormatting.RED), true);
        return true;
    }

    /**
     * 处理斗士无敌反制 - 钢筋铁骨期间可以反弹任何死亡
     */
    private static boolean handleBoxerInvulnerability(Player victim, ResourceLocation deathReason) {
        if (victim == null || victim.level().isClientSide())
            return false;

        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(victim.level());

        // 模仿者斗士无敌检测
        if (gameWorld.isRole(victim, ModRoles.IMITATOR)) {
            var imitComp = ModComponents.IMITATOR.get(victim);
            if (imitComp.isImitatorInvulnerable()) {
                victim.level().playSound(null, victim.blockPosition(),
                        io.wifi.starrailexpress.index.TMMSounds.ITEM_PSYCHO_ARMOUR,
                        SoundSource.MASTER, 5.0F, 1.0F);
                if (victim instanceof ServerPlayer sp) {
                    sp.displayClientMessage(Component.translatable(
                            "message.noellesroles.imitator.boxer_blocked")
                            .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), true);
                }
                return true;
            }
        }

        if (!gameWorld.isRole(victim, ModRoles.FIGHTER))
            return false;

        BoxerPlayerComponent boxerComponent = ModComponents.FIGHTER.get(victim);
        if (!boxerComponent.isInvulnerable)
            return false;

        if (deathReason.equals(GameConstants.DeathReasons.FELL_OUT_OF_TRAIN)
                || deathReason.getPath().equals("death_afk")
                || deathReason.getPath().equals("shot_innocent")) {
            return false;
        }

        boolean isKnife = deathReason.equals(GameConstants.DeathReasons.KNIFE);
        boolean isBat = deathReason.equals(GameConstants.DeathReasons.BAT);

        if (isKnife || isBat) {
            Player attacker = RicesRoleRhapsody.findAttackerWithWeapon(victim, isKnife);
            if (attacker != null) {
                ItemStack weapon = attacker.getMainHandItem();
                boxerComponent.handleCounterAttack(attacker, weapon);
            }
        }

        boxerComponent.handleAnyDeathCounter(deathReason);
        return true;
    }

    /**
     * 处理跟踪者免疫 - 盾牌只在一阶段有效
     */
    private static boolean handleStalkerImmunity(Player victim, ResourceLocation deathReason) {
        if (victim == null || victim.level().isClientSide())
            return false;

        StalkerPlayerComponent stalkerComp = ModComponents.STALKER.get(victim);
        if (!stalkerComp.isActiveStalker())
            return false;
        if (stalkerComp.phase != 1)
            return false;
        if (stalkerComp.immunityUsed)
            return false;

        stalkerComp.immunityUsed = true;
        stalkerComp.sync();

        victim.level().playSound(null, victim.blockPosition(),
                io.wifi.starrailexpress.index.TMMSounds.ITEM_PSYCHO_ARMOUR,
                SoundSource.MASTER, 5.0F, 1.0F);

        if (victim instanceof ServerPlayer serverPlayer) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.stalker.immunity_triggered")
                            .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD),
                    true);
        }
        return true;
    }

    /**
     * 处理傀儡师死亡 - 假人死亡时返回本体
     */
    private static boolean handlePuppeteerDeath(Player victim, ResourceLocation deathReason) {
        if (victim == null || victim.level().isClientSide())
            return false;

        PuppeteerPlayerComponent puppeteerComp = ModComponents.PUPPETEER.get(victim);
        if (!puppeteerComp.isActivePuppeteer())
            return false;
        if (!puppeteerComp.isControllingPuppet)
            return false;

        puppeteerComp.onPuppetDeath();
        return true;
    }

    private static boolean handleDefibrillator(Player victim) {
        DefibrillatorComponent component = ModComponents.DEFIBRILLATOR.get(victim);
        if (component.hasProtection()) {
            if (component.defibrillatorMark) {
                component.isDead = true;
                component.resurrectionTime = victim.level().getGameTime() + 30 * 20;
                component.deathPos = victim.position();
                ModComponents.DEFIBRILLATOR.sync(victim);

                DeathPenaltyComponent deathPenaltyComponent = ModComponents.DEATH_PENALTY.get(victim);
                deathPenaltyComponent.setPenalty(45 * 20, true);
                victim.displayClientMessage(
                        Component.translatable("message.noellesroles.doctor.penalty").withStyle(ChatFormatting.RED),
                        true);
                victim.sendSystemMessage(
                        Component.translatable("message.noellesroles.doctor.penalty").withStyle(ChatFormatting.RED));
            } else {
                component.triggerDeath(30 * 20, null, victim.position());
            }
            return true;
        }
        return false;
    }

    private static void handleGlitchRobotDeath(Player victim) {
        if (victim == null || victim.level().isClientSide())
            return;
        SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(victim.level());
        if (!gameWorldComponent.isRole(victim, ModRoles.GLITCH_ROBOT))
            return;
        GlitchRobotPlayerComponent.onKnockOut(victim);
    }

    private static void handleCakeMakerDeath(Player victim) {
        if (victim == null || victim.level().isClientSide())
            return;
        SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(victim.level());
        if (!gameWorldComponent.isRole(victim, ModRoles.CAKE_MAKER))
            return;
        CakeMakerComponent.KEY.get(victim).onDeath();
    }

    /**
     * 处理猎人死亡 - 给予其它杀手阵营（含杀手方中立）每人一匹随机的马（亡者赠礼）。
     * 原 HunterRole.onDeath 逻辑迁移至此集中管理。
     */
    private static void handleHunterDeath(Player victim) {
        if (victim == null || victim.level().isClientSide())
            return;
        if (!(victim instanceof ServerPlayer))
            return;
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(victim.level());
        if (!gameWorld.isRole(victim, ModRoles.HUNTER))
            return;
        for (Player p : victim.level().players()) {
            if (p == victim)
                continue;
            if (!GameUtils.isPlayerAliveAndSurvival(p))
                continue;
            if (!(p instanceof ServerPlayer sp))
                continue;
            SRERole role = gameWorld.getRole(p);
            if (role != null && (role.canUseKiller() || role.isNeutralForKiller())) {
                int r = victim.getRandom().nextInt(100);
                ItemStack horse;
                if (r < 40)
                    horse = FunnyItems.RAINBOW_HORSESHOE.getDefaultInstance();
                else if (r < 80)
                    horse = FunnyItems.CANYUESA_HORSESHOE.getDefaultInstance();
                else
                    horse = FunnyItems.SUPER_PIG_HORSESHOE.getDefaultInstance();
                RoleUtils.insertStackInFreeSlot(sp, horse);
                sp.displayClientMessage(
                        Component.translatable("message.noellesroles.hunter.horse_gifted", victim.getName())
                                .withStyle(ChatFormatting.GOLD),
                        true);
            }
        }
    }

    // ==================== 死亡惩罚 ====================

    public static void reJudgeSpectatorsPenalty(Level level) {
        final ArrayList<Player> players = new ArrayList<>(level.players());
        players.removeIf(p -> !GameUtils.isPlayerSpectator(p));
        handleDeathPenalty(level, players, true, true);
    }

    public static void handleDeathPenalty(Player victim) {
        handleDeathPenalty(victim.level(), List.of(victim), false, false);
    }

    public static void handleDeathPenalty(Player victim, boolean ignoreDoctor, boolean ignoreLooseEnd) {
        handleDeathPenalty(victim.level(), List.of(victim), ignoreDoctor, ignoreLooseEnd);
    }

    public static void handleDeathPenalty(Level level, List<Player> victims, boolean ignoreDoctor,
            boolean ignoreLooseEnd) {
        SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(level);
        boolean doctorAlive = false;
        boolean looseEndAlive = false;
        boolean CONSPIRATOR_alive = false;
        boolean limitView = false;
        var refugeeComponent = RefugeeComponent.KEY.get(level);
        if (gameWorldComponent.getGameMode().identifier.equals(SREGameModes.LOOSE_ENDS_ID))
            return;
        if (refugeeComponent.isAnyRevivals && !ignoreLooseEnd) {
            looseEndAlive = true;
        }
        for (Player player : level.players()) {
            if (!GameUtils.isPlayerAliveAndSurvival(player)) {
                continue;
            }
            if (gameWorldComponent.isRole(player, ModRoles.DOCTOR) && !ignoreDoctor) {
                doctorAlive = true;
            } else if (gameWorldComponent.isRole(player, ModRoles.CONSPIRATOR)) {
                CONSPIRATOR_alive = true;
            }
            if (doctorAlive || CONSPIRATOR_alive) {
                break;
            }
        }
        if (CONSPIRATOR_alive) {
            limitView = true;
        }
        for (final var victim : victims) {
            DeathPenaltyComponent deathPenaltyComponent = ModComponents.DEATH_PENALTY.get(victim);
            if (deathPenaltyComponent.hasPenalty()
                    && (deathPenaltyComponent.limitCameraUUID != null || deathPenaltyComponent.limitPos != null)) {
                continue;
            }
            if (looseEndAlive && !ignoreLooseEnd) {
                ServerPlayer refugeePlayer = null;
                deathPenaltyComponent.limitCameraUUID = null;
                deathPenaltyComponent.limitPos = null;
                if (victim instanceof ServerPlayer sp) {
                    for (var p : sp.getServer().getPlayerList().getPlayers()) {
                        if (GameUtils.isPlayerAliveAndSurvival(p)) {
                            if (gameWorldComponent.isRole(p, TMMRoles.LOOSE_END)) {
                                refugeePlayer = p;
                                break;
                            }
                        }
                    }
                }
                if (refugeePlayer != null)
                    deathPenaltyComponent.limitCameraUUID = refugeePlayer.getUUID();
                if (deathPenaltyComponent.limitCameraUUID != null) {
                    deathPenaltyComponent.setPenalty(-1, true);
                    victim.sendSystemMessage(
                            Component.translatable("message.noellesroles.penalty.limit.loose_end")
                                    .withStyle(ChatFormatting.RED));
                    victim.displayClientMessage(
                            Component.translatable("message.noellesroles.penalty.limit.loose_end")
                                    .withStyle(ChatFormatting.RED),
                            true);
                    if (victim.hasPermissions(2)) {
                        victim.sendSystemMessage(Component.translatable("message.noellesroles.admin.free_cam_hint")
                                .withStyle(ChatFormatting.YELLOW));
                    }
                }
            } else if (limitView) {
                deathPenaltyComponent.setPenalty(-1, true);
                victim.sendSystemMessage(
                        Component.translatable("message.noellesroles.penalty.limit.god_job_couple")
                                .withStyle(ChatFormatting.RED));
                victim.displayClientMessage(
                        Component.translatable("message.noellesroles.penalty.limit.god_job_couple")
                                .withStyle(ChatFormatting.RED),
                        true);
                if (victim.hasPermissions(2)) {
                    victim.sendSystemMessage(Component.translatable("message.noellesroles.admin.free_cam_hint")
                            .withStyle(ChatFormatting.YELLOW));
                }
            } else if (doctorAlive && !ignoreDoctor) {
                deathPenaltyComponent.setPenalty(45 * 20, true);
                victim.displayClientMessage(
                        Component.translatable("message.noellesroles.doctor.penalty").withStyle(ChatFormatting.RED),
                        true);
                victim.sendSystemMessage(
                        Component.translatable("message.noellesroles.doctor.penalty").withStyle(ChatFormatting.RED));
                if (victim.hasPermissions(2)) {
                    victim.sendSystemMessage(Component.translatable("message.noellesroles.admin.free_cam_hint")
                            .withStyle(ChatFormatting.YELLOW));
                }
            }
        }
    }

    // ==================== 角色死亡物品传递辅助方法 ====================

    private static void handleDoctorDeath(Player victim) {
        if (victim == null || victim.level().isClientSide())
            return;
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(victim.level());
        if (!gameWorld.isRole(victim, ModRoles.DOCTOR))
            return;

        ArrayList<ItemStack> itemsToTransfer = new ArrayList<>();
        for (int i = 0; i < victim.getInventory().getContainerSize(); i++) {
            ItemStack stack = victim.getInventory().getItem(i);
            if (stack.getItem() == ModItems.ANTIDOTE || stack.getItem() == ModItems.PURIFY_BOMB) {
                itemsToTransfer.add(stack.copy());
                victim.getInventory().setItem(i, ItemStack.EMPTY);
            }
        }
        if (itemsToTransfer.isEmpty())
            return;

        Player targetPlayer = findAliveInnocent(victim);
        if (targetPlayer != null) {
            for (ItemStack item : itemsToTransfer) {
                targetPlayer.addItem(item);
            }
            if (targetPlayer instanceof ServerPlayer serverTarget) {
                serverTarget.displayClientMessage(
                        Component.translatable("message.noellesroles.doctor.items_inherited",
                                victim.getName().getString())
                                .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD),
                        true);
            }
        }
    }

    private static void handleAccountantDeath(Player victim) {
        if (victim == null || victim.level().isClientSide())
            return;
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(victim.level());
        if (!gameWorld.isRole(victim, ModRoles.ACCOUNTANT))
            return;

        ArrayList<ItemStack> itemsToTransfer = new ArrayList<>();
        for (int i = 0; i < victim.getInventory().getContainerSize(); i++) {
            ItemStack stack = victim.getInventory().getItem(i);
            if (stack.getItem() == ModItems.PASSBOOK) {
                itemsToTransfer.add(stack.copy());
                victim.getInventory().setItem(i, ItemStack.EMPTY);
            }
        }
        if (itemsToTransfer.isEmpty())
            return;

        Player targetPlayer = findAliveInnocent(victim);
        if (targetPlayer != null) {
            for (ItemStack item : itemsToTransfer) {
                targetPlayer.addItem(item);
            }
            if (targetPlayer instanceof ServerPlayer serverTarget) {
                serverTarget.displayClientMessage(
                        Component.translatable("message.noellesroles.accountant.passbook_inherited",
                                victim.getName().getString())
                                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                        true);
            }
        }
    }

    private static void handleFitterDeath(Player victim) {
        if (victim == null || victim.level().isClientSide())
            return;
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(victim.level());
        if (!gameWorld.isRole(victim, ModRoles.FITTER))
            return;

        ArrayList<ItemStack> itemsToTransfer = new ArrayList<>();
        for (int i = 0; i < victim.getInventory().getContainerSize(); i++) {
            ItemStack stack = victim.getInventory().getItem(i);
            if (stack.getItem() == ModItems.PLIERS) {
                itemsToTransfer.add(stack.copy());
                victim.getInventory().setItem(i, ItemStack.EMPTY);
            }
        }
        if (itemsToTransfer.isEmpty())
            return;

        Player targetPlayer = findAliveInnocent(victim);
        if (targetPlayer != null) {
            for (ItemStack item : itemsToTransfer) {
                targetPlayer.addItem(item);
            }
            if (targetPlayer instanceof ServerPlayer serverTarget) {
                serverTarget.displayClientMessage(
                        Component.translatable("message.noellesroles.fitter.pliers_inherited",
                                victim.getName().getString())
                                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                        true);
            }
        }
    }

    private static void handleStashedPlayerDeath(Player victim) {
        if (victim == null || victim.level().isClientSide())
            return;
        if (!(victim instanceof ServerPlayer sp))
            return;
        if (!org.agmas.noellesroles.game.roles.neutral.pelican.PelicanManager.isStashed(sp))
            return;
        org.agmas.noellesroles.game.roles.neutral.pelican.PelicanManager.onStashedPlayerDeath(sp);
    }

    private static void handleGodfatherDeath(Player victim) {
        if (victim == null || victim.level().isClientSide())
            return;
        if (!(victim instanceof ServerPlayer sp))
            return;
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(victim.level());
        if (!gameWorld.isRole(victim, ModRoles.GODFATHER))
            return;
        org.agmas.noellesroles.game.roles.neutral.mafia.MafiaManager.onGodfatherDeath(sp);
    }

    private static void handlePelicanDeath(Player victim) {
        if (victim == null || victim.level().isClientSide())
            return;
        if (!(victim instanceof ServerPlayer sp))
            return;
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(victim.level());
        if (!gameWorld.isRole(victim, ModRoles.PELICAN))
            return;
        org.agmas.noellesroles.game.roles.neutral.pelican.PelicanManager.onPelicanDeath(sp);
    }

    private static void handleLocksmithDeath(Player victim) {
        if (victim == null || victim.level().isClientSide())
            return;
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(victim.level());
        if (!gameWorld.isRole(victim, ModRoles.LOCKSMITH))
            return;

        ArrayList<ItemStack> itemsToTransfer = new ArrayList<>();
        for (int i = 0; i < victim.getInventory().getContainerSize(); i++) {
            ItemStack stack = victim.getInventory().getItem(i);
            if (stack.is(ModItems.NOELL_ARTISAN_KEY) || stack.is(TMMItems.LOCKPICK)) {
                itemsToTransfer.add(stack.copy());
                victim.getInventory().setItem(i, ItemStack.EMPTY);
            }
        }
        if (itemsToTransfer.isEmpty())
            return;

        Player targetPlayer = findNearestAliveInnocent(victim);
        if (targetPlayer != null) {
            for (ItemStack item : itemsToTransfer) {
                if (!targetPlayer.addItem(item)) {
                    targetPlayer.drop(item, false);
                }
            }
            if (targetPlayer instanceof ServerPlayer serverTarget) {
                serverTarget.displayClientMessage(
                        Component.translatable("message.noellesroles.locksmith.items_inherited",
                                victim.getName().getString())
                                .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD),
                        true);
            }
        }
    }

    // ==================== 查找存活平民辅助方法 ====================

    private static Player findAliveInnocent(Player victim) {
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(victim.level());
        for (Player player : victim.level().players()) {
            if (player == victim)
                continue;
            if (!GameUtils.isPlayerAliveAndSurvival(player))
                continue;
            SRERole role = gameWorld.getRole(player);
            if (role != null && role.isInnocent()) {
                return player;
            }
        }
        return null;
    }

    private static Player findNearestAliveInnocent(Player victim) {
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(victim.level());
        Player targetPlayer = null;
        double bestDistanceSqr = 10.0 * 10.0;
        for (Player player : victim.level().players()) {
            if (player == victim)
                continue;
            if (!GameUtils.isPlayerAliveAndSurvival(player))
                continue;
            SRERole role = gameWorld.getRole(player);
            if (role == null || !role.isInnocent())
                continue;
            double distanceSqr = player.distanceToSqr(victim);
            if (distanceSqr <= bestDistanceSqr) {
                bestDistanceSqr = distanceSqr;
                targetPlayer = player;
            }
        }
        return targetPlayer;
    }

    // ==================== 事件注册 ====================

    public static void register() {
        registerAllowPlayerDeath();
        registerAfterShieldAllowPlayerDeath();
        registerOnPlayerDeath();
        registerOnPlayerDeathWithKiller();
        registerAllowPlayerDeathWithKiller();
        registerAfterShieldAllowPlayerDeathWithKiller();
        registerOnPlayerKilledPlayerIdentifier();
        registerOnKillPlayerTriggered();
        registerOnShieldBroken();
        registerShouldDropOnDeath();
        registerPostDeathChain();
        registerOthers();
    }

    private static void registerOthers() {
        OnKillPlayerTriggered.EVENT.register((victim, spawnBody, killer, deathreason, forceKill) -> {
            var cca = InControlCCA.KEY.get(victim);
            if (cca.isControlling) {
                cca.bounceToSafe();
                return TrueFalseResult.FALSE;
            }
            return TrueFalseResult.PASS;
        });
        ShouldReloadDerringer.EVENT.register((victim, killer, deathReason) -> {
            if (RoleUtils.isPlayerTheJob(killer, ModRoles.GODFATHER)) {
                return TrueFalseResult.FALSE;
            }
            return TrueFalseResult.PASS;
        });
    }
    // --- AllowPlayerDeath ---

    private static void registerAllowPlayerDeath() {
        // 算命大师庇护 - 阻止死亡
        AllowPlayerDeath.EVENT.register((player, deathReason) -> {
            if (deathReason.equals(GameConstants.DeathReasons.FELL_OUT_OF_TRAIN)
                    || deathReason.getPath().equals("death_afk")
                    || deathReason.getPath().equals("shot_innocent")) {
                return true;
            }
            SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(player.level());
            WorldModifierComponent worldModifierComponent = WorldModifierComponent.KEY.get(player.level());
            for (var p : player.level().players()) {
                if (gameWorldComponent.isRole(p, ModRoles.FORTUNETELLER)) {
                    if (GameUtils.isPlayerAliveAndSurvival(p)
                            || (worldModifierComponent.isModifier(p, SEModifiers.SPLIT_PERSONALITY)
                                    && !SplitPersonalityComponent.KEY.get(p).isDeath())) {
                        if (FortunetellerPlayerComponent.KEY.get(p).triggerProtect(player)) {
                            return false;
                        }
                    }
                }
            }
            return true;
        });

        // 小丑精神爆发期间免死 + 窃皮者/斗士/跟踪者免死
        AllowPlayerDeath.EVENT.register((playerEntity, identifier) -> {
            // 小丑
            if (identifier == GameConstants.DeathReasons.FELL_OUT_OF_TRAIN)
                return true;
            if (identifier.getPath().equals("disconnected"))
                return true;
            if (identifier.getPath().equals("ignited"))
                return true;
            if (identifier.getPath().equals("failed_ignite"))
                return true;
            if (identifier.getPath().equals("heart_attack"))
                return true;
            SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(playerEntity.level());
            if (gameWorldComponent.isRole(playerEntity, ModRoles.JESTER)) {
                SREPlayerPsychoComponent component = SREPlayerPsychoComponent.KEY.get(playerEntity);
                if (component.getPsychoTicks() > GameConstants.getInTicks(0, 44)) {
                    return false;
                }
            }

            // 窃皮者皮肤死亡免疫
            if (handleSkincrawlerDeath(playerEntity, identifier))
                return false;
            // 斗士无敌反制
            if (handleBoxerInvulnerability(playerEntity, identifier))
                return false;
            // 跟踪者免疫
            if (handleStalkerImmunity(playerEntity, identifier))
                return false;

            return true;
        });
    }

    // --- AfterShieldAllowPlayerDeath ---

    private static void registerAfterShieldAllowPlayerDeath() {
        // 塔罗牌清除追踪目标
        AfterShieldAllowPlayerDeath.EVENT.register((victim, deathReason) -> {
            if (victim.level() instanceof ServerLevel serverLevel) {
                TarotAssemblyManager.clearTrackedTarget(serverLevel, victim.getUUID());
            }
            return true;
        });

        // 傀儡师假人死亡 + 起搏器
        AfterShieldAllowPlayerDeath.EVENT.register((victim, deathReason) -> {
            if (handlePuppeteerDeath(victim, deathReason))
                return false;
            handleDefibrillator(victim);
            return true;
        });
    }

    // --- OnPlayerDeath ---

    private static void registerOnPlayerDeath() {
        // 死亡掉枪 + 掉落自定义物品 + 角色清理
        OnPlayerDeath.EVENT.register((p, reason) -> {
            if (!(p instanceof ServerPlayer player))
                return;
            if (C4BackComponent.hasC4(player)) {
                var c4instance = C4BackComponent.getInstance(player);
                UUID planterUid = c4instance.getPlanter(player.getUUID());
                var planter = player.server.getPlayerList().getPlayer(planterUid);
                if (planter != null && GameUtils.isPlayerAliveAndSurvival(planter)) {
                    RoleUtils.insertStackInFreeSlot(planter, ModItems.C4.getDefaultInstance());
                    // c4instance.addC4(planterUid, planterUid);
                    planter.displayClientMessage(
                            Component.translatable("c4.back_to_planter").withStyle(ChatFormatting.RED), true);
                }
            }
            // 掉落左轮 (DropRevolverWhenDead)
            int dropCount = MCItemsUtils.clearItem(player, (t) -> {
                return t.getItem() instanceof DropRevolverWhenDead || t.is(TMMItems.REVOLVER)
                        || t.is(ModItems.BANDIT_REVOLVER);
            });
            while (dropCount > 0) {
                player.drop(TMMItems.REVOLVER.getDefaultInstance(), false);
                dropCount--;
            }

            // 自定义掉落 (DropWhenDead)
            Inventory container = player.getInventory();
            for (int k = 0; k < container.getContainerSize(); ++k) {
                ItemStack itemStack = container.getItem(k);
                if (itemStack.getItem() instanceof DropWhenDead dwd) {
                    var it = dwd.onDrop(player, itemStack);
                    if (it != null && !it.isEmpty()) {
                        player.drop(it, false);
                    }
                    container.setItem(k, ItemStack.EMPTY);
                }
            }

            ServerPlayNetworking.send(player, new CloseUiPayload());
            FortunetellerPlayerComponent.KEY.get(player).init();
            SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(player.level());

            if (!RefugeeComponent.KEY.get(player.level()).isAnyRevivals) {
                PuppeteerPlayerComponent.KEY.get(player).clear();
                if (gameWorldComponent.isRole(player, ModRoles.INSANE_KILLER)) {
                    InsaneKillerPlayerComponent.KEY.get(player).init();
                }
            }

            RoleUtils.removeAllEffects(player);

            // 葬仪死亡清除拖动状态
            if (gameWorldComponent.isRole(player, ModRoles.MORTICIAN_BODYMAKER)) {
                var morticianComponent = ModComponents.MORTICIAN_BODYMAKER.get(player);
                if (morticianComponent != null && morticianComponent.draggedBodyUuid != null) {
                    morticianComponent.draggedBodyUuid = null;
                    morticianComponent.sync();
                }
            }

            // 角色特定物品掉落
            dropRoleSpecificItems(player, gameWorldComponent);
        });
    }

    private static void dropRoleSpecificItems(ServerPlayer player, SREGameWorldComponent gameWorldComponent) {
        if (gameWorldComponent.isRole(player, ModRoles.JOJO)) {
            int dropCount = 1 + MCItemsUtils.clearItem(player, TMMItemTags.GUNS);
            while (dropCount > 0) {
                player.drop(TMMItems.REVOLVER.getDefaultInstance(), false);
                dropCount--;
            }
        }
        if (gameWorldComponent.isRole(player, ModRoles.ELF)) {
            int bowcount = SREItemUtils.clearItem(player, Items.BOW);
            int crossbowcount = SREItemUtils.clearItem(player, Items.CROSSBOW);
            int dropCount = bowcount + crossbowcount;
            while (dropCount > 0) {
                player.drop(TMMItems.REVOLVER.getDefaultInstance(), false);
                dropCount--;
            }
        }
        if (gameWorldComponent.isRole(player, ModRoles.MARTIAL_ARTS_INSTRUCTOR)) {
            int nunchuckCount = SREItemUtils.clearItem(player, TMMItems.NUNCHUCK);
            while (nunchuckCount > 0) {
                player.drop(TMMItems.REVOLVER.getDefaultInstance(), false);
                nunchuckCount--;
            }
        }
        if (gameWorldComponent.isRole(player, ModRoles.GUARD)) {
            int batonCount = SREItemUtils.clearItem(player, ModItems.BATON);
            while (batonCount > 0) {
                player.drop(TMMItems.REVOLVER.getDefaultInstance(), false);
                batonCount--;
            }
        }
        if (gameWorldComponent.isRole(player, ModRoles.SEA_KING)) {
            if (player.level() instanceof ServerLevel level) {
                for (var e : level.getAllEntities()) {
                    if (e instanceof ThrownTrident te)
                        if (te.getOwner().getUUID().equals(player.getUUID())) {
                            player.drop(TMMItems.REVOLVER.getDefaultInstance(), false);
                            te.discard();
                        }
                }
            }
        }
        {
            int tridentCount = SREItemUtils.clearItem(player, Items.TRIDENT);
            while (tridentCount > 0) {
                player.drop(TMMItems.REVOLVER.getDefaultInstance(), false);
                tridentCount--;
            }
        }
        if (gameWorldComponent.isRole(player, ModRoles.WATER_GHOST)) {
            int tridentCount = SREItemUtils.clearItem(player, Items.TRIDENT);
            while (tridentCount > 0) {
                player.drop(TMMItems.REVOLVER.getDefaultInstance(), false);
                tridentCount--;
            }
        }
        if (gameWorldComponent.isRole(player, ModRoles.SWAST)) {
            int sniperRifleCount = SREItemUtils.clearItem(player, TMMItems.SNIPER_RIFLE);
            while (sniperRifleCount > 0) {
                player.drop(TMMItems.REVOLVER.getDefaultInstance(), false);
                sniperRifleCount--;
            }
        }
        if (gameWorldComponent.isRole(player, ModRoles.BETTER_VIGILANTE)) {
            BetterVigilantePlayerComponent.KEY.get(player).init();
        }
    }

    // --- 死亡后链式处理 ---

    private static void registerPostDeathChain() {
        OnPlayerDeath.EVENT.register((victim, deathReason) -> {
            handleStashedPlayerDeath(victim);
            handleDoctorDeath(victim);
            handleLocksmithDeath(victim);
            handleAccountantDeath(victim);
            handleFitterDeath(victim);
            handlePelicanDeath(victim);
            handleGodfatherDeath(victim);
            handleDeathPenalty(victim);
            handleGlitchRobotDeath(victim);
            handleCakeMakerDeath(victim);
            handleHunterDeath(victim);
        });

        // 实体交互方块 DEATH 条件触发（无击杀者场景：摔出列车等）
        OnPlayerDeath.EVENT.register((victim, deathReason) -> {
            if (victim instanceof ServerPlayer sp && victim.level() instanceof ServerLevel sl) {
                EntityInteractionBlockEntity.onPlayerDeath(sl, sp, deathReason);
            }
        });
    }

    // --- OnPlayerDeathWithKiller ---

    private static void registerOnPlayerDeathWithKiller() {
        // 渡鸦击杀目标
        OnPlayerDeathWithKiller.EVENT.register((victim, killer, reason) -> {
            if (killer == null || !SREGameWorldComponent.KEY.get(killer.level()).isRole(killer, ModRoles.RAVEN))
                return;
            RavenPlayerComponent raven = ModComponents.RAVEN.get(killer);
            if (raven.canKill(victim))
                raven.onTargetKilled(victim);
        });

        // 血迹路径
        OnPlayerDeathWithKiller.EVENT.register((victim, killer, deathReason) -> {
            io.wifi.starrailexpress.SREConfig cfg = io.wifi.starrailexpress.SREConfig.instance();
            if (cfg == null || !cfg.enableForensicEvidence || !cfg.forensicBloodTrail)
                return;
            if (victim == null || killer == null || victim.level().isClientSide)
                return;
            if (!(killer instanceof ServerPlayer killerSp))
                return;
            SREGameWorldComponent gw = SREGameWorldComponent.KEY.get(victim.level());
            if (gw == null || !gw.isRunning())
                return;
            io.wifi.starrailexpress.game.forensic.ForensicCategory cat = io.wifi.starrailexpress.game.forensic.ForensicCategory
                    .fromDeathReason(deathReason);
            if (cat != io.wifi.starrailexpress.game.forensic.ForensicCategory.BLADE
                    && cat != io.wifi.starrailexpress.game.forensic.ForensicCategory.FIREARM
                    && cat != io.wifi.starrailexpress.game.forensic.ForensicCategory.BLUNT)
                return;
            int track_distance = GameConstants.getBloodTrackWetDistance();
            if (killer.distanceToSqr(victim) <= track_distance)
                return;
            int bleedTicks = switch (cat) {
                case FIREARM -> 7 * 20;
                case BLADE -> 4 * 20;
                case BLUNT -> 5 * 20;
                default -> 5 * 20;
            };
            gw.startKillerBleed(killerSp, victim.position(), victim.level().getGameTime(), bleedTicks);
        });

        // 巡警附近死亡感知
        OnPlayerDeathWithKiller.EVENT.register((victim, killer, deathReason) -> {
            SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(victim.level());
            if (gameWorld == null || !gameWorld.isRunning())
                return;
            for (Player player : victim.level().players()) {
                if (player.getUUID().equals(victim.getUUID()))
                    continue;
                if (!gameWorld.isRole(player, ModRoles.PATROLLER))
                    continue;
                if (!GameUtils.isPlayerAliveAndSurvival(player))
                    continue;
                if (player.distanceToSqr(victim) > 50 * 50
                        || !PatrollerPlayerComponent.isBoundTargetVisible(victim, player))
                    continue;
                PatrollerPlayerComponent patrollerComponent = ModComponents.PATROLLER.get(player);
                patrollerComponent.onNearbyDeath();
            }
        });

        // 播音员死亡广播
        OnPlayerDeathWithKiller.EVENT.register((victim, killer, deathReason) -> {
            final var world = victim.level();
            if (world.isClientSide)
                return;
            SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(world);
            if (gameWorldComponent.isRole(victim, ModRoles.BROADCASTER)) {
                String last_message = null;
                BroadcasterPlayerComponent comp = BroadcasterPlayerComponent.KEY.get(victim);
                if (comp != null) {
                    last_message = comp.getStoredStr();
                }
                Component msg;
                if (last_message != null && !last_message.trim().isEmpty()) {
                    msg = Component
                            .translatable("message.noellesroles.broadcaster.death_with_msg",
                                    Component.literal(last_message).withStyle(ChatFormatting.GOLD))
                            .withStyle(ChatFormatting.RED);
                } else {
                    msg = Component.translatable("message.noellesroles.broadcaster.death")
                            .withStyle(ChatFormatting.RED);
                }
                world.players().forEach(player -> {
                    if (player instanceof ServerPlayer sp) {
                        player.playNotifySound(SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 0.5F, 1.3F);
                        org.agmas.noellesroles.packet.BroadcastMessageS2CPacket packet = new org.agmas.noellesroles.packet.BroadcastMessageS2CPacket(
                                msg);
                        ServerPlayNetworking.send(sp, packet);
                    }
                });
            }
        });

        // 复仇者绑定目标死亡激活
        OnPlayerDeathWithKiller.EVENT.register((victim, killer, deathReason) -> {
            SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(victim.level());
            if (gameWorld == null)
                return;
            var refugeeC = RefugeeComponent.KEY.get(victim.level());
            if (refugeeC.isAnyRevivals)
                return;
            for (Player player : victim.level().players()) {
                if (!gameWorld.isRole(player, ModRoles.AVENGER))
                    continue;
                if (player.equals(victim))
                    continue;
                AvengerPlayerComponent avengerComponent = ModComponents.AVENGER.get(player);
                if (avengerComponent.targetPlayer != null &&
                        avengerComponent.targetPlayer.equals(victim.getUUID()) &&
                        !avengerComponent.activated) {
                    if (killer != null) {
                        avengerComponent.activate(killer.getUUID());
                        avengerComponent.targetName = killer.getName().getString();
                    } else {
                        avengerComponent.activate(null);
                    }
                    String victimName = victim.getName().getString();
                    String killerName = killer != null ? killer.getName().getString() : "Unknown";
                    player.displayClientMessage(
                            Component.translatable("message.avenger.target_died", victimName, killerName)
                                    .withStyle(ChatFormatting.GOLD),
                            true);
                    // 复仇者
                }
            }
        });

        // 观者冷静姿态反杀
        OnPlayerDeathWithKiller.EVENT.register((victim, killer, deathReason) -> {
            var gameWorldComponent = SREGameWorldComponent.KEY.get(victim.level());
            if (gameWorldComponent.isRole(victim, ModRoles.WATCHER)) {
                var watcher = WatcherPlayerComponent.KEY.get(victim);
                if (watcher.isInCalmStance()) {
                    if (gameWorldComponent.isInnocent(killer)) {
                        GameUtils.killPlayer(killer, true, victim, Noellesroles.id("shot_innocent"));
                    }
                }
            }
        });

        // 影隼死亡发放喷气背包
        OnPlayerDeathWithKiller.EVENT.register((victim, killer, deathReason) -> {
            ShadowFalconPlayerComponent.onDeathGiveJetpacks(victim);
        });

        // 监视者 - 杀手击杀后冷却
        OnPlayerDeathWithKiller.EVENT.register((victim, killer, deathReason) -> {
            if (killer == null)
                return;
            var gameWorldComponent = SREGameWorldComponent.KEY.get(victim.level());
            for (var p : victim.level().players()) {
                if (gameWorldComponent.isRole(p, ModRoles.MONITOR)) {
                    if (p.getCooldowns().isOnCooldown(Items.BARRIER)) {
                        continue;
                    }
                    p.getCooldowns().addCooldown(Items.BARRIER, 60 * 20);
                    p.displayClientMessage(
                            Component.translatable("message.monitor.killer_killed", victim.getName())
                                    .withStyle(ChatFormatting.AQUA),
                            true);
                }
            }
        });

        // 葬仪被动-引渡：杀手阵营死亡广播
        OnPlayerDeathWithKiller.EVENT.register((victim, killer, deathReason) -> {
            var gameWorldComponent = SREGameWorldComponent.KEY.get(victim.level());
            if (gameWorldComponent == null || !gameWorldComponent.isRunning())
                return;
            boolean hasAliveMortician = false;
            for (Player player : victim.level().players()) {
                if (GameUtils.isPlayerAliveAndSurvival(player)
                        && gameWorldComponent.isRole(player, ModRoles.MORTICIAN_BODYMAKER)) {
                    hasAliveMortician = true;
                    break;
                }
            }
            if (!hasAliveMortician)
                return;
            var victimRole = gameWorldComponent.getRole(victim);
            if (victimRole == null || !gameWorldComponent.isKillerTeamRole(victimRole))
                return;
            Component roleName = RoleUtils.getRoleName(victimRole.identifier());
            Component deathMessage = Component
                    .translatable("message.noellesroles.mortician_bodymaker.passive_death", roleName)
                    .withStyle(ChatFormatting.GOLD);
            for (Player player : victim.level().players()) {
                if (!GameUtils.isPlayerAliveAndSurvival(player))
                    continue;
                var targetRole = gameWorldComponent.getRole(player);
                if (targetRole == null)
                    continue;
                if (!targetRole.canUseKiller() && !targetRole.isNeutralForKiller()
                        && !gameWorldComponent.isRole(player, ModRoles.MAGICIAN))
                    continue;
                if (player instanceof ServerPlayer sp) {
                    org.agmas.noellesroles.commands.BroadcastCommand.BroadcastMessage(sp, deathMessage);
                }
            }
        });

        // 实体交互方块 DEATH 条件触发
        OnPlayerDeathWithKiller.EVENT.register((victim, killer, deathReason) -> {
            if (victim instanceof ServerPlayer sp && victim.level() instanceof ServerLevel sl) {
                EntityInteractionBlockEntity.onPlayerDeath(sl, sp, deathReason);
            }
        });
    }

    // --- AllowPlayerDeathWithKiller ---

    private static void registerAllowPlayerDeathWithKiller() {
        // 渡鸦狩猎击杀限制
        AllowPlayerDeathWithKiller.EVENT.register((victim, killer, reason) -> {
            if (killer == null || !SREGameWorldComponent.KEY.get(killer.level()).isRole(killer, ModRoles.RAVEN))
                return true;
            RavenPlayerComponent raven = ModComponents.RAVEN.get(killer);
            if (!raven.isHunting())
                return true;
            if (!raven.canKill(victim)) {
                raven.endHunt(true);
                return false;
            }
            return true;
        });

        // 渡鸦狩猎期间自身免死
        AllowPlayerDeathWithKiller.EVENT.register((victim, killer, reason) -> {
            if (!SREGameWorldComponent.KEY.get(victim.level()).isRole(victim, ModRoles.RAVEN))
                return true;
            RavenPlayerComponent raven = ModComponents.RAVEN.get(victim);
            return !raven.isHunting();
        });

        // 肉汁独处保护
        AllowPlayerDeathWithKiller.EVENT.register((victim, killer, deathReason) -> {
            SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(victim.level());
            if (!gameWorld.isRole(victim, ModRoles.MEATBALL))
                return true;
            if (deathReason != null && (deathReason.getPath().equals("bomb_death")
                    || deathReason.getPath().equals("poison")
                    || deathReason.getPath().equals("infection")
                    || deathReason.getPath().equals("ignited"))) {
                return true;
            }
            if (gameWorld.isRole(killer, TMMRoles.LOOSE_END)
                    || gameWorld.isRole(killer, SpecialGameModeRoles.SUPER_LOOSE_END)) {
                return true;
            }
            if (killer == null || gameWorld.isInnocent(killer))
                return true;

            // 门框检测
            double doorCheckRange = 1.5;
            BlockPos meatballPos = victim.blockPosition();
            for (int dx = -2; dx <= 2; dx++) {
                for (int dy = -2; dy <= 2; dy++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        BlockPos checkPos = meatballPos.offset(dx, dy, dz);
                        double dist = Math.sqrt(
                                (checkPos.getX() + 0.5 - victim.getX()) * (checkPos.getX() + 0.5 - victim.getX()) +
                                        (checkPos.getY() + 0.5 - victim.getY())
                                                * (checkPos.getY() + 0.5 - victim.getY())
                                        +
                                        (checkPos.getZ() + 0.5 - victim.getZ())
                                                * (checkPos.getZ() + 0.5 - victim.getZ()));
                        if (dist <= doorCheckRange) {
                            if (victim.level().getBlockState(checkPos).getBlock() instanceof SmallDoorBlock) {
                                if (victim instanceof ServerPlayer sp) {
                                    sp.displayClientMessage(
                                            Component.translatable("message.noellesroles.meatball.near_door")
                                                    .withStyle(ChatFormatting.RED),
                                            true);
                                }
                                return true;
                            }
                        }
                    }
                }
            }

            double safeDistanceSq = 4.0 * 4.0;
            double safeHeightSq = 3.0 * 3.0;
            for (Player nearbyPlayer : victim.level().players()) {
                if (nearbyPlayer == victim || nearbyPlayer == killer)
                    continue;
                if (!GameUtils.isPlayerAliveAndSurvival(nearbyPlayer))
                    continue;
                double dx = nearbyPlayer.getX() - victim.getX();
                double dy = nearbyPlayer.getY() - victim.getY();
                double dz = nearbyPlayer.getZ() - victim.getZ();
                double horizontalDistSq = dx * dx + dz * dz;
                if (horizontalDistSq <= safeDistanceSq && dy * dy <= safeHeightSq) {
                    if (gameWorld.isInnocent(nearbyPlayer)) {
                        if (victim instanceof ServerPlayer sp) {
                            sp.displayClientMessage(
                                    Component.translatable("message.noellesroles.meatball.protected")
                                            .withStyle(ChatFormatting.GREEN),
                                    true);
                        }
                        return false;
                    }
                }
            }
            return true;
        });

        // 小丑绕过不屈服
        AllowPlayerDeathWithKiller.EVENT.register((victim, killer, deathReason) -> {
            if (killer != null) {
                SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(victim.level());
                if (gameWorldComponent.isRole(victim, ModRoles.JESTER)
                        && !gameWorldComponent.isRole(killer, ModRoles.JESTER)
                        && gameWorldComponent.isInnocent(killer)) {
                    SREPlayerPsychoComponent component = SREPlayerPsychoComponent.KEY.get(victim);
                    if (component.getPsychoTicks() <= 0) {
                        component.startPsycho();
                        component.psychoTicks = GameConstants.getInTicks(0, 45);
                        component.armour = 0;
                        return false;
                    }
                }
            }
            return true;
        });
    }

    // --- AfterShieldAllowPlayerDeathWithKiller ---

    private static void registerAfterShieldAllowPlayerDeathWithKiller() {
        // 不屈修饰符
        AfterShieldAllowPlayerDeathWithKiller.EVENT.register((victim, killer, deathReason) -> {
            if (victim == null || victim.level().isClientSide())
                return true;
            var worldModifiers = WorldModifierComponent.KEY.get(victim.level());
            if (worldModifiers == null)
                return true;
            if (!worldModifiers.isModifier(victim.getUUID(), SEModifiers.UNYIELDING))
                return true;

            SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(victim.level());
            var victimRole = gameWorld.getRole(victim);
            var killerRole = killer != null ? gameWorld.getRole(killer) : null;

            if (victimRole != null && !victimRole.isInnocent() && victimRole.isCanUseKiller()) {
                if (killer != null && killer != victim && killerRole != null
                        && !killerRole.isInnocent() && killerRole.isCanUseKiller()) {
                    if (victim instanceof ServerPlayer sp) {
                        sp.displayClientMessage(Component.translatable("message.sre.unyielding.immune_killer")
                                .withStyle(ChatFormatting.RED), true);
                        sp.serverLevel().playSound(null, sp.blockPosition(), SoundEvents.SHIELD_BLOCK,
                                SoundSource.MASTER, 1.0F, 1.0F);
                        ServerLevel level = sp.serverLevel();
                        for (int i = 0; i < 30; i++) {
                            level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING,
                                    sp.getX() + level.random.nextDouble() * 2.0 - 1.0,
                                    sp.getY() + 0.5 + level.random.nextDouble() * 2.5,
                                    sp.getZ() + level.random.nextDouble() * 2.0 - 1.0,
                                    1, 0, 0, 0, 0);
                        }
                    }
                    return false;
                }
            }

            if (victimRole != null && victimRole.isInnocent() && killer != null && killer != victim
                    && killerRole != null && killerRole.isInnocent()) {
                if (!SEModifiers.UNYIELDING_IMMUNITY_USED.contains(victim.getUUID())) {
                    SEModifiers.UNYIELDING_IMMUNITY_USED.add(victim.getUUID());
                    if (victim instanceof ServerPlayer sp) {
                        sp.serverLevel().playSound(null, sp.blockPosition(), SoundEvents.SHIELD_BLOCK,
                                SoundSource.MASTER, 1.0F, 1.0F);
                        sp.displayClientMessage(Component.translatable("message.sre.unyielding.immune_civilian")
                                .withStyle(ChatFormatting.GREEN), true);
                        ServerLevel level = sp.serverLevel();
                        for (var p : level.players()) {
                            level.sendParticles(p, ParticleTypes.TOTEM_OF_UNDYING, true,
                                    sp.getX(), sp.getY(), sp.getZ(),
                                    30, 1, 1, 1, 0);
                        }
                    }
                    return false;
                }
            }
            return true;
        });

        // 塔罗牌清除追踪目标
        AfterShieldAllowPlayerDeathWithKiller.EVENT.register((victim, killer, deathReason) -> {
            if (victim.level() instanceof ServerLevel serverLevel) {
                TarotAssemblyManager.clearTrackedTarget(serverLevel, victim.getUUID());
            }
            return true;
        });

        // 生死状 - 明星反伤
        AfterShieldAllowPlayerDeathWithKiller.EVENT.register((player, killer, deathReason) -> {
            SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(player.level());
            if (gameWorldComponent.isRole(player, ModRoles.SUPERSTAR))
                return true;
            var lifeAndDeathShape = MCItemsUtils.getFirstMatchedItem(player, ModItems.LIFE_AND_DEATH_SHAPE);
            if (lifeAndDeathShape == null)
                return true;
            String starPlayerName = lifeAndDeathShape.getOrDefault(SREDataComponentTypes.OWNER, "");
            for (var p : player.level().players()) {
                if (gameWorldComponent.isRole(p, ModRoles.SUPERSTAR)) {
                    if (p.getScoreboardName().equals(starPlayerName)) {
                        if (GameUtils.isPlayerAliveAndSurvival(p)) {
                            SRE.REPLAY_MANAGER.recordCustomEvent(
                                    Component.translatable("hud.noellesroles.star.dead.life_and_death_shape.event",
                                            GameReplayUtils.getReplayPlayerDisplayText(p, true),
                                            GameReplayUtils.getReplayPlayerDisplayText(player, true)));
                            p.displayClientMessage(Component.translatable(
                                    "hud.noellesroles.star.dead.life_and_death_shape", player.getName())
                                    .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD), true);
                            player.displayClientMessage(Component.translatable(
                                    "hud.noellesroles.star.dead.life_and_death_shape.victim", p.getName())
                                    .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), true);
                            MCItemsUtils.clearItem(player, ModItems.LIFE_AND_DEATH_SHAPE, 1);
                            GameUtils.killPlayer(p, true, killer, deathReason);
                            return false;
                        }
                    }
                }
            }
            return true;
        });
    }

    // --- OnPlayerKilledPlayerIdentifier ---

    private static void registerOnPlayerKilledPlayerIdentifier() {
        OnPlayerKilledPlayerIdentifier.EVENT.register((victim, killer, deathReason) -> {
            var gameWorldComponent = SREGameWorldComponent.KEY.get(victim.level());

            // 雇佣兵契约目标击杀
            if (gameWorldComponent.isRole(killer, ModRoles.MERCENARY)) {
                var mercenary = MercenaryPlayerComponent.KEY.get(killer);
                if (mercenary != null && mercenary.isContractTarget(victim)) {
                    mercenary.onContractTargetKilled();
                }
            }

            // 观者冷静姿态误杀惩罚
            if (gameWorldComponent.isRole(killer, ModRoles.WATCHER)) {
                var watcher = WatcherPlayerComponent.KEY.get(killer);
                if (watcher.isInCalmStance()) {
                    if (!deathReason.getPath().equals("shot_innocent")) {
                        if (gameWorldComponent.isInnocent(victim)) {
                            GameUtils.killPlayer(killer, true, null, Noellesroles.id("watcher_calm_kill"));
                        }
                    }
                }
            }

            // 强盗金钱盗取
            if (gameWorldComponent.isRole(killer, ModRoles.BANDIT)) {
                var banditComponent = ModComponents.BANDIT.get(killer);
                if (banditComponent != null) {
                    banditComponent.handleKilledVictim(victim);
                }
            }

            // 小偷击杀奖励
            if (gameWorldComponent.isRole(killer, ModRoles.THIEF)) {
                var thiefComponent = ThiefPlayerComponent.KEY.get(killer);
                if (thiefComponent != null) {
                    thiefComponent.handleKilledVictim(victim);
                }
            }

            // 刀杀速度加成
            if (deathReason.getPath().equals(GameConstants.DeathReasons.KNIFE.getPath())) {
                killer.addEffect(new MobEffectInstance(
                        MobEffects.MOVEMENT_SPEED, 1, 1, false, false, false));
            }
        });
    }

    // --- OnKillPlayerTriggered ---

    private static void registerOnKillPlayerTriggered() {
        OnKillPlayerTriggered.EVENT.register((victim, spawnBody, _killer, deathReasosn, forceKill) -> {
            final var level = victim.level();
            final var gameWorldComponent = SREGameWorldComponent.KEY.get(level);
            if (gameWorldComponent != null && gameWorldComponent.isRunning()) {
                final var inControlCCA = InControlCCA.KEY.get(victim);
                if (inControlCCA != null && inControlCCA.isControlling && inControlCCA.controller != null) {
                    var controllerPlayer = level.getPlayerByUUID(inControlCCA.controller);
                    if (controllerPlayer != null) {
                        io.wifi.starrailexpress.data.PlayerEconomyManager.addCoinNum(controllerPlayer,
                                NoellesRolesConfig.HANDLER.instance().manipulatorTargetDeathReward);
                        controllerPlayer.displayClientMessage(Component.translatable(
                                "message.noellesroles.manipulator.target_died", victim.getName())
                                .withStyle(ChatFormatting.GOLD), true);
                        var controllerComp = org.agmas.noellesroles.game.roles.killer.manipulator.ManipulatorPlayerComponent.KEY
                                .get(controllerPlayer);
                        if (controllerComp != null) {
                            controllerComp.stopControl(false);
                        }
                    }
                }
                inControlCCA.isControlling = false;
                inControlCCA.sync();
            }
            return TrueFalseResult.PASS;
        });
    }

    // --- OnShieldBroken ---

    private static void registerOnShieldBroken() {
        OnShieldBroken.EVENT.register((victim, killer) -> {
            var gameWorldComponent = SREGameWorldComponent.KEY.get(victim.level());

            // 巫师魔药护盾破碎
            if (gameWorldComponent.isRole(victim, ModRoles.WIZARD)) {
                ModComponents.WIZARD.get(victim).onPotionShieldBroken();
            }
            // 观者护盾破碎标记
            if (gameWorldComponent.isRole(victim, ModRoles.WATCHER)) {
                WatcherPlayerComponent.KEY.get(victim).markShieldConsumed();
            }
            // 雇佣兵强制目标
            if (killer != null && gameWorldComponent.isRole(victim, ModRoles.MERCENARY)) {
                var mercenary = MercenaryPlayerComponent.KEY.get(victim);
                if (mercenary != null) {
                    mercenary.setForcedTarget(killer);
                    victim.displayClientMessage(
                            Component.translatable("message.noellesroles.mercenary.new_forced_target",
                                    killer.getName())
                                    .withStyle(ChatFormatting.RED),
                            true);
                }
            }
            // 影隼临时护盾破碎
            if (gameWorldComponent.isRole(victim, ModRoles.SHADOW_FALCON)) {
                ShadowFalconPlayerComponent shadowFalconComponent = ShadowFalconPlayerComponent.KEY.get(victim);
                shadowFalconComponent.onShieldBroken();
            }
        });
    }

    // --- ShouldDropOnDeath ---

    private static void registerShouldDropOnDeath() {
        ShouldDropOnDeath.EVENT.register((stack) -> {
            final var key = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            if ("exposure:album".equals(key) || "exposure:photograph".equals(key)
                    || "exposure:stacked_photographs".equals(key) || stack.is(ModItems.PATROLLER_REVOLVER)) {
                return true;
            }
            if (stack.is(ModItems.DEALER_PACKAGE))
                return true;
            if (RoleShopHandler.isOldmanEasterEggRod(stack))
                return true;

            if (stack.is(ModItems.NEWSPAPER)) {
                if (stack.has(SREDataComponentTypes.WRITTEN_BOOK_CONTENT)) {
                    return true;
                } else {
                    return false;
                }
            }
            if (stack.is(ModItems.MASTER_KEY) || stack.is(Items.BUNDLE) ||
                    stack.is(Items.WRITTEN_BOOK)) {
                return true;
            }
            return false;
        });
    }
}
