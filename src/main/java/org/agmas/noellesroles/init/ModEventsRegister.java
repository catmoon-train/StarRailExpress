package org.agmas.noellesroles.init;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.rules.*;
import io.wifi.starrailexpress.api.SREGameModes;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.api.replay.GameReplayUtils;
import io.wifi.starrailexpress.cca.SREGameRoundEndComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerPsychoComponent;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.content.block.SmallDoorBlock;
import io.wifi.starrailexpress.content.entity.NoteEntity;
import io.wifi.starrailexpress.content.item.StandardRevolverItem;
import io.wifi.starrailexpress.event.*;
import io.wifi.starrailexpress.util.TrueFalseResult;
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
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
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
import net.minecraft.world.level.Level;
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
import org.agmas.noellesroles.game.roles.innocence.avenger.AvengerPlayerComponent;
import org.agmas.noellesroles.game.roles.innocence.awesome_binglus.AwesomePlayerComponent;
import org.agmas.noellesroles.game.roles.innocence.boxer.BoxerPlayerComponent;
import org.agmas.noellesroles.game.roles.innocence.broadcaster.BroadcasterPlayerComponent;
import org.agmas.noellesroles.game.roles.innocence.cake_maker.CakeMakerComponent;
import org.agmas.noellesroles.game.roles.innocence.fool.TarotAssemblyManager;
import org.agmas.noellesroles.game.roles.innocence.fortuneteller.FortunetellerPlayerComponent;
import org.agmas.noellesroles.game.roles.innocence.glitch_robot.GlitchRobotPlayerComponent;
import org.agmas.noellesroles.game.roles.innocence.hoan_meirin.HoanMeirinFistPunchHandler;
import org.agmas.noellesroles.game.roles.innocence.veteran.VeteranKnifeHandler;
import org.agmas.noellesroles.game.roles.innocence.voodoo.VoodooDeathHandler;
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
import org.agmas.noellesroles.game.roles.neutral.cupid.CupidPlayerComponent;
import org.agmas.noellesroles.game.roles.neutral.gambler.GamblerHandler;
import org.agmas.noellesroles.game.roles.neutral.cuckoo.CuckooEggHandler;
import org.agmas.noellesroles.game.roles.neutral.infected.InfectedWinChecker;
import org.agmas.noellesroles.game.roles.neutral.mercenary.MercenaryPlayerComponent;
import org.agmas.noellesroles.game.roles.neutral.puppeteer.PuppeteerPlayerComponent;
import org.agmas.noellesroles.game.roles.neutral.raven.RavenPlayerComponent;
import org.agmas.noellesroles.game.roles.neutral.thief.ThiefPlayerComponent;
import org.agmas.noellesroles.game.roles.neutral.wayfarer.WayfarerPlayerComponent;
import org.agmas.noellesroles.game.roles.special.better_vigilante.BetterVigilantePlayerComponent;
import org.agmas.noellesroles.game.roles.vigilante.patroller.PatrollerPlayerComponent;
import org.agmas.noellesroles.packet.BloodConfigS2CPacket;
import org.agmas.noellesroles.packet.EmbalmerSkinSwapS2CPacket;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role.TraitorAndModifiers;
import org.agmas.noellesroles.role.touhou.RedHouseRoles;
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

    // 本局游戏是否已发放过年兽鞭炮（一局只能有一次）
    private static boolean nianShouFirecrackersDistributedThisGame = false;
    // private static AttributeModifier oldmanAttribute = new AttributeModifier(
    // Noellesroles.id("oldman"), -0.4f, AttributeModifier.Operation.ADD_VALUE);
    // private static AttributeModifier windYaoseScaleAttribute = new
    // AttributeModifier(
    // Noellesroles.id("wind_yaose"), -0.2f, AttributeModifier.Operation.ADD_VALUE);

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
        // 消耗一次抵挡次数，取消偷皮并进入眩晕（5秒禁止移动），广播恢复原皮肤
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
     * 处理斗士无敌反制
     * 钢筋铁骨期间可以反弹任何死亡
     *
     * @param victim      受害者
     * @param deathReason 死亡原因
     * @return true 表示成功反制，应阻止死亡
     */
    private static boolean handleBoxerInvulnerability(Player victim, ResourceLocation deathReason) {
        if (victim == null || victim.level().isClientSide())
            return false;

        // 检查受害者是否是斗士
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(victim.level());

        // 模仿者斗士无敌检测
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

        if (!gameWorld.isRole(victim, ModRoles.FIGHTER))
            return false;

        // 获取斗士组件
        BoxerPlayerComponent boxerComponent = ModComponents.FIGHTER.get(victim);

        // 检查是否处于无敌状态
        if (!boxerComponent.isInvulnerable)
            return false;

        // 钢筋铁骨无法抵挡：被列车碾压、误杀平民死亡(shot_innocent)、挂机死亡
        if (deathReason.equals(GameConstants.DeathReasons.FELL_OUT_OF_TRAIN)
                || deathReason.getPath().equals("death_afk")
                || deathReason.getPath().equals("shot_innocent")) {
            return false; // 穿透无敌，允许死亡
        }

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

    public static void reJudgeSpectatorsPenalty(Level level) {
        final ArrayList<Player> players = new ArrayList<>(level.players());
        players.removeIf(p -> !GameUtils.isPlayerSpectator(p));
        handleDeathPenalty(level, players, true, true);
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

    /**
     * 蛋糕师死亡 - 取消进行中的烘焙并移除已部署的烟熏炉，
     * 防止烟熏炉残留在世界上（并遗留到下一局）。
     */
    private static void handleCakeMakerDeath(Player victim) {
        if (victim == null || victim.level().isClientSide())
            return;

        SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(victim.level());
        if (!gameWorldComponent.isRole(victim, ModRoles.CAKE_MAKER))
            return;

        CakeMakerComponent.KEY.get(victim).onDeath();
    }

    public static void handleDeathPenalty(Player victim) {
        handleDeathPenalty(victim.level(), List.of(victim), false, false);
    }

    public static void handleDeathPenalty(Player victim, boolean ignoreDoctor,
            boolean ignoreLooseEnd) {
        handleDeathPenalty(victim.level(), List.of(victim), ignoreDoctor, ignoreLooseEnd);
    }

    public static void handleDeathPenalty(Level level, List<Player> victims, boolean ignoreDoctor,
            boolean ignoreLooseEnd) {
        SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(level);
        boolean doctorAlive = false;
        boolean looseEndAlive = false;
        // boolean INSANE_alive = false;
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
                // 已经在别的地方处理过了不给死亡限制。
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
     * 处理钳工死亡 - 将拆弹钳传递给另一名存活的平民（参考会计传递存折逻辑）
     */
    private static void handleFitterDeath(Player victim) {
        if (victim == null || victim.level().isClientSide())
            return;

        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(victim.level());
        if (!gameWorld.isRole(victim, ModRoles.FITTER))
            return;

        // 查找钳工背包中的拆弹钳
        ArrayList<ItemStack> itemsToTransfer = new ArrayList<>();
        for (int i = 0; i < victim.getInventory().getContainerSize(); i++) {
            ItemStack stack = victim.getInventory().getItem(i);
            if (stack.getItem() == org.agmas.noellesroles.init.ModItems.PLIERS) {
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
                        Component.translatable("message.noellesroles.fitter.pliers_inherited",
                                victim.getName().getString())
                                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                        true);
            }
        }
    }

    /**
     * 处理被鹈鹕吞噬的玩家死亡 - 从鹈鹕肚子中释放并恢复正常死亡状态
     */
    private static void handleStashedPlayerDeath(Player victim) {
        if (victim == null || victim.level().isClientSide())
            return;
        if (!(victim instanceof ServerPlayer sp))
            return;
        if (!org.agmas.noellesroles.game.roles.neutral.pelican.PelicanManager.isStashed(sp))
            return;
        org.agmas.noellesroles.game.roles.neutral.pelican.PelicanManager.onStashedPlayerDeath(sp);
    }

    /**
     * 处理教父死亡 - 家族成员恢复原身份
     */
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

    /**
     * 处理鹈鹕死亡 - 将肚子里的所有玩家释放出来
     */
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
        // Cake Maker: ingredient input via right-click on smoker interaction entity
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClientSide || hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
            if (CakeMakerComponent.isSmokerInteractionEntity(entity)) {
                UUID ownerId = CakeMakerComponent.getSmokerOwner(entity);
                // Only the cake maker who owns the smoker can add ingredients
                if (ownerId != null && ownerId.equals(player.getUUID())) {
                    if (ModComponents.CAKE_MAKER.get(player).addIngredient(player, entity)) {
                        return InteractionResult.SUCCESS;
                    }
                }
            }
            // Cake eating via right-click on cake interaction entity (any player)
            if (CakeMakerComponent.isCakeInteractionEntity(entity)) {
                UUID ownerId = CakeMakerComponent.getCakeOwner(entity);
                if (ownerId != null) {
                    ServerPlayer cakeOwner = world.getServer().getPlayerList().getPlayer(ownerId);
                    if (cakeOwner != null) {
                        if (ModComponents.CAKE_MAKER.get(cakeOwner).eat(entity, (ServerPlayer) player)) {
                            return InteractionResult.SUCCESS;
                        }
                    }
                }
            }
            return InteractionResult.PASS;
        });
        // 吝啬 - 商店购买返还20%金币
        StandardRevolverItem.registerEvents();
        AllowPlayerPunching.EVENT.register(player -> {
            RavenPlayerComponent raven = ModComponents.RAVEN.get(player);
            return SREGameWorldComponent.KEY.get(player.level()).isRole(player, ModRoles.RAVEN) && raven.isHunting();
        });
        AllowPlayerDeathWithKiller.EVENT.register((victim, killer, reason) -> {
            if (killer == null || !SREGameWorldComponent.KEY.get(killer.level()).isRole(killer, ModRoles.RAVEN)) return true;
            RavenPlayerComponent raven = ModComponents.RAVEN.get(killer);
            if (!raven.isHunting()) return true;
            if (!raven.canKill(victim)) {
                raven.endHunt(true);
                return false;
            }
            return true;
        });
        OnPlayerDeathWithKiller.EVENT.register((victim, killer, reason) -> {
            if (killer == null || !SREGameWorldComponent.KEY.get(killer.level()).isRole(killer, ModRoles.RAVEN)) return;
            RavenPlayerComponent raven = ModComponents.RAVEN.get(killer);
            if (raven.canKill(victim)) raven.onTargetKilled(victim);
        });
        AllowPlayerDeathWithKiller.EVENT.register((victim, killer, reason) -> {
            if (!SREGameWorldComponent.KEY.get(victim.level()).isRole(victim, ModRoles.RAVEN)) return true;
            RavenPlayerComponent raven = ModComponents.RAVEN.get(victim);
            return !raven.isHunting();
        });
        RefugeeComponent.register();
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
                    // 被操控的目标在操控期间死亡：奖励操纵师金币并结束操控
                    if (inControlCCA.isControlling && inControlCCA.controller != null) {
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
            }
        });

        // 肉汁独处保护机制 - 杀手/中立只能在单独相处时击杀肉汁
        // 巫师魔药：60 秒内免疫一次致命攻击

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

            // 纵火犯点火伤害（不触发独处保护）
            if (deathReason != null && deathReason.getPath().equals("ignited")) {
                return true;
            }

            // 亡命徒职业：肉汁不免疫来自亡命徒角色的伤害
            if (gameWorld.isRole(killer, TMMRoles.LOOSE_END)
                    || gameWorld.isRole(killer, SpecialGameModeRoles.SUPER_LOOSE_END)) {
                return true;
            }

            // 检查击杀者是否存在且是否为非乘客阵营
            if (killer == null || gameWorld.isInnocent(killer)) {
                return true;
            }

            // 门框检测：肉汁1.5格范围内存在模组门方块时，保护失效
            double doorCheckRange = 1.5;
            BlockPos meatballPos = victim.blockPosition();
            for (int dx = -2; dx <= 2; dx++) {
                for (int dy = -2; dy <= 2; dy++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        BlockPos checkPos = meatballPos.offset(dx, dy, dz);
                        double dist = Math.sqrt(
                                (checkPos.getX() + 0.5 - victim.getX()) * (checkPos.getX() + 0.5 - victim.getX()) +
                                (checkPos.getY() + 0.5 - victim.getY()) * (checkPos.getY() + 0.5 - victim.getY()) +
                                (checkPos.getZ() + 0.5 - victim.getZ()) * (checkPos.getZ() + 0.5 - victim.getZ()));
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

            // 肉汁独处判定：
            // 规则：规定范围内只存在杀手或中立时，肉汁被判定为「独处」，允许击杀
            // 范围内只要存在至少一个好人，无论是否有杀手/中立同时存在，肉汁就不算独处，阻止击杀
            double safeDistanceSq = 4.0 * 4.0; // 水平4格距离平方
            double safeHeightSq = 3.0 * 3.0; // y轴3格距离平方

            for (Player nearbyPlayer : victim.level().players()) {
                if (nearbyPlayer == victim || nearbyPlayer == killer) {
                    continue;
                }
                if (!GameUtils.isPlayerAliveAndSurvival(nearbyPlayer)) {
                    continue;
                }

                double dx = nearbyPlayer.getX() - victim.getX();
                double dy = nearbyPlayer.getY() - victim.getY();
                double dz = nearbyPlayer.getZ() - victim.getZ();

                double horizontalDistSq = dx * dx + dz * dz;
                // 先判断此人是否在保护范围内
                if (horizontalDistSq <= safeDistanceSq && dy * dy <= safeHeightSq) {
                    // 范围内有人 —— 看其阵营
                    if (gameWorld.isInnocent(nearbyPlayer)) {
                        // 范围内有好人 → 不独处，阻止击杀（保护肉汁）
                        if (victim instanceof ServerPlayer sp) {
                            sp.displayClientMessage(
                                    Component.translatable("message.noellesroles.meatball.protected")
                                            .withStyle(ChatFormatting.GREEN),
                                    true);
                        }
                        return false;
                    }
                    // 此人是杀手/中立 —— 不构成保护，继续检查其他玩家
                }
            }

            // 循环结束仍未触发保护 → 范围内不存在好人（只存在杀手/中立）→ 独处 → 允许击杀
            return true;
        });
        THEventHandler.registerEvents();
        NinjaPlayerComponent.registerEvents();
        org.agmas.noellesroles.game.roles.killer.nostalgist.NostalgistPlayerComponent.registerEvents();
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
        CupidPlayerComponent.registerEvents();
        ChatHudRules.cantUseChatHud.add((p) -> {
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
                    return TrueFalseResult.TRUE;
            }
            return TrueFalseResult.PASS;
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
                org.agmas.noellesroles.game.roles.innocence.fool.TarotAssemblyManager.clearTrackedTarget(serverLevel,
                        victim.getUUID());
            }
            return true;
        });
        AfterShieldAllowPlayerDeathWithKiller.EVENT.register((victim, killer, deathReason) -> {
            if (victim.level() instanceof ServerLevel serverLevel) {
                org.agmas.noellesroles.game.roles.innocence.fool.TarotAssemblyManager.clearTrackedTarget(serverLevel,
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
        // 注册亡灵之主骨杖处理器
        org.agmas.noellesroles.content.item.BoneStaffHandler.register();

        RiotShieldHandler.register();
        // 注册仁之剑处理器
        BenevolenceSwordHandler.register();
        // 布谷鸟蛋交互注册
        CuckooEggHandler.register();
        // 注册保安技能
        GuardPlayerHandler.register();
        // 格罗赛尔游记：放逐管理器（tick + 击杀改判 + 一局结束清理）
        org.agmas.noellesroles.content.item.GroselleJourneyManager.register();
        VoodooDeathHandler.registerEvents();
        PlayerStatsBeforeRefugee.beforeLoadFunc = (player) -> {
            ModComponents.DEATH_PENALTY.get(player).init();
        };
        OnGameEnd.EVENT.register((world, gameWorldComponent) -> {
            nianShouFirecrackersDistributedThisGame = false;
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
                org.agmas.noellesroles.game.roles.innocence.builder.BuilderPlayerComponent builderComp = org.agmas.noellesroles.component.ModComponents.BUILDER
                        .get(player);
                builderComp.clearAllWalls();
            }
            // 清除全局墙位置注册表
            org.agmas.noellesroles.game.roles.innocence.builder.BuilderWallPositions.clearAll();
            // 清除冒险家开启的路径点
            io.wifi.starrailexpress.game.data.WaypointVisibilityManager.get(world.getServer())
                    .setWaypointsVisibility(false);
            // 清除鹈鹕状态 - 释放所有被吞噬的玩家
            org.agmas.noellesroles.game.roles.neutral.pelican.PelicanManager.releaseAllInWorld(world);
            org.agmas.noellesroles.game.roles.neutral.pelican.PelicanManager.clearAll();
            // 已经发包清空了
            // 清除嬉命人变装 - 恢复所有玩家皮肤和语音
            for (ServerPlayer p : world.players()) {
                ServerPlayNetworking.send(p, EmbalmerSkinSwapS2CPacket.clear());
            }
            // 清除所有肉汁的悬赏
            for (ServerPlayer player : world.players()) {
                org.agmas.noellesroles.component.ModComponents.MEATBALL.get(player).init();
            }
            // 重置游玩区域内所有实体交互方块的内置冷却
            if (io.wifi.starrailexpress.content.block_entity.EntityInteractionBlockEntity.getCountForMap(world) == 0) {
                // 当前地图没有任何实体交互方块，跳过
            } else {
                var playArea = io.wifi.starrailexpress.cca.AreasWorldComponent.KEY.get(world).getPlayArea();
                int minChunkX = ((int) playArea.minX) >> 4;
                int maxChunkX = ((int) playArea.maxX) >> 4;
                int minChunkZ = ((int) playArea.minZ) >> 4;
                int maxChunkZ = ((int) playArea.maxZ) >> 4;
                var chunkSource = world.getChunkSource();
                for (int cx = minChunkX; cx <= maxChunkX; cx++) {
                    for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                        var chunk = chunkSource.getChunkNow(cx, cz);
                        if (chunk != null) {
                            for (var be : chunk.getBlockEntities().values()) {
                                if (be instanceof io.wifi.starrailexpress.content.block_entity.EntityInteractionBlockEntity entity
                                        && playArea.contains(entity.getBlockPos().getCenter())) {
                                    entity.resetAllCooldowns();
                                }
                            }
                        }
                    }
                }
            }
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
            var playerRole = gameC.getRole(player);
            if (playerRole == null || !playerRole.isVigilanteTeam())
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
        DropRules.canDrop.add((player) -> {
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
                Harpymodloader.officialVerify = Noellesroles.checkMJVerify();
            } else {
                Harpymodloader.officialVerify = true;
            }
        });
        CommanderHandler.registerChatEvent();
        InsaneKillerPlayerComponent.registerEvent();
        ConspiratorKilledPlayer.registerEvents();
        // 注册疫使胜利检测和加速检测
        InfectedWinChecker.registerEvent();
        EntityClearUtils.registerResetEvent();
        org.agmas.noellesroles.game.roles.innocence.photographer.PhotographerFrameEvents.register();
        ReplayRules.cantSendReplay.add(player -> {
            DeathPenaltyComponent component = ModComponents.DEATH_PENALTY.get(player);
            if (component != null) {
                if (component.hasPenalty())
                    return true;
            }
            return false;
        });
        ArmorRules.canStickArmor.add((deathInfo -> {
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
        // 通用物证：血迹路径。凶手击杀后开始"滴血跟随"，边走边沿途留下会衰减的血迹（仅他杀触发）。
        OnPlayerDeathWithKiller.EVENT.register((victim, killer, deathReason) -> {
            io.wifi.starrailexpress.SREConfig cfg = io.wifi.starrailexpress.SREConfig.instance();
            if (cfg == null || !cfg.enableForensicEvidence || !cfg.forensicBloodTrail)
                return;
            if (victim == null || killer == null || victim.level().isClientSide)
                return;
            if (!(killer instanceof net.minecraft.server.level.ServerPlayer killerSp))
                return;
            SREGameWorldComponent gw = SREGameWorldComponent.KEY.get(victim.level());
            if (gw == null || !gw.isRunning())
                return;
            io.wifi.starrailexpress.game.forensic.ForensicCategory cat =
                    io.wifi.starrailexpress.game.forensic.ForensicCategory.fromDeathReason(deathReason);
            // 凶器大类决定滴血持续时长（枪/穿刺出血久=血迹更长，刀较短）
            int bleedTicks = switch (cat) {
                case FIREARM, PROJECTILE -> 14 * 20;
                case BLADE -> 8 * 20;
                default -> 10 * 20;
            };
            gw.startKillerBleed(killerSp, victim.position(), victim.level().getGameTime(), bleedTicks);
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
            Component roleName = RoleUtils.getRoleName(victimRole.identifier());
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
            if (gameWorldComponent.isRole(victim, ModRoles.WIZARD)) {
                ModComponents.WIZARD.get(victim).onPotionShieldBroken();
            }
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
            RoleUtils.removeAllEffects(playerEntity);
            // 葬仪死亡时清除拖动状态
            if (gameWorldComponent.isRole(playerEntity, ModRoles.MORTICIAN_BODYMAKER)) {
                var morticianComponent = org.agmas.noellesroles.component.ModComponents.MORTICIAN_BODYMAKER
                        .get(playerEntity);
                if (morticianComponent != null && morticianComponent.draggedBodyUuid != null) {
                    morticianComponent.draggedBodyUuid = null;
                    morticianComponent.sync();
                }
            }
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
                            MCItemsUtils.clearItem(player, ModItems.LIFE_AND_DEATH_SHAPE, 1);
                            GameUtils.killPlayer(p, true, killer, deathReason);
                            return false;
                        }
                    }
                }
            }
            return true;
        });
        AllowPlayerDeath.EVENT.register((player, deathReason) -> {
            // 算命大师无法抵挡：被列车碾压、误杀平民死亡、挂机死亡
            if (deathReason.equals(GameConstants.DeathReasons.FELL_OUT_OF_TRAIN)
                    || deathReason.getPath().equals("death_afk")
                    || deathReason.getPath().equals("shot_innocent")) {
                return true; // 穿透庇护，允许死亡
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
                org.agmas.noellesroles.game.roles.innocence.fool.TarotAssemblyManager.serverLevelTick(level);
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

        // 游戏开始，安全时间刚开始计时
        OnGameStarted.EVENT.register((serverLevel) -> {
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

            // 判断是否有指定职业
            SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(serverLevel);
            WorldModifierComponent worldModifierComponent = WorldModifierComponent.KEY.get(serverLevel);
            final var all_players = serverLevel.players();
            for (var p : all_players) {
                if (!gameWorldComponent.isJumpAvailable() && GameUtils.isPlayerAliveAndSurvivalIgnoreShitSplit(p)) {
                    // NO JUMPING! For everyone who hasn't permissions
                    if (!p.hasPermissions(2)) {
                        p.getAttribute(Attributes.JUMP_STRENGTH).addOrReplacePermanentModifier(noJumpingAttribute);
                    }
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
        });
        // 游戏正式开始，安全时间结束！
        OnGameTrueStarted.EVENT.register((serverLevel) -> {
            SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(serverLevel);
            boolean hasDio = false;
            boolean hasRecorder = false;
            boolean hasCandlebearer = false;
            boolean hasRaven = false;
            // 年兽除岁效果：给所有玩家分发4个鞭炮
            boolean hasNianShou = false;
            boolean hasArsonist = false;
            boolean hasCuckoo = false;
            boolean hasPelican = false;
            boolean hasGodfather = false;
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
                } else if (gameWorldComponent.isRole(p, ModRoles.RAVEN)) {
                    hasRaven = true;
                } else if (gameWorldComponent.isRole(p, ModRoles.NIAN_SHOU)) {
                    hasNianShou = true;
                } else if (gameWorldComponent.isRole(p, SERoles.ARSONIST)) {
                    hasArsonist = true;
                } else if (gameWorldComponent.isRole(p, ModRoles.CUCKOO)) {
                    hasCuckoo = true;
                } else if (gameWorldComponent.isRole(p, ModRoles.PELICAN)) {
                    hasPelican = true;
                } else if (gameWorldComponent.isRole(p, ModRoles.GODFATHER)) {
                    hasGodfather = true;
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
            if (hasRaven) {
                all_players.forEach((p) -> {
                    if (p != null) {
                        BroadcastCommand.BroadcastMessage(p, Component
                                .translatable("message.noellesroles.raven.entry")
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
            if (hasPelican) {
                all_players.forEach((p) -> {
                    if (p != null) {
                        BroadcastCommand.BroadcastMessage(p, Component
                                .translatable("message.noellesroles.pelican.entry").withStyle(ChatFormatting.YELLOW));
                    }
                });
            }
            if (hasGodfather) {
                GameUtils.serverAsynTaskLists.add(new ServerTaskInfoClasses.SchedulerTask(20 * 6, () -> {
                    all_players.forEach((p) -> {
                        if (p != null) {
                            p.playNotifySound(NRSounds.MAFIA, SoundSource.MASTER, 1.0F, 1.0F);
                        }
                    });
                }));
            }
            if (hasNianShou && !nianShouFirecrackersDistributedThisGame) {
                nianShouFirecrackersDistributedThisGame = true;
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
        // 监听玩家死亡事件 - 用于激活复仇者能力、斗士反制、跟踪者免疫和操纵师死亡判定
        AllowPlayerDeath.EVENT.register((victim, deathReason) -> {
            // 检查窃皮者皮肤死亡免疫
            if (handleSkincrawlerDeath(victim, deathReason))
                return false;
            // 检查斗士无敌反制
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
            // 检查被吞噬玩家死亡 - 从鹈鹕肚子中释放并正常进入死亡
            handleStashedPlayerDeath(victim);

            // 检查医生死亡 - 传递针管
            handleDoctorDeath(victim);

            // 检查锁匠死亡 - 传递巧匠钥匙和撬锁器
            handleLocksmithDeath(victim);

            // 检查会计死亡 - 传递存折
            handleAccountantDeath(victim);

            // 检查钳工死亡 - 传递拆弹钳
            handleFitterDeath(victim);

            // 检查鹈鹕死亡 - 释放肚子里的所有玩家
            handlePelicanDeath(victim);

            // 检查教父死亡 - 家族成员恢复原身份
            handleGodfatherDeath(victim);

            // 检查死亡惩罚
            handleDeathPenalty(victim);

            // 检查故障机器人 - 死亡时生成缓慢效果云
            handleGlitchRobotDeath(victim);

            // 检查蛋糕师 - 死亡时移除烟熏炉，防止残留到下一局
            handleCakeMakerDeath(victim);
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
            final var player = handler.getPlayer();
            ModEventsRegister.handleDeathPenalty(player, true, true);
            sender.sendPacket(new BloodConfigS2CPacket(NoellesRolesConfig.HANDLER.instance().enableClientBlood));
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
        ChatHudRules.canUseChatHud.add((role -> role.getIdentifier()
                .equals(ModRoles.INSANE_KILLER_ID)));
        ChatHudRules.canUseChatHudPlayer.add(player -> {
            return SREClient.gameComponent != null && SREClient.gameComponent.isRunning()
                    && SREClient.gameComponent.getGameMode() instanceof ChairWheelRaceGame;
        });
        RoleVisibilityRules.canUseOtherPerson.add((role -> role.getIdentifier()
                .equals(TMMRoles.DISCOVERY_CIVILIAN.getIdentifier())));
        RoleVisibilityRules.canUseOtherPerson.add((role -> role.getIdentifier()
                .equals(ModRoles.INSANE_KILLER_ID)));
        RoleVisibilityRules.canUseOtherPerson.add((role -> role.getIdentifier()
                .equals(ModRoles.MONOKUMA_ID)));
        RoleVisibilityRules.canUseOtherPerson.add((role -> role.getIdentifier()
                .equals(ModRoles.MANIPULATOR_ID)));
        CollisionRules.canCollide.add(a -> {
            final var gameWorldComponent = SREGameWorldComponent.KEY.get(a.level());
            if (gameWorldComponent.isRole(a,
                    ModRoles.INSANE_KILLER)) {
                if (InsaneKillerPlayerComponent.KEY.get(a).isActive) {
                    return true;
                }
            }
            return false;
        });
        CollisionRules.canCollide.add(a -> {
            if (a.hasEffect(MobEffects.INVISIBILITY) || a.hasEffect(ModEffects.SAFE_TIME)
                    || a.hasEffect(ModEffects.NO_COLLIDE)) {
                return true;
            }
            return false;
        });
        CollisionRules.cantPushableBy.add(entity -> {
            if (entity instanceof PuppeteerBodyEntity) {
                return true;
            }
            return false;
        });
        CollisionRules.cantPushableBy.add(entity -> {
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
        CollisionRules.canCollideEntity.add(entity -> {
            return entity instanceof PuppeteerBodyEntity;
        });
        CollisionRules.cantPushableBy.add(entity -> {
            return (entity instanceof NoteEntity);
        });
        DropRules.canDropItem.addAll(List.of(
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
                .filter(entry -> DropRules.canDropItem.contains(entry.getKey().toString()))
                .map(entry -> entry.getValue().getDefaultInstance().getItem())
                .forEach(item -> {
                    ModEventsRegister.canThrowItems.add(item);
                });

    }

}
