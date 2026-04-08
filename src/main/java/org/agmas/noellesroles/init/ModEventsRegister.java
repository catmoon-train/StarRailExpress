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
import io.wifi.starrailexpress.entity.NoteEntity;
import io.wifi.starrailexpress.event.*;
import io.wifi.starrailexpress.event.AllowShootRevolverDrop.ShouldDropResult;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.ServerTaskInfoClasses;
import io.wifi.starrailexpress.index.SREDataComponentTypes;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.index.tag.TMMItemTags;
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
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.Vec3;
import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.harpymodloader.events.ModdedRoleRemoved;
import org.agmas.noellesroles.*;
import org.agmas.noellesroles.commands.BroadcastCommand;
import org.agmas.noellesroles.component.*;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.agmas.noellesroles.effects.TimeStopEffect;
import org.agmas.noellesroles.entity.HallucinationAreaManager;
import org.agmas.noellesroles.entity.PuppeteerBodyEntity;
import org.agmas.noellesroles.entity.ServerSmokeAreaManager;
import org.agmas.noellesroles.entity.WheelchairEntity;
import org.agmas.noellesroles.events.OnVendingMachinesBuyItems;
import org.agmas.noellesroles.game.ChairWheelRaceGame;
import org.agmas.noellesroles.item.HandCuffsItem;
import org.agmas.noellesroles.modifier.NRModifiers;
import org.agmas.noellesroles.modifier.expedition.ExpeditionComponent;
import org.agmas.noellesroles.packet.BloodConfigS2CPacket;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role.RedHouseRoles;
import org.agmas.noellesroles.roles.commander.CommanderHandler;
import org.agmas.noellesroles.roles.conspirator.ConspiratorKilledPlayer;
import org.agmas.noellesroles.roles.executioner.ExecutionerPlayerComponent;
import org.agmas.noellesroles.roles.executioner.ShootingFrenzyPlayerComponent;
import org.agmas.noellesroles.roles.fortuneteller.FortunetellerPlayerComponent;
import org.agmas.noellesroles.roles.gambler.GamblerHandler;
import org.agmas.noellesroles.roles.hoan_meirin.HoanMeirinFistPunchHandler;
import org.agmas.noellesroles.roles.ma_chen_xu.MaChenXuEventHandler;
import org.agmas.noellesroles.roles.thief.ThiefPlayerComponent;
import org.agmas.noellesroles.roles.veteran.VeteranKnifeHandler;
import org.agmas.noellesroles.roles.voodoo.VoodooDeathHandler;
import org.agmas.noellesroles.utils.*;
import pro.fazeclan.river.stupid_express.constants.SEModifiers;
import pro.fazeclan.river.stupid_express.constants.SERoles;
import pro.fazeclan.river.stupid_express.modifier.refugee.cca.PlayerStatsBeforeRefugee;
import pro.fazeclan.river.stupid_express.modifier.refugee.cca.RefugeeComponent;
import pro.fazeclan.river.stupid_express.modifier.split_personality.cca.SplitPersonalityComponent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
            org.agmas.noellesroles.roles.imitator.ImitatorPlayerComponent imitComp = ModComponents.IMITATOR.get(victim);
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
            component.triggerDeath(30 * 20, null, victim.position());
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
            if (stack.getItem() == org.agmas.noellesroles.repack.HSRItems.ANTIDOTE) {
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

    public static void registerEvents() {
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
                    // 播放提示音并向玩家发送提示（只对受害玩家播放）
                    if (victim instanceof ServerPlayer sp) {
                        sp.serverLevel().playSound(sp, sp.blockPosition(), SoundEvents.SHIELD_BLOCK, SoundSource.MASTER,
                                1.0F, 1.0F);
                        sp.displayClientMessage(Component.translatable("message.sre.unyielding.immune_civilian")
                                .withStyle(ChatFormatting.GREEN), true);
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
        // 不屈修饰符：一次性免疫被平民误杀；杀手阵营对杀手攻击免疫
        AllowPlayerDeathWithKiller.EVENT.register((victim, killer, deathReason) -> {
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
            if (victimRole != null
                    && io.wifi.starrailexpress.cca.SREGameWorldComponent.isKillerTeamRoleStatic(victimRole)) {
                if (killer != null && killer != victim && killerRole != null
                        && io.wifi.starrailexpress.cca.SREGameWorldComponent.isKillerTeamRoleStatic(killerRole)
                        && !victimRole.isNeutrals() && !killerRole.isNeutrals() && !killerRole.isNeutralForKiller()) {
                    // 若该职业通过 setNeutralForKiller 标记为对杀手中立，则不触发不屈的“杀手间免疫”效果
                    if (victimRole.isNeutralForKiller()) {
                        // skip unyielding killer-team immunity for neutral-for-killer roles
                    } else {
                        if (victim instanceof ServerPlayer sp) {
                            sp.displayClientMessage(Component.translatable("message.sre.unyielding.immune_killer")
                                    .withStyle(ChatFormatting.RED), true);
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
                    // 播放提示音并向玩家发送提示
                    victim.level().playSound(null, victim.blockPosition(), SoundEvents.SHIELD_BLOCK, SoundSource.MASTER,
                            1.0F, 1.0F);
                    if (victim instanceof ServerPlayer sp) {
                        sp.displayClientMessage(Component.translatable("message.sre.unyielding.immune_civilian")
                                .withStyle(ChatFormatting.GREEN), true);
                    }
                    return false;
                }
            }

            return true;
        });
        MaChenXuEventHandler.register();
        VeteranKnifeHandler.register();
        GamblerHandler.register();
        StalkerPlayerComponent.registerEvents();
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
        ShootingFrenzyPlayerComponent.registerGunNoDropEvent();
        ExecutionerPlayerComponent.registerBackfireEvent();
        ShootingFrenzyPlayerComponent.registerFrenzyCooldownEvent();

        HoanMeirinFistPunchHandler.register();
        VoodooDeathHandler.registerEvents();
        PlayerStatsBeforeRefugee.beforeLoadFunc = (player) -> {
            ModComponents.DEATH_PENALTY.get(player).init();
        };
        OnGameEnd.EVENT.register((world, gameWorldComponent) -> {
            HoanMeirinFistPunchHandler.PUNCH_RECORDS.clear();
            RoleShopHandler.resetOldmanEasterEggState();
            // 重置所有玩家的锁匠灵感
            world.players().forEach(player -> {
                LocksmithInspirationComponent locksmithInspiration = ModComponents.LOCKSMITH_INSPIRATION.get(player);
                locksmithInspiration.init();
            });
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
                    } else {
                        return false;
                    }
                }
            }
            return true;
        });
        UseEntityCallback.EVENT.register((player, level, interactionHand, entity, entityHitResult) -> {
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
        OnTeammateKilledTeammate.EVENT.register((victim, killer, isInnocent, deathReason) -> {
            if (GameUtils.isPlayerAliveAndSurvival(killer)) {
                if (isInnocent) {
                    SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(victim.level());
                    if (gameWorldComponent.isRole(victim, TMMRoles.DISCOVERY_CIVILIAN)) {
                        // 跳过游客惩罚
                        return;
                    }
                    // 检查是否是疯狂模式下的魔术师，如果是则不算误杀
                    if (gameWorldComponent.isRole(victim, ModRoles.MAGICIAN)) {
                        var psychoComponent = SREPlayerPsychoComponent.KEY.get(victim);
                        if (psychoComponent != null && psychoComponent.getPsychoTicks() > 0) {
                            // 魔术师处于疯狂模式，不算误杀
                            return;
                        }
                    }

                    if (gameWorldComponent.isRole(victim, ModRoles.VOODOO)) {
                        return;
                    }
                    if (NoellesRolesConfig.HANDLER.instance().accidentalKillPunishment) {
                        if (deathReason.getPath().equals("revolver_shot")
                                || deathReason.getPath().equals("sniper_rifle")
                                || deathReason.getPath().equals("nunchuck_hit")
                                || deathReason.getPath().equals("bat_hit")
                                || deathReason.getPath().equals("gun_shot")
                                || deathReason.getPath().equals("hoan_meirin_attack")
                                || deathReason.getPath().equals("arrow")
                                || deathReason.getPath().equals("trident")
                                || deathReason.getPath().equals("knife_stab")
                                || deathReason.getPath().equals("knife")
                                || deathReason.getPath().equals("fell_out_of_train")
                                || deathReason.getPath().equals("poison")
                                || deathReason.getPath().equals("throwing_knife_hit")
                                || deathReason.getPath().equals("bowen")
                                || deathReason.getPath().equals("fire_axe")) {
                            GameUtils.killPlayer(killer, true, null, Noellesroles.id("shot_innocent"));

                            // 仇杀客事件：误杀发生时强化仇杀客
                            for (Player player : victim.level().players()) {
                                if (gameWorldComponent.isRole(player, ModRoles.BLOOD_FEUDIST)) {
                                    BloodFeudistPlayerComponent bfComp = ModComponents.BLOOD_FEUDIST.get(player);
                                    if (bfComp != null) {
                                        bfComp.onAccidentalKill();
                                    }
                                }
                            }
                        }
                    }
                }
            }
        });
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
                var thiefComponent = org.agmas.noellesroles.roles.thief.ThiefPlayerComponent.KEY.get(killer);
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
        });

        WayfarerPlayerComponent.registerEvents();
        OnPlayerDeath.EVENT.register((playerEntity, reason) -> {
            FortunetellerPlayerComponent.KEY.get(playerEntity).init();
            PuppeteerPlayerComponent.KEY.get(playerEntity).clear();
            RoleUtils.RemoveAllEffects(playerEntity);
            SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(playerEntity.level());
            if (gameWorldComponent.isRole(playerEntity,
                    ModRoles.THE_INSANE_DAMNED_PARANOID_KILLER_OF_DOOM_DEATH_DESTRUCTION_AND_WAFFLES)) {
                final var insaneKillerPlayerComponent = InsaneKillerPlayerComponent.KEY.get(playerEntity);
                insaneKillerPlayerComponent.init();
            }
            if (gameWorldComponent.isRole(playerEntity, ModRoles.JOJO)) {
                int dropCount = 1 + MCItemsUtils.hasItem(playerEntity, TMMItemTags.GUNS);
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
                        .equals(ModRoles.THE_INSANE_DAMNED_PARANOID_KILLER_OF_DOOM_DEATH_DESTRUCTION_AND_WAFFLES
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
            HoanMeirinFistPunchHandler.PUNCH_RECORDS.clear();
            RoleShopHandler.resetOldmanEasterEggState();
            SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(serverLevel);
            WorldModifierComponent worldModifierComponent = WorldModifierComponent.KEY.get(serverLevel);
            boolean hasDio = false;
            boolean hasRecorder = false;
            // 年兽除岁效果：给所有玩家分发4个鞭炮
            boolean hasNianShou = false;
            boolean hasArsonist = false;
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
                } else if (gameWorldComponent.isRole(p, ModRoles.NIAN_SHOU)) {
                    hasNianShou = true;
                } else if (gameWorldComponent.isRole(p, SERoles.ARSONIST)) {
                    hasArsonist = true;
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
            if (hasArsonist) {
                all_players.forEach((p) -> {
                    if (p != null) {
                        BroadcastCommand.BroadcastMessage(p, Component
                                .translatable("message.noellesroles.arsonist.entry").withStyle(ChatFormatting.YELLOW));
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
                .equals(ModRoles.THE_INSANE_DAMNED_PARANOID_KILLER_OF_DOOM_DEATH_DESTRUCTION_AND_WAFFLES_ID)));
        SRE.canUseChatHudPlayer.add(player -> {
            return SREClient.gameComponent != null && SREClient.gameComponent.isRunning()
                    && SREClient.gameComponent.getGameMode() instanceof ChairWheelRaceGame;
        });
        SRE.canUseOtherPerson.add((role -> role.getIdentifier()
                .equals(TMMRoles.DISCOVERY_CIVILIAN.getIdentifier())));
        SRE.canUseOtherPerson.add((role -> role.getIdentifier()
                .equals(ModRoles.THE_INSANE_DAMNED_PARANOID_KILLER_OF_DOOM_DEATH_DESTRUCTION_AND_WAFFLES_ID)));
        SRE.canUseOtherPerson.add((role -> role.getIdentifier()
                .equals(ModRoles.MANIPULATOR_ID)));
        SRE.canCollide.add(a -> {
            final var gameWorldComponent = SREGameWorldComponent.KEY.get(a.level());
            if (gameWorldComponent.isRole(a,
                    ModRoles.THE_INSANE_DAMNED_PARANOID_KILLER_OF_DOOM_DEATH_DESTRUCTION_AND_WAFFLES)) {
                if (InsaneKillerPlayerComponent.KEY.get(a).isActive) {
                    return true;
                }
            }
            return false;
        });
        SRE.canCollide.add(a -> {
            if (a.hasEffect(MobEffects.INVISIBILITY) || a.hasEffect(ModEffects.NO_COLLIDE)) {
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
                                ModRoles.THE_INSANE_DAMNED_PARANOID_KILLER_OF_DOOM_DEATH_DESTRUCTION_AND_WAFFLES)) {
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
                "noellesroles:stalker_knife_offhand",
                "noellesroles:pill",
                "noellesroles:pocket_watch",
                "noellesroles:throwing_knife",
                "noellesroles:shisiye",
                "noellesroles:signed_paper",
                "noellesroles:mercenary_contract",
                "noellesroles:diving_helmet",
                "noellesroles:life_and_death_shape",
                "noellesroles:noell_paperclip",
                "minecraft:clock",
                "noellesroles:passbook",
                "minecraft:written_book"));

    }

}
