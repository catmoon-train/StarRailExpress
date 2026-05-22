package org.agmas.noellesroles.init;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.SREGameModes;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.api.replay.GameReplayUtils;
import io.wifi.starrailexpress.cca.SREGameRoundEndComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerPsychoComponent;
import io.wifi.starrailexpress.cca.SREPlayerStatsComponent;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.content.entity.NoteEntity;
import io.wifi.starrailexpress.event.*;
import io.wifi.starrailexpress.event.AllowShootRevolverDrop.ShouldDropResult;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.ServerTaskInfoClasses;
import io.wifi.starrailexpress.game.roles.SpecialGameModeRoles;
import io.wifi.starrailexpress.index.SREDataComponentTypes;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.index.tag.TMMItemTags;
import io.wifi.starrailexpress.network.CloseUiPayload;
import io.wifi.starrailexpress.network.RemoveStatusBarPayload;
import io.wifi.starrailexpress.util.SREItemUtils;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Saddleable;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.Vec3;
import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.harpymodloader.events.ModdedRoleRemoved;
import org.agmas.noellesroles.*;
import org.agmas.noellesroles.commands.BroadcastCommand;
import org.agmas.noellesroles.component.DeathPenaltyComponent;
import org.agmas.noellesroles.component.DefibrillatorComponent;
import org.agmas.noellesroles.component.InfectedPlayerComponent;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.agmas.noellesroles.content.effects.TimeStopEffect;
import org.agmas.noellesroles.content.entity.HallucinationAreaManager;
import org.agmas.noellesroles.content.entity.PuppeteerBodyEntity;
import org.agmas.noellesroles.content.entity.ServerSmokeAreaManager;
import org.agmas.noellesroles.content.entity.WheelchairEntity;
import org.agmas.noellesroles.content.item.HandCuffsItem;
import org.agmas.noellesroles.content.item.RadioItem;
import org.agmas.noellesroles.content.item.BatonHandler;
import org.agmas.noellesroles.content.item.BenevolenceSwordHandler;
import org.agmas.noellesroles.content.item.RiotShieldHandler;
import org.agmas.noellesroles.events.OnVendingMachinesBuyItems;
import org.agmas.noellesroles.events.OnShopPurchase;
import org.agmas.noellesroles.game.modes.ChairWheelRaceGame;
import org.agmas.noellesroles.game.modifier.NRModifiers;
import org.agmas.noellesroles.game.modifier.expedition.ExpeditionComponent;
import org.agmas.noellesroles.game.roles.Innocent.avenger.AvengerPlayerComponent;
import org.agmas.noellesroles.game.roles.Innocent.awesome_binglus.AwesomePlayerComponent;
import org.agmas.noellesroles.game.roles.Innocent.boxer.BoxerPlayerComponent;
import org.agmas.noellesroles.game.roles.Innocent.broadcaster.BroadcasterPlayerComponent;
import org.agmas.noellesroles.game.roles.Innocent.fool.TarotAssemblyManager;
import org.agmas.noellesroles.game.roles.Innocent.fortuneteller.FortunetellerPlayerComponent;
import org.agmas.noellesroles.game.roles.Innocent.glitch_robot.GlitchRobotPlayerComponent;
import org.agmas.noellesroles.game.roles.Innocent.hoan_meirin.HoanMeirinFistPunchHandler;
import org.agmas.noellesroles.game.roles.Innocent.veteran.VeteranKnifeHandler;
import org.agmas.noellesroles.game.roles.Innocent.voodoo.VoodooDeathHandler;
import org.agmas.noellesroles.game.roles.killer.conspirator.ConspiratorKilledPlayer;
import org.agmas.noellesroles.game.roles.vigilante.guard.GuardPlayerHandler;
import org.agmas.noellesroles.game.roles.killer.executioner.ExecutionerPlayerComponent;
import org.agmas.noellesroles.game.roles.killer.executioner.ShootingFrenzyPlayerComponent;
import org.agmas.noellesroles.game.roles.killer.insane_killer.InsaneKillerPlayerComponent;
import org.agmas.noellesroles.game.roles.killer.ma_chen_xu.MaChenXuEventHandler;
import org.agmas.noellesroles.game.roles.killer.manipulator.InControlCCA;
import org.agmas.noellesroles.game.roles.killer.ninja.NinjaPlayerComponent;
import org.agmas.noellesroles.game.roles.killer.stalker.StalkerPlayerComponent;
import org.agmas.noellesroles.game.roles.killer.watcher.WatcherPlayerComponent;
import org.agmas.noellesroles.game.roles.killer.shadow_falcon.ShadowFalconPlayerComponent;
import org.agmas.noellesroles.game.roles.neutral.commander.CommanderHandler;
import org.agmas.noellesroles.game.roles.neutral.gambler.GamblerHandler;
import org.agmas.noellesroles.game.roles.neutral.cuckoo.CuckooEggHandler;
import org.agmas.noellesroles.game.roles.neutral.infected.InfectedWinChecker;
import org.agmas.noellesroles.game.roles.neutral.mercenary.MercenaryPlayerComponent;
import org.agmas.noellesroles.game.roles.neutral.puppeteer.PuppeteerPlayerComponent;
import org.agmas.noellesroles.game.roles.neutral.thief.ThiefPlayerComponent;
import org.agmas.noellesroles.game.roles.neutral.wayfarer.WayfarerPlayerComponent;
import org.agmas.noellesroles.game.roles.special.better_vigilante.BetterVigilantePlayerComponent;
import org.agmas.noellesroles.game.roles.vigilante.patroller.PatrollerPlayerComponent;
import org.agmas.noellesroles.packet.BloodConfigS2CPacket;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role.RedHouseRoles;
import org.agmas.noellesroles.role.TraitorAndModifiers;
import org.agmas.noellesroles.utils.EntityClearUtils;
import org.agmas.noellesroles.utils.MCItemsUtils;
import org.agmas.noellesroles.utils.MapScanner;
import org.agmas.noellesroles.utils.RoleUtils;
import pro.fazeclan.river.stupid_express.constants.SEModifiers;
import pro.fazeclan.river.stupid_express.constants.SERoles;
import pro.fazeclan.river.stupid_express.modifier.refugee.cca.PlayerStatsBeforeRefugee;
import pro.fazeclan.river.stupid_express.modifier.refugee.cca.RefugeeComponent;
import pro.fazeclan.river.stupid_express.modifier.split_personality.cca.SplitPersonalityComponent;

import java.util.*;

public class ModEventsRegister {
    private static AttributeModifier noJumpingAttribute = new AttributeModifier(
            Noellesroles.id("no_jumping"), -1.0f, AttributeModifier.Operation.ADD_VALUE);
    private static final Map<UUID, Vec3> oldmanPigRidePositions = new HashMap<>();
    // private static AttributeModifier oldmanAttribute = new AttributeModifier(
    // Noellesroles.id("oldman"), -0.4f, AttributeModifier.Operation.ADD_VALUE);
    // private static AttributeModifier windYaoseScaleAttribute = new
    // AttributeModifier(
    // Noellesroles.id("wind_yaose"), -0.2f, AttributeModifier.Operation.ADD_VALUE);

    /**
     * 处理拳击手无敌反制
     * 钢筋铁骨期间可以反弹任何死亡
     *
     * @param victim      受害者
     * @param deathReason 死亡原因
     * @return true 表示成功反制，应阻止死亡
     */
    private static boolean handleBoxerInvulnerability(Player victim, ResourceLocation deathReason) {
        if (victim == null || victim.level().isClientSide())
            return false;

        // 检查受害者是否是拳击手
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(victim.level());

        // 模仿者拳击手无敌检测
        if (gameWorld.isRole(victim, ModRoles.IMITATOR)) {
            org.agmas.noellesroles.game.roles.killer.imitator.ImitatorPlayerComponent imitComp = ModComponents.IMITATOR
                    .get(victim);
            if (imitComp.isImitatorInvulnerable()) {
                // 播放反弹音效
                victim.level().playSound(null, victim.blockPosition(),
                        io.wifi.starrailexpress.index.TMMSounds.ITEM_PSYCHO_ARMOUR,
                        net.minecraft.sounds.SoundSource.MASTER, 5.0F, 1.0F);
                if (victim instanceof net.minecraft.server.level.ServerPlayer sp) {
                    sp.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                            "message.noellesroles.imitator.boxer_blocked")
                            .withStyle(net.minecraft.ChatFormatting.GOLD, net.minecraft.ChatFormatting.BOLD), true);
                }
                return true;
            }
        }

        if (!gameWorld.isRole(victim, ModRoles.BOXER))
            return false;

        // 获取拳击手组件
        BoxerPlayerComponent boxerComponent = ModComponents.BOXER.get(victim);

        // 检查是否处于无敌状态
        if (!boxerComponent.isInvulnerable)
            return false;

        // 钢筋铁骨可以反弹任何死亡 - 不再限制死亡原因

        // 尝试找到攻击者（如果是刀或棍棒攻击）
        boolean isKnife = deathReason.equals(io.wifi.starrailexpress.game.GameConstants.DeathReasons.KNIFE);
        boolean isBat = deathReason.equals(io.wifi.starrailexpress.game.GameConstants.DeathReasons.BAT);

        if (isKnife || isBat) {
            // 需要找到攻击者 - 遍历附近玩家找到持有对应武器的
            Player attacker = RicesRoleRhapsody.findAttackerWithWeapon(victim, isKnife);

            if (attacker != null) {
                // 获取攻击者的武器
                ItemStack weapon = attacker.getMainHandItem();

                // 执行反制（对刀和棍棒有额外效果）
                boxerComponent.handleCounterAttack(attacker, weapon);
            }
        }

        // 执行通用反制（反弹任何死亡）
        boxerComponent.handleAnyDeathCounter(deathReason);

        // 无敌状态下阻止任何死亡
        return true;
    }

    /**
     * 处理跟踪者免疫
     * 盾牌只在一阶段有效，进入二阶段后消失
     *
     * @param victim      受害者
     * @param deathReason 死亡原因
     * @return true 表示成功免疫，应阻止死亡
     */
    private static boolean handleStalkerImmunity(Player victim, ResourceLocation deathReason) {
        if (victim == null || victim.level().isClientSide())
            return false;

        // 获取跟踪者组件
        StalkerPlayerComponent stalkerComp = ModComponents.STALKER.get(victim);

        // 检查是否是活跃的跟踪者且处于一阶段（盾牌只在一阶段有效）
        if (!stalkerComp.isActiveStalker())
            return false;
        if (stalkerComp.phase != 1)
            return false;

        // 检查免疫是否已使用
        if (stalkerComp.immunityUsed)
            return false;

        // 消耗免疫
        stalkerComp.immunityUsed = true;
        stalkerComp.sync();

        // 播放音效
        victim.level().playSound(null, victim.blockPosition(),
                io.wifi.starrailexpress.index.TMMSounds.ITEM_PSYCHO_ARMOUR,
                SoundSource.MASTER, 5.0F, 1.0F);

        // 发送消息
        if (victim instanceof ServerPlayer serverPlayer) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.stalker.immunity_triggered")
                            .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD),
                    true);
        }

        return true;
    }

    /**
     * 处理傀儡师死亡
     * 假人死亡时返回本体，本体死亡时真正死亡
     *
     * @param victim      受害者
     * @param deathReason 死亡原因
     * @return true 表示假人死亡（阻止真正死亡），false 表示正常处理
     */
    private static boolean handlePuppeteerDeath(Player victim, ResourceLocation deathReason) {
        if (victim == null || victim.level().isClientSide())
            return false;

        // 获取傀儡师组件
        PuppeteerPlayerComponent puppeteerComp = ModComponents.PUPPETEER.get(victim);

        // 检查是否是活跃的傀儡师
        if (!puppeteerComp.isActivePuppeteer())
            return false;

        // 检查是否正在操控假人
        if (!puppeteerComp.isControllingPuppet)
            return false;

        // 假人死亡，返回本体
        puppeteerComp.onPuppetDeath();

        return true; // 阻止真正死亡
    }

    private static boolean handleDefibrillator(Player victim) {
        DefibrillatorComponent component = ModComponents.DEFIBRILLATOR.get(victim);
        if (component.hasProtection()) {
            if (component.defibrillatorMark) {
                // 拥有标记的玩家死亡后进入医生的死亡惩罚
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
                // 无标记：保持原有位置锁定逻辑
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

    private static void handleDeathPenalty(Player victim) {
        SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(victim.level());
        DeathPenaltyComponent deathPenaltyComponent = ModComponents.DEATH_PENALTY.get(victim);
        if (deathPenaltyComponent.hasPenalty()
                && (deathPenaltyComponent.limitCameraUUID != null || deathPenaltyComponent.limitPos != null)) {
            // 已经在别的地方处理过了不给死亡限制。
            return;
        }
        boolean doctorAlive = false;
        boolean looseEndAlive = false;
        // boolean INSANE_alive = false;
        boolean CONSPIRATOR_alive = false;
        boolean limitView = false;
        var refugeeComponent = RefugeeComponent.KEY.get(victim.level());
        if (gameWorldComponent.getGameMode().identifier.equals(SREGameModes.LOOSE_ENDS_ID))
            return;
        if (refugeeComponent.isAnyRevivals) {
            looseEndAlive = true;
        }
        for (Player player : victim.level().players()) {
            if (!GameUtils.isPlayerAliveAndSurvival(player)) {
                continue;
            }
            if (gameWorldComponent.isRole(player, ModRoles.DOCTOR)) {
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
        if (looseEndAlive) {
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
        } else if (doctorAlive) {
            deathPenaltyComponent.setPenalty(45 * 20, true);
            victim.displayClientMessage(
                    Component.translatable("message.noellesroles.doctor.penalty").withStyle(ChatFormatting.RED), true);

            victim.sendSystemMessage(
                    Component.translatable("message.noellesroles.doctor.penalty").withStyle(ChatFormatting.RED));
            if (victim.hasPermissions(2)) {
                victim.sendSystemMessage(Component.translatable("message.noellesroles.admin.free_cam_hint")
                        .withStyle(ChatFormatting.YELLOW));
            }
        }
    }

    /**
     * 处理医生死亡 - 将针管和净化弹传递给另一名存活的平民
     */
    private static void handleDoctorDeath(Player victim) {
        if (victim == null || victim.level().isClientSide())
            return;

        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(victim.level());
        if (!gameWorld.isRole(victim, ModRoles.DOCTOR))
            return;

        // 查找医生背包中的针管和净化弹
        ArrayList<ItemStack> itemsToTransfer = new ArrayList<>();
        for (int i = 0; i < victim.getInventory().getContainerSize(); i++) {
            ItemStack stack = victim.getInventory().getItem(i);
            if (stack.getItem() == ModItems.ANTIDOTE) {
                itemsToTransfer.add(stack.copy());
                victim.getInventory().setItem(i, ItemStack.EMPTY);
            } else if (stack.getItem() == org.agmas.noellesroles.init.ModItems.PURIFY_BOMB) {
                itemsToTransfer.add(stack.copy());
                victim.getInventory().setItem(i, ItemStack.EMPTY);
            }
        }

        if (itemsToTransfer.isEmpty())
            return;

        // 查找另一名存活的平民
        Player targetPlayer = null;
        for (Player player : victim.level().players()) {
            if (player == victim)
                continue;
            if (!GameUtils.isPlayerAliveAndSurvival(player))
                continue;

            SRERole role = gameWorld.getRole(player);
            if (role != null && role.isInnocent()) {
                targetPlayer = player;
                break;
            }
        }

        // 如果找到存活的平民，传递物品
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

    /**
     * 处理会计死亡 - 将存折传递给另一名存活的平民
     */
    private static void handleAccountantDeath(Player victim) {
        if (victim == null || victim.level().isClientSide())
            return;

        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(victim.level());
        if (!gameWorld.isRole(victim, ModRoles.ACCOUNTANT))
            return;

        // 查找会计背包中的存折
        ArrayList<ItemStack> itemsToTransfer = new ArrayList<>();
        for (int i = 0; i < victim.getInventory().getContainerSize(); i++) {
            ItemStack stack = victim.getInventory().getItem(i);
            if (stack.getItem() == org.agmas.noellesroles.init.ModItems.PASSBOOK) {
                itemsToTransfer.add(stack.copy());
                victim.getInventory().setItem(i, ItemStack.EMPTY);
            }
        }

        if (itemsToTransfer.isEmpty())
            return;

        // 查找另一名存活的平民
        Player targetPlayer = null;
        for (Player player : victim.level().players()) {
            if (player == victim)
                continue;
            if (!GameUtils.isPlayerAliveAndSurvival(player))
                continue;

            SRERole role = gameWorld.getRole(player);
            if (role != null && role.isInnocent()) {
                targetPlayer = player;
                break;
            }
        }

        // 如果找到存活的平民，传递物品
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

    /**
     * 处理锁匠死亡 - 将巧匠钥匙和撬锁器传递给附近一名存活的平民
     */
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

    public static boolean isMJVerifyEnabled = false;
    public static List<Item> canThrowItems = new ArrayList<>();

    public static void registerEvents() {
        // 吝啬 - 商店购买返还20%金币
        OnShopPurchase.EVENT.register((player, entry, price) -> {
            org.agmas.noellesroles.role.ModifierEffects
                    .onStingyPurchase((net.minecraft.server.level.ServerPlayer) player, price);
        });

        OnKillPlayerTriggered.EVENT.register((victim, spawnBody, _killer, deathReasosn, forceKill) -> {
            final var level = victim.level();
            final var gameWorldComponent = SREGameWorldComponent.KEY.get(level);
            if (gameWorldComponent != null && gameWorldComponent.isRunning()) {
                final var inControlCCA = InControlCCA.KEY.get(victim);
                if (inControlCCA != null) {
                    inControlCCA.isControlling = false;
                    inControlCCA.sync();
                }
            }
        });

        // 肉汁独处保护机制 - 杀手/中立只能在单独相处时击杀肉汁
        AllowPlayerDeathWithKiller.EVENT.register((victim, killer, deathReason) -> {
            SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(victim.level());

            // 检查受害者是否是肉汁
            if (!gameWorld.isRole(victim, ModRoles.MEATBALL)) {
                return true;
            }

            // 检查是否是炸弹客炸弹伤害（不触发独处保护）
            if (deathReason != null && deathReason.getPath().equals("bomb_death")) {
                return true;
            }

            // 检查是否是中毒伤害（不触发独处保护）
            if (deathReason != null && deathReason.getPath().equals("poison")) {
                return true;
            }

            // 检查是否是病毒感染伤害（不触发独处保护）
            if (deathReason != null && deathReason.getPath().equals("infection")) {
                return true;
            }

            // 检查击杀者是否存在且是否为非乘客阵营
            if (killer == null || gameWorld.isInnocent(killer)) {
                return true;
            }

            // 检查附近4格范围内（y轴3格）是否有其他好人
            double safeDistanceSq = 4.0 * 4.0; // 水平4格距离平方
            double safeHeightSq = 3.0 * 3.0; // y轴3格距离平方

            for (Player nearbyPlayer : victim.level().players()) {
                if (nearbyPlayer == victim || nearbyPlayer == killer) {
                    continue;
                }
                if (!GameUtils.isPlayerAliveAndSurvival(nearbyPlayer)) {
                    continue;
                }

                // 检查是否是好人阵营
                if (gameWorld.isInnocent(nearbyPlayer)) {
                    double dx = nearbyPlayer.getX() - victim.getX();
                    double dy = nearbyPlayer.getY() - victim.getY();
                    double dz = nearbyPlayer.getZ() - victim.getZ();

                    // 检查是否在范围内（水平4格，y轴3格）
                    double horizontalDistSq = dx * dx + dz * dz;
                    if (horizontalDistSq <= safeDistanceSq && dy * dy <= safeHeightSq) {
                        // 附近有好人在保护范围内，阻止击杀
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
        THEventHandler.registerEvents();
        NinjaPlayerComponent.registerEvents();
        OnPlayerUsedSkill.EVENT.register((player) -> {
            NoellesRolesConfig config = NoellesRolesConfig.HANDLER.instance();
            if (!config.skillEchoEventEnabled) {
                return false;
            }
            SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
            if (!gameWorld.isRunning()) {
                return false;
            }
            SRERole role = gameWorld.getRole(player);
            if (role == null) {
                return false;
            }
            if (Math.random() <= 0.6)
                return false;

            // 随机延迟 3~7 秒后触发回响
            int delayTicks = (int) ((Math.random() * 4 + 3) * 20); // 3-7 秒转换为 tick (20 ticks = 1 秒)

            if (player.level() instanceof ServerLevel serverLevel) {
                GameUtils.serverAsynTaskLists.add(new ServerTaskInfoClasses.SchedulerTask(delayTicks, () -> {
                    ConfigWorldComponent.KEY.get(serverLevel).announceSkillEchoForRole(role);
                }));
            }
            return false;
        });
        // 不屈修饰符：一次性免疫被平民误杀；杀手阵营对杀手攻击免疫
        AfterShieldAllowPlayerDeathWithKiller.EVENT.register((victim, killer, deathReason) -> {
            if (victim == null || victim.level().isClientSide())
                return true;

            var worldModifiers = WorldModifierComponent.KEY.get(victim.level());
            if (worldModifiers == null)
                return true;

            if (!worldModifiers.isModifier(victim.getUUID(),
                    pro.fazeclan.river.stupid_express.constants.SEModifiers.UNYIELDING)) {
                return true;
            }

            SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(victim.level());
            var victimRole = gameWorld.getRole(victim);
            var killerRole = killer != null ? gameWorld.getRole(killer) : null;

            // 若受害者为杀手阵营，且攻击者也为杀手阵营，则免疫此杀戮（要求双方均为非中立）
            if (victimRole != null && !victimRole.isInnocent()
                    && victimRole.isCanUseKiller()) {
                if (killer != null && killer != victim && killerRole != null
                        && !killerRole.isInnocent() && killerRole.isCanUseKiller()) {
                    {
                        if (victim instanceof ServerPlayer sp) {
                            sp.displayClientMessage(Component.translatable("message.sre.unyielding.immune_killer")
                                    .withStyle(ChatFormatting.RED), true);
                            // 播放盾牌格挡音效，让附近所有人听到
                            sp.serverLevel().playSound(null, sp.blockPosition(), SoundEvents.SHIELD_BLOCK,
                                    SoundSource.MASTER,
                                    1.0F, 1.0F);
                            // 释放不灭图腾粒子效果
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
            }

            // 若受害者与击杀者均为平民（无杀手能力），则消耗一次免疫并阻止死亡
            if (victimRole != null && victimRole.isInnocent() && killer != null && killer != victim
                    && killerRole != null && killerRole.isInnocent()) {
                if (!pro.fazeclan.river.stupid_express.constants.SEModifiers.UNYIELDING_IMMUNITY_USED
                        .contains(victim.getUUID())) {
                    pro.fazeclan.river.stupid_express.constants.SEModifiers.UNYIELDING_IMMUNITY_USED
                            .add(victim.getUUID());
                    // 播放盾牌格挡音效，让附近所有人听到
                    if (victim instanceof ServerPlayer sp) {
                        sp.serverLevel().playSound(null, sp.blockPosition(), SoundEvents.SHIELD_BLOCK,
                                SoundSource.MASTER,
                                1.0F, 1.0F);
                        sp.displayClientMessage(Component.translatable("message.sre.unyielding.immune_civilian")
                                .withStyle(ChatFormatting.GREEN), true);
                        // 释放不灭图腾粒子效果
                        ServerLevel level = sp.serverLevel();
                        for (var p : level.players()) {
                            // boolean sendParticles( ServerPlayer player, ParticleOptions type, boolean
                            // longDistance, double posX, double posY, double posZ, int particleCount,
                            // double xOffset, double yOffset, double zOffset, double speed)
                            level.sendParticles(p, ParticleTypes.TOTEM_OF_UNDYING, true,
                                    sp.getX(),
                                    sp.getY(),
                                    sp.getZ(),
                                    30, 1, 1, 1, 0);
                        }
                    }
                    return false;
                }
            }

            return true;
        });

        // 其它插件/事件（比如小丑触发）放在不屈之后以保证不屈优先级
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
        MaChenXuEventHandler.register();
        VeteranKnifeHandler.register();
        GamblerHandler.register();
        StalkerPlayerComponent.registerEvents();
        org.agmas.noellesroles.game.roles.killer.delayer.DelayerPlayerComponent.registerEvents();
        SRE.cantUseChatHud.add((p) -> {
            /**
             * 这只会发生在客户端
             */
            var deathPenalty = ModComponents.DEATH_PENALTY.get(p);
            if (deathPenalty.hasPenalty()) {
                if (deathPenalty.chatEnabled == false)
                    return true;
            }
            return false;
        });
        // 观者掉枪
        AllowShootRevolverDrop.EVENT.register((player, target) -> {
            var gameWorldComponent = SREGameWorldComponent.KEY.get(player.level());
            ItemStack mainHandStack = player.getMainHandItem();
            if (!mainHandStack.is(TMMItems.DERRINGER) && gameWorldComponent.isRole(target, ModRoles.WATCHER)) {
                if (WatcherPlayerComponent.KEY.get(target).isInCalmStance())
                    return ShouldDropResult.TRUE;
            }
            return ShouldDropResult.PASS;
        });
        // 所有枪械公用冷却
        OnRevolverUsed.EVENT.register((player, target) -> {
            if (!player.isCreative()) {
                var cooldowns = player.getCooldowns();
                ItemStack mainHandStack = player.getMainHandItem();
                var items = new ArrayList<>(MCItemsUtils.getItemsByTag(player.serverLevel(), TMMItemTags.GUNS));
                // Noellesroles.LOGGER.info("itemSize:" + items.size());
                int REVOLVER_COOLDOWN = GameConstants.ITEM_COOLDOWNS.getOrDefault(TMMItems.REVOLVER, 0);
                items.remove(ModItems.FAKE_REVOLVER);
                if (mainHandStack.is(ModItems.ONCE_REVOLVER)) {
                    items.remove(ModItems.ONCE_REVOLVER);
                }
                items.remove(ModItems.PATROLLER_REVOLVER);
                if (mainHandStack.is(ModItems.PATROLLER_REVOLVER)) {
                    cooldowns.addCooldown(ModItems.PATROLLER_REVOLVER, REVOLVER_COOLDOWN / 3);
                } else {
                    cooldowns.addCooldown(ModItems.PATROLLER_REVOLVER, REVOLVER_COOLDOWN / 15);
                }
                // cooldowns.addCooldown(ModItems.PATROLLER_REVOLVER, 3 * 20);
                items.forEach((item) -> {
                    cooldowns.addCooldown(item,
                            (Integer) GameConstants.ITEM_COOLDOWNS.getOrDefault(item, REVOLVER_COOLDOWN));
                });
            }
        });
        // JOJO 两倍冷却
        OnRevolverUsed.EVENT.register((player, target) -> {
            var gameWorldComponent = SREGameWorldComponent.KEY.get(player.level());
            ItemStack mainHandStack = player.getMainHandItem();
            if (mainHandStack.is(TMMItemTags.GUNS)) {
                if (gameWorldComponent.isRole(player, ModRoles.JOJO)) {
                    player.getCooldowns().addCooldown(mainHandStack.getItem(),
                            (Integer) GameConstants.ITEM_COOLDOWNS.getOrDefault(mainHandStack.getItem(), 0) * 2);
                }
            }
        });
        // 黄油手 - 手枪冷却随机变化
        OnRevolverUsed.EVENT.register((player, target) -> {
            if (!(player instanceof ServerPlayer))
                return;
            WorldModifierComponent modifiers = WorldModifierComponent.KEY.get(player.level());
            ItemStack mainHandStack = player.getMainHandItem();
            if (mainHandStack.is(TMMItemTags.GUNS)
                    && modifiers.isModifier(player.getUUID(), TraitorAndModifiers.BUTTER_FINGERS)) {
                int roll = player.getRandom().nextInt(100);
                int baseCooldown = (Integer) GameConstants.ITEM_COOLDOWNS.getOrDefault(mainHandStack.getItem(), 400);
                int newCooldown = baseCooldown;
                if (roll < 33) {
                    // 33%: 冷却 +3秒
                    newCooldown = baseCooldown + 60;
                    player.displayClientMessage(
                            Component.translatable("modifier.noellesroles.butter_fingers.cooldown_up"), true);
                } else if (roll < 66) {
                    // 33%: 冷却 -3秒
                    newCooldown = Math.max(0, baseCooldown - 60);
                    player.displayClientMessage(
                            Component.translatable("modifier.noellesroles.butter_fingers.cooldown_down"), true);
                } else if (roll < 99) {
                    // 33%: 无事发生
                    // 不做处理
                } else {
                    // 1%: 冷却归零
                    newCooldown = 0;
                    player.displayClientMessage(Component.translatable("modifier.noellesroles.butter_fingers.reset"),
                            true);
                }
                if (newCooldown != baseCooldown) {
                    player.getCooldowns().addCooldown(mainHandStack.getItem(), newCooldown);
                }
            }
        });
        AfterShieldAllowPlayerDeath.EVENT.register((victim, deathReason) -> {
            if (victim.level() instanceof ServerLevel serverLevel) {
                org.agmas.noellesroles.game.roles.Innocent.fool.TarotAssemblyManager.clearTrackedTarget(serverLevel,
                        victim.getUUID());
            }
            return true;
        });
        AfterShieldAllowPlayerDeathWithKiller.EVENT.register((victim, killer, deathReason) -> {
            if (victim.level() instanceof ServerLevel serverLevel) {
                org.agmas.noellesroles.game.roles.Innocent.fool.TarotAssemblyManager.clearTrackedTarget(serverLevel,
                        victim.getUUID());
            }
            return true;
        });
        ShootingFrenzyPlayerComponent.registerGunNoDropEvent();
        ExecutionerPlayerComponent.registerBackfireEvent();
        ShootingFrenzyPlayerComponent.registerFrenzyCooldownEvent();
        HoanMeirinFistPunchHandler.register();
        org.agmas.noellesroles.game.roles.killer.spellbreaker.SpellbreakerPlayerComponent.registerEvents();
        // 注册警棍与防暴盾处理器
        BatonHandler.register();
        RiotShieldHandler.register();
        // 注册仁之剑处理器
        BenevolenceSwordHandler.register();
        // 布谷鸟蛋交互注册
        CuckooEggHandler.register();
        // 注册保安技能
        GuardPlayerHandler.register();
        VoodooDeathHandler.registerEvents();
        PlayerStatsBeforeRefugee.beforeLoadFunc = (player) -> {
            ModComponents.DEATH_PENALTY.get(player).init();
        };
        OnGameEnd.EVENT.register((world, gameWorldComponent) -> {
            HoanMeirinFistPunchHandler.PUNCH_RECORDS.clear();
            RoleShopHandler.resetOldmanEasterEggState();
            org.agmas.noellesroles.game.roles.killer.delayer.DelayerPlayerComponent.timeBoostTriggered = false;
            // 清除所有玩家的感染状态
            for (ServerPlayer player : world.players()) {
                InfectedPlayerComponent infectedComponent = org.agmas.noellesroles.component.ModComponents.INFECTED
                        .get(player);
                if (infectedComponent != null) {
                    infectedComponent.cure();
                }
            }
            // 清除疫使时刻状态
            org.agmas.noellesroles.game.roles.neutral.infected.InfectedWinChecker.resetAcceleratedState();
            // 清除所有建筑师的客户端墙
            for (ServerPlayer player : world.players()) {
                org.agmas.noellesroles.game.roles.Innocent.builder.BuilderPlayerComponent builderComp = org.agmas.noellesroles.component.ModComponents.BUILDER
                        .get(player);
                builderComp.clearAllWalls();
            }
            // 清除全局墙位置注册表
            org.agmas.noellesroles.game.roles.Innocent.builder.BuilderWallPositions.clearAll();
            // 已经在resetPlayer清除部分cca
            // 重置所有玩家的锁匠灵感
            SREGameRoundEndComponent roundEnd = SREGameRoundEndComponent.KEY.get(world);
            if (roundEnd.getWinStatus().equals(GameUtils.WinStatus.TIME)) {
                int alivePlayers = 0, aliveKillers = 0, aliveGhost = 0;
                var players = world.players();
                for (ServerPlayer player : players) {
                    if (GameUtils.isPlayerAliveAndSurvival(player)) {
                        alivePlayers++;
                        if (gameWorldComponent.isKillerTeam(player)) {
                            aliveKillers++;
                        }
                        if (gameWorldComponent.isRole(player, ModRoles.GHOST)) {
                            aliveGhost++;
                        }
                    }
                }
                if (aliveGhost >= 1 && aliveKillers >= 1 && aliveGhost + aliveKillers >= alivePlayers) {
                    GameUtils.serverAsynTaskLists.add(new ServerTaskInfoClasses.SchedulerTask(8 * 20, () -> {
                        players.forEach((p) -> {
                            p.playNotifySound(NRSounds.TO_BE_CONTINUED, SoundSource.MASTER, 0.5f, 1f);
                        });
                    }));
                }
            }
        });
        OnVendingMachinesBuyItems.EVENT.register((player, itemStack) -> {
            var gameWorldComponent = SREGameWorldComponent.KEY.get(player.level());
            if (itemStack.stack().is(ModItems.ONCE_REVOLVER)) {
                var role = gameWorldComponent.getRole(player);
                if (role != null) {
                    if (role.isInnocent() && role.canPickUpRevolver() && !role.isNeutrals()) {
                        return true;
                    } else if (role == SpecialGameModeRoles.DIRT) {
                        return true;
                    } else {
                        return false;
                    }
                }
            }
            return true;
        });
        UseEntityCallback.EVENT.register((player, level, interactionHand, entity, entityHitResult) -> {
            if (player.isSpectator())
                return InteractionResult.PASS;
            var gameC = SREGameWorldComponent.KEY.get(level);
            if (!gameC.isRole(player, TMMRoles.VIGILANTE))
                return InteractionResult.PASS;
            if (HandCuffsItem.hasHandCuff(player)) {
                return InteractionResult.PASS;
            }
            if (entity instanceof Player target) {
                if (HandCuffsItem.hasHandCuff(target)) {
                    if (!player.getMainHandItem().isEmpty())
                        return InteractionResult.PASS;
                    var fkit = HandCuffsItem.putOffHandCuff(target);
                    if (fkit == null)
                        return InteractionResult.FAIL;
                    RoleUtils.insertStackInFreeSlot(player, fkit.copy());
                    player.displayClientMessage(
                            Component.translatable("item.noellesroles.handcuffs.put_off", target.getName())
                                    .withStyle(ChatFormatting.GREEN),
                            true);
                    target.displayClientMessage(Component
                            .translatable("item.noellesroles.handcuffs.reciever_put_off", player.getName())
                            .withStyle(ChatFormatting.GREEN), true);
                    return InteractionResult.SUCCESS;
                }
            }
            return InteractionResult.PASS;
        });
        UseItemCallback.EVENT.register((player, world, hand) -> {
            ItemStack stack = player.getItemInHand(hand);
            if (!RoleShopHandler.isOldmanEasterEggRod(stack)) {
                return InteractionResultHolder.pass(stack);
            }
            if (RoleShopHandler.hasUsedOldmanEasterEggRod(stack)) {
                return InteractionResultHolder.pass(stack);
            }
            if (world.isClientSide()) {
                return InteractionResultHolder.success(stack);
            }

            var pig = EntityType.PIG.create(world);
            if (pig == null) {
                return InteractionResultHolder.fail(stack);
            }
            pig.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0f);
            if (pig instanceof Saddleable saddleable) {
                saddleable.equipSaddle(ItemStack.EMPTY, null);
            }
            var pigStepHeight = pig.getAttribute(Attributes.STEP_HEIGHT);
            if (pigStepHeight != null) {
                pigStepHeight.setBaseValue(0.5D);
            }
            var pigJumpStrength = pig.getAttribute(Attributes.JUMP_STRENGTH);
            if (pigJumpStrength != null) {
                pigJumpStrength.setBaseValue(0.0D);
            }
            pig.addTag(RoleShopHandler.OLDMAN_EASTER_EGG_PIG_NO_STEP_TAG);
            world.addFreshEntity(pig);
            RoleShopHandler.markOldmanEasterEggRodUsed(stack);
            return InteractionResultHolder.success(stack);
        });
        SRE.canDrop.add((player) -> {
            var mainHandItem = player.getMainHandItem();
            var gameWorldComponent = SREGameWorldComponent.KEY.get(player.level());
            if (gameWorldComponent.isRole(player, RedHouseRoles.BAKA)) {
                if (mainHandItem.is(FunnyItems.PROBLEM_SET)) {
                    return true;
                }
            }
            if (gameWorldComponent.isRole(player, ModRoles.CHEF)) {
                if (mainHandItem.get(ModDataComponentTypes.COOKED) != null) {
                    return true;
                }
            }
            if (mainHandItem.is(ModItems.RADIO)) {
                return true;
            }
            if (RoleShopHandler.isOldmanEasterEggRod(mainHandItem)) {
                return true;
            }
            return false;
        });

        ServerLifecycleEvents.SERVER_STARTED.register((server) -> {
            if (isMJVerifyEnabled) {
                Harpymodloader.isMojangVerify = Noellesroles.checkMJVerify();
            } else {
                Harpymodloader.isMojangVerify = true;
            }
        });
        CommanderHandler.registerChatEvent();
        InsaneKillerPlayerComponent.registerEvent();
        ConspiratorKilledPlayer.registerEvents();
        // 注册疫使胜利检测和加速检测
        InfectedWinChecker.registerEvent();
        EntityClearUtils.registerResetEvent();
        SRE.cantSendReplay.add(player -> {
            DeathPenaltyComponent component = ModComponents.DEATH_PENALTY.get(player);
            if (component != null) {
                if (component.hasPenalty())
                    return true;
            }
            return false;
        });
        SRE.canStickArmor.add((deathInfo -> {
            String deathReasonPath = deathInfo.deathReason().getPath();
            if (deathReasonPath.equals("ignited")) {
                // 纵火犯
                return true;
            }
            if (deathReasonPath.equals("hoan_meirin_lonely")) {
                // 红美铃孤独
                return true;
            }
            if (deathReasonPath.equals("voodoo")) {
                // 巫毒
                return true;
            }
            if (deathReasonPath.equals("shot_innocent")) {
                // 误杀平民
                return true;
            }
            return false;
        }));
        MapScanner.registerMapScanEvent();
        CustomWinnerClass.registerCustomWinners();
        XiaoNaoHandler.registerEvent();
        OnPlayerDeathWithKiller.EVENT.register((victim, killer, deathReason) -> {
            SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(victim.level());
            if (gameWorld == null || !gameWorld.isRunning())
                return;
            for (Player player : victim.level().players()) {
                // 排除受害者自己（虽然巡警死了也不能触发能力，但以防万一）
                if (player.getUUID().equals(victim.getUUID()))
                    continue;
                // 检查是否是巡警
                if (!gameWorld.isRole(player, ModRoles.PATROLLER))
                    continue;

                // 检查是否存活

                if (!GameUtils.isPlayerAliveAndSurvival(player))
                    continue;
                // 检查距离（50格内）
                if (player.distanceToSqr(victim) > 50 * 50
                        || !PatrollerPlayerComponent.isBoundTargetVisible(victim, player))
                    continue;

                PatrollerPlayerComponent patrollerComponent = ModComponents.PATROLLER.get(player);
                patrollerComponent.onNearbyDeath();
            }
        });
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
                world.players().forEach(
                        player -> {
                            if (player instanceof ServerPlayer sp) {
                                player.playNotifySound(SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 0.5F, 1.3F);

                                org.agmas.noellesroles.packet.BroadcastMessageS2CPacket packet = new org.agmas.noellesroles.packet.BroadcastMessageS2CPacket(
                                        msg);
                                net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(sp, packet);
                            }
                        });
            }
        });
        OnPlayerDeathWithKiller.EVENT.register((victim, killer, deathReason) -> {
            SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(victim.level());
            if (gameWorld == null)
                return;
            var refugeeC = RefugeeComponent.KEY.get(victim.level());
            boolean isRefugeeAlive = false;
            if (refugeeC.isAnyRevivals) {
                isRefugeeAlive = true;
            }
            if (isRefugeeAlive)
                return;
            // 遍历所有玩家，检查是否有复仇者绑定了这个受害者
            for (Player player : victim.level().players()) {
                if (!gameWorld.isRole(player, ModRoles.AVENGER))
                    continue;
                if (player.equals(victim))
                    continue; // 复仇者自己死亡不触发

                AvengerPlayerComponent avengerComponent = ModComponents.AVENGER.get(player);

                // 检查这个复仇者是否绑定了受害者
                if (avengerComponent.targetPlayer != null &&
                        avengerComponent.targetPlayer.equals(victim.getUUID()) &&
                        !avengerComponent.activated) {

                    // 激活复仇者能力，传入凶手信息
                    if (killer != null) {
                        avengerComponent.activate(killer.getUUID());
                        avengerComponent.targetName = killer.getName().getString();
                    } else {
                        avengerComponent.activate(null);
                    }

                    String playerName = player.getName().getString();
                    String victimName = victim.getName().getString();
                    String killerName = killer != null ? killer.getName().getString() : "未知";

                    player.displayClientMessage(
                            Component.translatable("message.avenger.target_died", victimName, killerName)
                                    .withStyle(ChatFormatting.GOLD),
                            true);
                    Noellesroles.LOGGER.info("复仇者 {} 绑定的目标 {} 被 {} 杀死，激活复仇者能力", playerName, victimName, killerName);
                }
            }
        });
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
        // 影隼死亡处理 - 为存活杀手提供喷气背包
        OnPlayerDeathWithKiller.EVENT.register((victim, killer, deathReason) -> {
            ShadowFalconPlayerComponent.onDeathGiveJetpacks(victim);
        });
        // 葬仪被动-引渡：杀手/杀手方中立/魔术师死亡时向所有杀手、杀手方中立和魔术师广播
        // 葬仪死亡后被动失效（场上没有存活的葬仪时不会触发广播）
        OnPlayerDeathWithKiller.EVENT.register((victim, killer, deathReason) -> {
            var gameWorldComponent = SREGameWorldComponent.KEY.get(victim.level());
            if (gameWorldComponent == null || !gameWorldComponent.isRunning())
                return;
            // 场上没有存活的葬仪时，被动失效
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
            // 检查死亡玩家是否是杀手阵营、杀手方中立或魔术师
            var victimRole = gameWorldComponent.getRole(victim);
            if (victimRole == null || !gameWorldComponent.isKillerTeamRole(victimRole))
                return;
            // 获取死亡玩家的翻译职业名
            String rolePath = victimRole.identifier().getPath();
            Component roleName = Component.translatable("announcement.star.role." + rolePath);
            Component deathMessage = Component
                    .translatable("message.noellesroles.mortician_bodymaker.passive_death", roleName)
                    .withStyle(ChatFormatting.GOLD);
            // 向所有杀手、杀手方中立和魔术师广播
            for (Player player : victim.level().players()) {
                if (!GameUtils.isPlayerAliveAndSurvival(player))
                    continue;
                var targetRole = gameWorldComponent.getRole(player);
                if (targetRole == null)
                    continue;
                // 杀手 (canUseKiller) 或 杀手方中立 (isNeutralForKiller) 或 魔术师
                if (!targetRole.canUseKiller() && !targetRole.isNeutralForKiller()
                        && !gameWorldComponent.isRole(player, ModRoles.MAGICIAN))
                    continue;
                if (player instanceof ServerPlayer sp) {
                    org.agmas.noellesroles.commands.BroadcastCommand.BroadcastMessage(sp, deathMessage);
                }
            }
        });
        OnPlayerKilledPlayerIdentifier.EVENT.register((victim, killer, deathReason) -> {
            var gameWorldComponent = SREGameWorldComponent.KEY.get(victim.level());
            if (gameWorldComponent.isRole(killer, ModRoles.MERCENARY)) {
                var mercenary = MercenaryPlayerComponent.KEY.get(killer);
                if (mercenary != null && mercenary.isContractTarget(victim)) {
                    mercenary.onContractTargetKilled();
                }
            }
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
            // 强盗的金钱盗取逻辑
            if (gameWorldComponent.isRole(killer, ModRoles.BANDIT)) {
                var banditComponent = ModComponents.BANDIT.get(killer);
                if (banditComponent != null) {
                    banditComponent.handleKilledVictim(victim);
                }
            }

            // 小偷的击杀奖励逻辑
            if (gameWorldComponent.isRole(killer, ModRoles.THIEF)) {
                var thiefComponent = org.agmas.noellesroles.game.roles.neutral.thief.ThiefPlayerComponent.KEY
                        .get(killer);
                if (thiefComponent != null) {
                    thiefComponent.handleKilledVictim(victim);
                }
            }

            if (deathReason.getPath().equals(GameConstants.DeathReasons.KNIFE.getPath())) {
                killer.addEffect(new MobEffectInstance(
                        MobEffects.MOVEMENT_SPEED, // ID
                        1, // 持续时间（tick）
                        1, // 等级（0 = 速度 I）
                        false, // ambient（环境效果，如信标）
                        false, // showParticles（显示粒子）
                        false // showIcon（显示图标）
                ));
            }
            for (var p : victim.level().players()) {
                if (gameWorldComponent.isRole(p, ModRoles.MONITOR)) {
                    if (p.getCooldowns().isOnCooldown(Items.BARRIER)) {
                        continue;
                    } else {
                        p.getCooldowns().addCooldown(Items.BARRIER, 60 * 20);
                        p.displayClientMessage(
                                Component.translatable("message.monitor.killer_killed", victim.getName())
                                        .withStyle(ChatFormatting.AQUA),
                                true);
                    }
                }
            }
        });
        ShouldDropOnDeath.EVENT.register(((stack) -> {
            final var key = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            if ("exposure:album".equals(key) || "exposure:photograph".equals(key)
                    || "exposure:stacked_photographs".equals(key) || stack.is(ModItems.PATROLLER_REVOLVER)) {
                return true;
            }
            if (RoleShopHandler.isOldmanEasterEggRod(stack)) {
                return true;
            }
            if (stack.is(ModItems.MASTER_KEY) ||
                    stack.is(Items.WRITABLE_BOOK) ||
                    stack.is(Items.WRITTEN_BOOK)) {
                return true;
            }
            return false;
        }));

        OnShieldBroken.EVENT.register((victim, killer) -> {
            var gameWorldComponent = SREGameWorldComponent.KEY.get(victim.level());
            if (gameWorldComponent.isRole(victim, ModRoles.WATCHER)) {
                WatcherPlayerComponent.KEY.get(victim).markShieldConsumed();
            }
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
            // 影隼临时护盾破碎处理
            if (gameWorldComponent.isRole(victim, ModRoles.SHADOW_FALCON)) {
                ShadowFalconPlayerComponent shadowFalconComponent = ShadowFalconPlayerComponent.KEY.get(victim);
                shadowFalconComponent.onShieldBroken();
            }
        });

        WayfarerPlayerComponent.registerEvents();
        OnPlayerDeath.EVENT.register((playerEntity, reason) -> {
            ServerPlayNetworking.send((ServerPlayer) playerEntity, new CloseUiPayload());
            FortunetellerPlayerComponent.KEY.get(playerEntity).init();
            SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(playerEntity.level());
            if (!RefugeeComponent.KEY.get(playerEntity.level()).isAnyRevivals) {
                PuppeteerPlayerComponent.KEY.get(playerEntity).clear();
                if (gameWorldComponent.isRole(playerEntity,
                        ModRoles.INSANE_KILLER)) {
                    final var insaneKillerPlayerComponent = InsaneKillerPlayerComponent.KEY.get(playerEntity);
                    insaneKillerPlayerComponent.init();
                }
            }
            RoleUtils.RemoveAllEffects(playerEntity);
            if (gameWorldComponent.isRole(playerEntity, ModRoles.JOJO)) {
                int dropCount = 1 + MCItemsUtils.countItem(playerEntity, TMMItemTags.GUNS);
                while (dropCount > 0) {
                    playerEntity.drop(TMMItems.REVOLVER.getDefaultInstance(), false);
                    dropCount--;
                }
            }
            if (gameWorldComponent.isRole(playerEntity, ModRoles.ELF)) {
                int bowcount = SREItemUtils.clearItem(playerEntity, Items.BOW);
                int crossbowcount = SREItemUtils.clearItem(playerEntity, Items.CROSSBOW);
                int dropCount = bowcount + crossbowcount;
                while (dropCount > 0) {
                    playerEntity.drop(TMMItems.REVOLVER.getDefaultInstance(), false);
                    dropCount--;
                }
            }

            if (gameWorldComponent.isRole(playerEntity, ModRoles.MARTIAL_ARTS_INSTRUCTOR)) {
                int nunchuckCount = SREItemUtils.clearItem(playerEntity, TMMItems.NUNCHUCK);
                while (nunchuckCount > 0) {
                    playerEntity.drop(TMMItems.REVOLVER.getDefaultInstance(), false);
                    nunchuckCount--;
                }
            }
            if (gameWorldComponent.isRole(playerEntity, ModRoles.GUARD)) {
                int batonCount = SREItemUtils.clearItem(playerEntity, org.agmas.noellesroles.init.ModItems.BATON);
                while (batonCount > 0) {
                    playerEntity.drop(TMMItems.REVOLVER.getDefaultInstance(), false);
                    batonCount--;
                }
            }
            if (gameWorldComponent.isRole(playerEntity, ModRoles.SEA_KING)) {
                if (playerEntity.level() instanceof ServerLevel level) {
                    for (var e : level.getAllEntities()) {
                        if (e instanceof ThrownTrident te)
                            if (te.getOwner().getUUID().equals(playerEntity.getUUID())) {
                                playerEntity.drop(TMMItems.REVOLVER.getDefaultInstance(), false);
                                te.discard();
                            }
                    }
                }
            }
            {
                int tridentCount = SREItemUtils.clearItem(playerEntity, net.minecraft.world.item.Items.TRIDENT);
                while (tridentCount > 0) {
                    playerEntity.drop(TMMItems.REVOLVER.getDefaultInstance(), false);
                    tridentCount--;
                }
            }

            if (gameWorldComponent.isRole(playerEntity, ModRoles.WATER_GHOST)) {
                int tridentCount = SREItemUtils.clearItem(playerEntity, net.minecraft.world.item.Items.TRIDENT);
                while (tridentCount > 0) {
                    playerEntity.drop(TMMItems.REVOLVER.getDefaultInstance(), false);
                    tridentCount--;
                }
            }

            if (gameWorldComponent.isRole(playerEntity, ModRoles.SWAST)) {
                int sniperRifleCount = SREItemUtils.clearItem(playerEntity, TMMItems.SNIPER_RIFLE);
                while (sniperRifleCount > 0) {
                    playerEntity.drop(TMMItems.REVOLVER.getDefaultInstance(), false);
                    sniperRifleCount--;
                }
            }

            if (gameWorldComponent.isRole(playerEntity, ModRoles.BETTER_VIGILANTE)) {
                final var betterVigilantePlayerComponent = BetterVigilantePlayerComponent.KEY.get(playerEntity);
                betterVigilantePlayerComponent.init();
            }
        });
        AfterShieldAllowPlayerDeathWithKiller.EVENT.register((player, killer, deathReason) -> {
            SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(player.level());
            if (gameWorldComponent.isRole(player, ModRoles.SUPERSTAR)) {
                return true;
            }
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
                            GameUtils.killPlayer(p, true, killer, deathReason);
                            MCItemsUtils.clearItem(player, ModItems.LIFE_AND_DEATH_SHAPE, 1);
                            return false;
                        }
                    }
                }
            }
            return true;
        });
        AllowPlayerDeath.EVENT.register((player, deathReason) -> {
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
        AllowPlayerDeath.EVENT.register(((playerEntity, identifier) -> {
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
            return true;
        }));
        CanSeePoison.EVENT.register((player) -> {
            SREGameWorldComponent gameWorldComponent = (SREGameWorldComponent) SREGameWorldComponent.KEY
                    .get(player.level());
            if (gameWorldComponent.isRole((Player) player, ModRoles.BARTENDER)) {
                return true;
            }
            if (gameWorldComponent.isRole((Player) player, ModRoles.POISONER)) {
                return true;
            }
            return false;
        });
        AwesomePlayerComponent.registerEvents();
        TrueKillerFinder.registerEvents();
        ModdedRoleRemoved.EVENT.register((player, role) -> {
            if (role != null) {
                if (role.identifier()
                        .equals(ModRoles.INSANE_KILLER
                                .identifier())) {
                    InsaneKillerPlayerComponent.KEY.get(player).clear();
                }

            }
        });
        ModRolesInitialEventRegister.register();
        ServerTickEvents.END_SERVER_TICK.register(((server) -> {
            // 更新烟雾区域和迷幻区域
            ServerSmokeAreaManager.tick();
            HallucinationAreaManager.tick();
            ServerLevel level = server.overworld();
            {
                org.agmas.noellesroles.game.roles.Innocent.fool.TarotAssemblyManager.serverLevelTick(level);
            }
            {
                if (server.getTickCount() % 10 == 0) {
                    HashSet<UUID> toDeleted = new HashSet<>();
                    for (var p_u : RadioItem.RADIO_GROUP) {
                        ServerPlayer p = server.getPlayerList().getPlayer(p_u);
                        if (p == null) {
                            toDeleted.add(p_u);
                        } else {
                            if (p.isSpectator()) {
                                toDeleted.add(p_u);
                                p.displayClientMessage(Component.translatable("message.noellesroles.radio.left")
                                        .withStyle(ChatFormatting.RED), true);
                            } else if (!MCItemsUtils.hasItem(p, ModItems.RADIO)) {
                                toDeleted.add(p_u);
                                p.displayClientMessage(Component.translatable("message.noellesroles.radio.left")
                                        .withStyle(ChatFormatting.RED), true);
                            }

                        }
                    }
                    RadioItem.RADIO_GROUP.removeAll(toDeleted);
                }

            }
        }));
        ServerTickEvents.START_SERVER_TICK.register(((server) -> {
            if (TimeStopEffect.freezeTime > 0) {
                TimeStopEffect.freezeTime--;
                if (TimeStopEffect.freezeTime == 0) {
                    server.getPlayerList().getPlayers().forEach((player) -> {
                        if (TimeStopEffect.canMovePlayers.contains(player.getUUID())) {
                            player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 5, 0, false, false, false));
                        }
                        ServerPlayNetworking.send(player, new RemoveStatusBarPayload("Time_Stop"));
                    });
                    server.tickRateManager().setFrozen(false);
                }
            }
        }));
        // // 监听角色分配事件 - 这是最重要的事件！
        // // 当玩家被分配角色时触发，可以在这里给予初始物品、设置初始状态等
        // ModdedRoleAssigned.EVENT.register((player, role) -> {
        //
        // });
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, serverPlayer, bound) -> {
            if (!WorldModifierComponent.KEY.get(serverPlayer.level()).isModifier(serverPlayer,
                    SEModifiers.SPLIT_PERSONALITY))
                return true;
            var spc = SplitPersonalityComponent.KEY.get(serverPlayer);
            if (!spc.isDeath()) {
                ServerPlayer mainP = serverPlayer.server.getPlayerList().getPlayer(spc.getMainPersonality());
                ServerPlayer secondP = serverPlayer.server.getPlayerList().getPlayer(spc.getSecondPersonality());
                if (mainP == null || secondP == null)
                    return true;
                var broadcastMessage = Component
                        .translatable("message.split_personality.broadcast_prefix",
                                Component.literal("").append(serverPlayer.getDisplayName())
                                        .withStyle(ChatFormatting.AQUA),
                                Component.literal(message.signedContent()).withStyle(ChatFormatting.WHITE))
                        .withStyle(ChatFormatting.GOLD);
                if (serverPlayer.isSpectator()) {
                    BroadcastCommand
                            .BroadcastMessage(mainP,
                                    broadcastMessage);
                    BroadcastCommand.BroadcastMessage(secondP, broadcastMessage);
                } else {
                    BroadcastCommand
                            .BroadcastMessage(mainP,
                                    broadcastMessage);
                    BroadcastCommand.BroadcastMessage(secondP, broadcastMessage);
                }
            }
            return true;
        });
        // 禁止聊天药水效果：拥有CHAT_BAN效果的玩家发送的聊天消息不被任何人接收
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, serverPlayer, bound) -> {
            if (serverPlayer.hasEffect(ModEffects.CHAT_BAN)) {
                return false;
            }
            return true;
        });
        // 可以改玩家职业
        // OnGamePlayerRolesConfirm.EVENT.register((serverLevel, roleAssignments) -> {
        // String currentMap = "unknown";
        // if (serverLevel.getServer() != null) {
        // var areas =
        // io.wifi.starrailexpress.cca.AreasWorldComponent.KEY.get(serverLevel);
        // if (areas != null && areas.mapName != null) {
        // currentMap = areas.mapName;
        // }
        // }
        // });

        OnGameTrueStarted.EVENT.register((serverLevel) -> {
            TarotAssemblyManager.havingMeeting = false;
            HoanMeirinFistPunchHandler.PUNCH_RECORDS.clear();
            RoleShopHandler.resetOldmanEasterEggState();
            // 清除所有玩家的感染状态
            for (ServerPlayer player : serverLevel.players()) {
                InfectedPlayerComponent infectedComponent = org.agmas.noellesroles.component.ModComponents.INFECTED
                        .get(player);
                if (infectedComponent != null) {
                    infectedComponent.cure();
                }
            }
            // 重置疫使时刻状态
            org.agmas.noellesroles.game.roles.neutral.infected.InfectedWinChecker.resetAcceleratedState();
            SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(serverLevel);
            WorldModifierComponent worldModifierComponent = WorldModifierComponent.KEY.get(serverLevel);
            boolean hasDio = false;
            boolean hasRecorder = false;
            boolean hasCandlebearer = false;
            // 年兽除岁效果：给所有玩家分发4个鞭炮
            boolean hasNianShou = false;
            boolean hasArsonist = false;
            boolean hasCuckoo = false;
            final var all_players = serverLevel.players();
            for (var p : all_players) {
                if (!gameWorldComponent.isJumpAvailable() && GameUtils.isPlayerAliveAndSurvivalIgnoreShitSplit(p)) {
                    // NO JUMPING! For everyone who hasn't permissions
                    if (!p.hasPermissions(2)) {
                        p.getAttribute(Attributes.JUMP_STRENGTH).addOrReplacePermanentModifier(noJumpingAttribute);
                    }
                }

                if (gameWorldComponent.isRole(p, ModRoles.THIEF)) {
                    ThiefPlayerComponent.KEY.get(p).updateHonorCost(serverLevel.players().size());
                } else if (gameWorldComponent.isRole(p, ModRoles.ATTENDANT)) {
                    SRE.SendRoomInfoToPlayer(p);
                    // 发送房间信息
                } else if (gameWorldComponent.isRole(p, ModRoles.DIO)) {
                    hasDio = true;
                } else if (gameWorldComponent.isRole(p, ModRoles.RECORDER)) {
                    hasRecorder = true;
                } else if (gameWorldComponent.isRole(p, ModRoles.CANDLE_BEARER)) {
                    hasCandlebearer = true;
                } else if (gameWorldComponent.isRole(p, ModRoles.NIAN_SHOU)) {
                    hasNianShou = true;
                } else if (gameWorldComponent.isRole(p, SERoles.ARSONIST)) {
                    hasArsonist = true;
                } else if (gameWorldComponent.isRole(p, ModRoles.CUCKOO)) {
                    hasCuckoo = true;
                }
                if (worldModifierComponent.isModifier(p, NRModifiers.EXPEDITION)) {
                    SRERole role = gameWorldComponent.getRole(p);
                    var expeditionComponent = ExpeditionComponent.KEY.get(p);
                    if (expeditionComponent != null && expeditionComponent.isExpedition()) {
                        // 检查新角色是否是好人阵营
                        // 如果不是好人阵营（是杀手或中立），则清除远征队组件
                        if (role != null && (!role.isInnocent() || role.canUseKiller() || role.isNeutrals())) {
                            // 清除远征队组件
                            expeditionComponent.clear();
                            expeditionComponent.sync();

                            // 注意：由于 Harpymodloader 的修饰符系统限制，我们只能清除组件功能
                            // 修饰符本身仍然保留在系统中，但不会生效
                            // 这是为了防止某些角色（如赌徒、慕恋者）变成杀手后仍保留远征队能力
                            worldModifierComponent.removeModifier(p.getUUID(), NRModifiers.EXPEDITION);
                            Noellesroles.LOGGER
                                    .info("Expedition modifier effect disabled for player due to role change: "
                                            + p.getName().getString() + ", new role: " + role.identifier());
                        }
                    }
                }
            }
            if (hasDio) {
                GameUtils.serverAsynTaskLists.add(new ServerTaskInfoClasses.SchedulerTask(20 * 8, () -> {
                    all_players.forEach((p) -> {
                        if (p != null) {
                            p.playNotifySound(NRSounds.DIO_SPAWN, SoundSource.PLAYERS, 0.5F, 1.0F);
                        }
                    });
                }));
            }
            if (hasRecorder) {
                all_players.forEach((p) -> {
                    if (p != null) {
                        BroadcastCommand.BroadcastMessage(p, Component
                                .translatable("message.noellesroles.recorder.entry").withStyle(ChatFormatting.YELLOW));
                    }
                });
            }
            if (hasCandlebearer) {
                all_players.forEach((p) -> {
                    if (p != null) {
                        BroadcastCommand.BroadcastMessage(p, Component
                                .translatable("message.noellesroles.candlebearer.entry")
                                .withStyle(ChatFormatting.YELLOW));
                    }
                });
            }
            if (hasArsonist) {
                all_players.forEach((p) -> {
                    if (p != null) {
                        BroadcastCommand.BroadcastMessage(p, Component
                                .translatable("message.noellesroles.arsonist.entry").withStyle(ChatFormatting.YELLOW));
                    }
                });
            }
            if (hasCuckoo) {
                all_players.forEach((p) -> {
                    if (p != null) {
                        BroadcastCommand.BroadcastMessage(p, Component
                                .translatable("message.noellesroles.cuckoo.entry").withStyle(ChatFormatting.YELLOW));
                    }
                });
            }
            if (hasNianShou) {
                for (var player : all_players) {
                    // 给每个玩家4个鞭炮
                    ItemStack firecrackerStack = new ItemStack(TMMItems.FIRECRACKER);
                    firecrackerStack.set(DataComponents.MAX_STACK_SIZE, 4);
                    firecrackerStack.setCount(4);
                    player.getInventory().add(firecrackerStack);

                    // 发送提示消息
                    BroadcastCommand.BroadcastMessage(player, Component
                            .translatable("message.noellesroles.nianshou.firecrackers_distributed")
                            .withStyle(net.minecraft.ChatFormatting.GOLD));
                }
            }
        });
        // 监听玩家死亡事件 - 用于激活复仇者能力、拳击手反制、跟踪者免疫和操纵师死亡判定
        AllowPlayerDeath.EVENT.register((victim, deathReason) -> {
            // 检查拳击手无敌反制
            if (handleBoxerInvulnerability(victim, deathReason)) {
                return false; // 阻止死亡
            }

            // 检查跟踪者免疫
            if (handleStalkerImmunity(victim, deathReason)) {
                return false; // 阻止死亡
            }

            // onPlayerDeath(victim, deathReason);
            return true; // 允许死亡
        });
        AfterShieldAllowPlayerDeath.EVENT.register((victim, deathReason) -> {

            // 检查傀儡师假人状态
            if (handlePuppeteerDeath(victim, deathReason)) {
                return false; // 阻止死亡（假人死亡）
            }

            // 检查起搏器
            if (handleDefibrillator(victim)) {
                // 允许死亡，但已标记复活
            }
            return true; // 允许死亡
        });
        OnPlayerDeath.EVENT.register((victim, deathReason) -> {
            // 检查医生死亡 - 传递针管
            handleDoctorDeath(victim);

            // 检查锁匠死亡 - 传递巧匠钥匙和撬锁器
            handleLocksmithDeath(victim);

            // 检查会计死亡 - 传递存折
            handleAccountantDeath(victim);

            // 检查死亡惩罚
            handleDeathPenalty(victim);

            // 检查故障机器人 - 死亡时生成缓慢效果云
            handleGlitchRobotDeath(victim);
        });

        // 服务器Tick事件 - 老人的猪的处理
        // 复活已经移动到 DefibrillatorComponent 中
        // 锁匠已经移动到 LocksmithInspirationComponent 中
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            var gameWorldComponent = SREGameWorldComponent.KEY.maybeGet(server.overworld()).orElse(null);
            if (gameWorldComponent == null || !gameWorldComponent.isRunning()) {
                return;
            }
            {
                ServerLevel level = server.overworld();
                List<? extends Pig> pigs = level.getEntities(EntityTypeTest.forExactClass(Pig.class),
                        (pig) -> pig.getTags().contains(RoleShopHandler.OLDMAN_EASTER_EGG_PIG_NO_STEP_TAG));
                for (Pig pig : pigs) {
                    if (pig.getControllingPassenger() == null) {
                        oldmanPigRidePositions.remove(pig.getUUID());
                        continue;
                    }
                    pig.setJumping(false);
                }
            }
        });

        // 服务器Tick事件 - 喷气背包效果处理
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                org.agmas.noellesroles.content.item.JetpackItem.tickJetpackEffect(player);
            }
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            sender.sendPacket(new BloodConfigS2CPacket(NoellesRolesConfig.HANDLER.instance().enableClientBlood));
            final ServerPlayer p = handler.player;
            SREPlayerStatsComponent.KEY.get(p).joinLoadFromFile();
        });
    }

    public static void registerPredicate() {
        OnPlayerDeath.EVENT.register((victim, deathReason) -> {
            SREItemUtils.clearItem(victim, ModItems.BOMB);
            var gameWorldComponent = SREGameWorldComponent.KEY.get(victim.level());
            if (victim.getVehicle() instanceof WheelchairEntity we) {
                if (gameWorldComponent.isRole(victim, ModRoles.OLDMAN)) {
                    we.discard();
                }
                victim.stopRiding();
            }
        });
        // 设置谓词
        SRE.canUseChatHud.add((role -> role.getIdentifier()
                .equals(ModRoles.INSANE_KILLER_ID)));
        SRE.canUseChatHudPlayer.add(player -> {
            return SREClient.gameComponent != null && SREClient.gameComponent.isRunning()
                    && SREClient.gameComponent.getGameMode() instanceof ChairWheelRaceGame;
        });
        SRE.canUseOtherPerson.add((role -> role.getIdentifier()
                .equals(TMMRoles.DISCOVERY_CIVILIAN.getIdentifier())));
        SRE.canUseOtherPerson.add((role -> role.getIdentifier()
                .equals(ModRoles.INSANE_KILLER_ID)));
        SRE.canUseOtherPerson.add((role -> role.getIdentifier()
                .equals(ModRoles.MONOKUMA_ID)));
        SRE.canUseOtherPerson.add((role -> role.getIdentifier()
                .equals(ModRoles.MANIPULATOR_ID)));
        SRE.canCollide.add(a -> {
            final var gameWorldComponent = SREGameWorldComponent.KEY.get(a.level());
            if (gameWorldComponent.isRole(a,
                    ModRoles.INSANE_KILLER)) {
                if (InsaneKillerPlayerComponent.KEY.get(a).isActive) {
                    return true;
                }
            }
            return false;
        });
        SRE.canCollide.add(a -> {
            if (a.hasEffect(MobEffects.INVISIBILITY) || a.hasEffect(ModEffects.SAFE_TIME)
                    || a.hasEffect(ModEffects.NO_COLLIDE)) {
                return true;
            }
            return false;
        });
        SRE.cantPushableBy.add(entity -> {
            if (entity instanceof PuppeteerBodyEntity) {
                return true;
            }
            return false;
        });
        SRE.cantPushableBy.add(entity -> {
            if (entity instanceof Player serverPlayer) {
                if (serverPlayer.hasEffect(MobEffects.INVISIBILITY)
                        || serverPlayer.hasEffect(ModEffects.SAFE_TIME)
                        || serverPlayer.hasEffect(ModEffects.NO_COLLIDE)) {
                    return true;
                } else {
                    var modifiers = WorldModifierComponent.KEY.get(serverPlayer.level());
                    if (modifiers.isModifier(serverPlayer.getUUID(), SEModifiers.FEATHER)) {
                        return true;
                    }
                    var gameComp = SREGameWorldComponent.KEY.get(serverPlayer.level());
                    if (gameComp != null) {
                        if (gameComp.isRole(serverPlayer,
                                ModRoles.INSANE_KILLER)) {
                            InsaneKillerPlayerComponent insaneKillerPlayerComponent = InsaneKillerPlayerComponent.KEY
                                    .get(serverPlayer);
                            if (insaneKillerPlayerComponent.isActive) {
                                return true;
                            }
                        }
                    }

                }
            }
            return false;
        });
        SRE.canCollideEntity.add(entity -> {
            return entity instanceof PuppeteerBodyEntity;
        });
        SRE.cantPushableBy.add(entity -> {
            return (entity instanceof NoteEntity);
        });
        SRE.canDropItem.addAll(List.of(
                "exposure:stacked_photographs",
                "exposure:album",
                "exposure:photograph",
                "noellesroles:mint_candies",
                "noellesroles:alchemist_buff_potion",
                "noellesroles:stalker_knife",
                "noellesroles:yinyang_sword",
                "noellesroles:stalker_knife_offhand",
                "noellesroles:pill",
                "noellesroles:pocket_watch",
                "noellesroles:throwing_knife",
                "starrailexpress:dnf_suspicious_meat",
                "starrailexpress:dnf_paper_scrap",
                "supplementaries:key",
                "minecraft:emerald",
                "minecraft:glass_bottle",
                "starrailexpress:dnf_cornmeal_bag",
                "starrailexpress:dnf_toxic_heart",
                "starrailexpress:dnf_redemption_potion",
                "starrailexpress:dnf_redemption_formula",
                "starrailexpress:dnf_water_bottle",
                "starrailexpress:dnf_flour_bag",
                "starrailexpress:dnf_soap",
                "noellesroles:shisiye",
                "noellesroles:signed_paper",
                "noellesroles:mercenary_contract",
                "noellesroles:diving_helmet",
                "noellesroles:night_vision_glasses",
                "noellesroles:life_and_death_shape",
                "noellesroles:noell_paperclip",
                "noellesroles:jetpack",
                "minecraft:clock",
                "minecraft:lantern",
                "noellesroles:passbook",
                "minecraft:written_book"));
        BuiltInRegistries.ITEM.entrySet().stream()
                .filter(entry -> SRE.canDropItem.contains(entry.getKey().toString()))
                .map(entry -> entry.getValue().getDefaultInstance().getItem())
                .forEach(item -> {
                    ModEventsRegister.canThrowItems.add(item);
                });

    }

}
