package org.agmas.noellesroles.client;

import java.util.HashMap;

import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.noellesroles.component.FoodDrinkGlowComponent;
import org.agmas.noellesroles.component.InfectedPlayerComponent;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.agmas.noellesroles.content.entity.SaltedFishBodyEntity;
import org.agmas.noellesroles.content.item.SignedPaperItem;
import org.agmas.noellesroles.game.roles.innocence.awesome_binglus.AwesomePlayerComponent;
import org.agmas.noellesroles.game.roles.innocence.detective.AgentPlayerComponent;
import org.agmas.noellesroles.game.roles.innocence.fool.FoolPlayerComponent;
import org.agmas.noellesroles.game.roles.innocence.leather_pig.LeatherPigPlayerComponent;
import org.agmas.noellesroles.game.roles.innocence.monitor.MonitorPlayerComponent;
import org.agmas.noellesroles.game.roles.killer.executioner.ExecutionerPlayerComponent;
import org.agmas.noellesroles.game.roles.killer.insane_killer.InsaneKillerPlayerComponent;
import org.agmas.noellesroles.game.roles.killer.manipulator.ManipulatorPlayerComponent;
import org.agmas.noellesroles.game.roles.neutral.admirer.AdmirerPlayerComponent;
import org.agmas.noellesroles.game.roles.neutral.candlebearer.CandleBearerPlayerComponent;
import org.agmas.noellesroles.game.roles.neutral.cuckoo.CuckooEggData;
import org.agmas.noellesroles.game.roles.neutral.monokuma.MonokumaEventHandler;
import org.agmas.noellesroles.game.roles.neutral.pelican.PelicanPlayerComponent;
import org.agmas.noellesroles.game.roles.neutral.wayfarer.WayfarerPlayerComponent;
import org.agmas.noellesroles.game.roles.special.better_vigilante.BetterVigilantePlayerComponent;
import org.agmas.noellesroles.game.roles.vigilante.ghost_eye.GhostEyePlayerComponent;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role.TraitorAndModifiers;
import org.agmas.noellesroles.role.touhou.RedHouseRoles;
import org.agmas.noellesroles.utils.MCItemsUtils;
import org.agmas.noellesroles.utils.RoleUtils;

import io.wifi.starrailexpress.api.InstinctType;
import io.wifi.starrailexpress.api.SREGameModes;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.SREArmorPlayerComponent;
import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import io.wifi.starrailexpress.cca.SREPlayerPoisonComponent;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.customrole.CustomRoleLoader;
import io.wifi.starrailexpress.event.client.CommonInstinctEvents;
import io.wifi.starrailexpress.event.client.RoleInstinctEvents;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.roles.SpecialGameModeModifiers;
import io.wifi.starrailexpress.index.SREDataComponentTypes;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.util.Color;
import io.wifi.starrailexpress.util.SREItemUtils;
import io.wifi.starrailexpress.util.TrueFalseAndCustomResult;
import net.minecraft.client.Minecraft;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import pro.fazeclan.river.stupid_express.constants.SEModifiers;
import pro.fazeclan.river.stupid_express.constants.SERoles;
import pro.fazeclan.river.stupid_express.modifier.lovers.cca.LoversComponent;
import pro.fazeclan.river.stupid_express.role.arsonist.cca.DousedPlayerComponent;

/**
 * 高亮获取顺序：
 * <li>通用逻辑：{@link CommonInstinctEvents#ALIVE_COMMON_BEFORE_EVENT}</li>
 * <li>被看逻辑：{@link RoleInstinctEvents#TARGET_HIGHLIGHT_EVENT}</li>
 * <li>被看职业逻辑 {@link SRERole#setToggledOffBeSeenInstinctType(InstinctType)}</li>
 * <li>看人逻辑：{@link RoleInstinctEvents#OBSERVER_HIGHLIGHT_EVENT}</li>
 * <li>看人职业逻辑 {@link SRERole#setInstinctType(InstinctType)}</li>
 * <li>通用逻辑：{@link CommonInstinctEvents#ALIVE_COMMON_AFTER_EVENT}</li>
 * <li>杀手直觉（杀手默认）</li>
 */
public class RoleInstinctRegister {
    public static void registerInstinctEvents() {
        // TODO: 自定义角色的逻辑 待修改 @haiman233
        CustomRoleLoader.registerClientInstinctHandler();
        // 注册使用新 API 的看人 / 被看逻辑
        registerObserverHighlights();
        registerTargetHighlights();
        // 保留原有的特殊逻辑（已部分迁移）
        registerSpecialLogic();
        // 通用兜底逻辑
        registerNormalLogic();
    }

    // ---------- 看人高亮（OBSERVER） ----------
    private static void registerObserverHighlights() {

        // 验尸官
        RoleInstinctEvents.OBSERVER_HIGHLIGHT_EVENT.register(ModRoles.CORONER_ID,
                (client, self, target, hasInstinct) -> {
                    long time = self.level().getGameTime();
                    if (time % 400 >= 100)
                        return TrueFalseAndCustomResult.pass();
                    if (target instanceof PlayerBodyEntity)
                        return TrueFalseAndCustomResult.custom(ModRoles.CORONER.color());
                    if (target instanceof Player targetPlayer) {
                        InsaneKillerPlayerComponent comp = InsaneKillerPlayerComponent.KEY.get(targetPlayer);
                        if (comp.isActive)
                            return TrueFalseAndCustomResult.custom(ModRoles.CORONER.color());
                    }
                    return TrueFalseAndCustomResult.pass();
                });

        // 失忆
        RoleInstinctEvents.OBSERVER_HIGHLIGHT_EVENT.register(SERoles.AMNESIAC.identifier(),
                (client, self, target, hasInstinct) -> {
                    if (SREClient.gameComponent == null || !(target instanceof PlayerBodyEntity))
                        return TrueFalseAndCustomResult.pass();
                    return TrueFalseAndCustomResult.custom(SERoles.AMNESIAC.color());
                });
        // 葬仪：看所有人和尸体都是自己的颜色，且可以透视场上所有尸体
        RoleInstinctEvents.OBSERVER_HIGHLIGHT_EVENT.register(ModRoles.MORTICIAN_BODYMAKER_ID,
                (client, self, target, hasInstinct) -> {
                    if (SREClient.gameComponent == null)
                        return TrueFalseAndCustomResult.pass();
                    if (!SREClient.gameComponent.isRole(self, ModRoles.MORTICIAN_BODYMAKER))
                        return TrueFalseAndCustomResult.pass();

                    // 葬仪总是可以看到尸体（不需要开启杀手直觉）
                    if (target instanceof PlayerBodyEntity) {
                        return TrueFalseAndCustomResult.custom(ModRoles.MORTICIAN_BODYMAKER.color());
                    }

                    // 需要开启杀手直觉才能看到玩家
                    if (!hasInstinct)
                        return TrueFalseAndCustomResult.pass();

                    // 所有玩家都显示葬仪的颜色（无法透视的职业除外）
                    if (target instanceof Player targetPlayer) {
                        // 无法被透视的职业（小透明/秉烛人/雇佣兵/捣蛋鬼）
                        if (isTargetInvisibleToInstinct(targetPlayer)) {
                            return TrueFalseAndCustomResult.disallow();
                        }
                        return TrueFalseAndCustomResult.custom(ModRoles.MORTICIAN_BODYMAKER.color());
                    }
                    return TrueFalseAndCustomResult.pass();
                });
        // 秉烛人：可透视被秉烛的活人与对应尸体
        RoleInstinctEvents.OBSERVER_HIGHLIGHT_EVENT.register(ModRoles.CANDLE_BEARER_ID,
                (client, self, target, hasInstinct) -> {
                    if (!hasInstinct)
                        return TrueFalseAndCustomResult.pass();
                    CandleBearerPlayerComponent component = CandleBearerPlayerComponent.KEY.get(self);
                    // 尸体：已被完成秉烛的显示蓝色
                    if (target instanceof PlayerBodyEntity body) {
                        if (body.getPlayerUuid() != null && component.isCorpseCandleCompleted(body.getPlayerUuid())) {
                            return TrueFalseAndCustomResult.custom(Color.BLUE.getRGB());
                        }
                        if (body.getPlayerUuid() != null && component.isCandleLit(body.getPlayerUuid())) {
                            return TrueFalseAndCustomResult.custom(ModRoles.CANDLE_BEARER.color());
                        }
                        return TrueFalseAndCustomResult.custom(Color.GRAY.getRGB());
                    }
                    // 活人：无法透视的职业不显示，被秉烛过的显示原色，其余灰色
                    if (target instanceof Player targetPlayer) {
                        if (targetPlayer.distanceToSqr(self) > 40 * 40)
                            return TrueFalseAndCustomResult.disallow();
                        // 无法被透视的职业（小透明/秉烛人/雇佣兵/捣蛋鬼）
                        if (isTargetInvisibleToInstinct(targetPlayer)) {
                            return TrueFalseAndCustomResult.disallow();
                        }
                        if (component.isCandleLit(targetPlayer.getUUID())) {
                            return TrueFalseAndCustomResult.custom(ModRoles.CANDLE_BEARER.color());
                        }
                        return TrueFalseAndCustomResult.custom(Color.GRAY.getRGB());
                    }
                    return TrueFalseAndCustomResult.pass();
                });
        // 丘比特
        RoleInstinctEvents.OBSERVER_HIGHLIGHT_EVENT.register(ModRoles.CUPID_ID, (client, self, target, hasInstinct) -> {
            {
                if (!hasInstinct)
                    return TrueFalseAndCustomResult.pass();
                if (target instanceof Player targetPlayer) {
                    if (WorldModifierComponent.KEY.get(targetPlayer.level()).isModifier(targetPlayer,
                            SEModifiers.LOVERS)
                            || LoversComponent.KEY.get(targetPlayer).isLover())
                        return TrueFalseAndCustomResult.custom(Color.ORANGE.getRGB());
                    return TrueFalseAndCustomResult.custom(ModRoles.CUPID.color());
                }
            }
            return TrueFalseAndCustomResult.pass();
        });

        // 疫使：透视所有玩家，被感染者显示橙色边框
        RoleInstinctEvents.OBSERVER_HIGHLIGHT_EVENT.register(ModRoles.INFECTED_ID, (client, self, target, hasInstinct) -> {
            {
                if (!hasInstinct)
                    return TrueFalseAndCustomResult.pass();
                if (target instanceof Player targetPlayer) {
                    // 无法被透视的职业（小透明/秉烛人/雇佣兵/捣蛋鬼）
                    if (isTargetInvisibleToInstinct(targetPlayer)) {
                        return TrueFalseAndCustomResult.disallow();
                    }
                    // 检查目标玩家是否被感染（非疫使角色的玩家被感染）
                    InfectedPlayerComponent infectedComponent = ModComponents.INFECTED.get(targetPlayer);
                    if (infectedComponent != null && infectedComponent.infectedTicks > 0) {
                        // 被感染者显示橙色边框
                        return TrueFalseAndCustomResult.custom(Color.ORANGE.getRGB());
                    }
                    // 其他玩家显示疫使的颜色
                    return TrueFalseAndCustomResult.custom(ModRoles.INFECTED.color());
                }
                return TrueFalseAndCustomResult.pass();
            }
        });
        // 雇佣兵合约目标已在下面保留
        RoleInstinctEvents.OBSERVER_HIGHLIGHT_EVENT.register(ModRoles.MERCENARY_ID,
                (client, self, target, hasInstinct) -> {
                    var mercComp = ModComponents.MERCENARY.get(self);
                    if (mercComp == null || !mercComp.contractActive)
                        return TrueFalseAndCustomResult.pass();
                    if (target instanceof Player targetPlayer
                            && targetPlayer.getUUID().equals(mercComp.contractTargetUuid))
                        return TrueFalseAndCustomResult.custom(ModRoles.MERCENARY.color());
                    if (target instanceof PlayerBodyEntity body && body.getPlayerUuid() != null
                            && body.getPlayerUuid().equals(mercComp.contractTargetUuid))
                        return TrueFalseAndCustomResult.custom(ModRoles.MERCENARY.color());
                    return TrueFalseAndCustomResult.pass();
                });

        // 初学者
        RoleInstinctEvents.OBSERVER_HIGHLIGHT_EVENT.register(SERoles.INITIATE.identifier(),
                (client, self, target, hasInstinct) -> {
                    if (SREItemUtils.countItem(self, TMMItems.KNIFE) <= 0)
                        return TrueFalseAndCustomResult.pass();
                    if (target instanceof Player targetPlayer
                            && SREClient.gameComponent.isRole(targetPlayer, SERoles.INITIATE))
                        return TrueFalseAndCustomResult.custom(SERoles.INITIATE.color());
                    return TrueFalseAndCustomResult.pass();
                });

        // 纵火犯
        RoleInstinctEvents.OBSERVER_HIGHLIGHT_EVENT.register(SERoles.ARSONIST.identifier(),
                (client, self, target, hasInstinct) -> {
                    if (!hasInstinct)
                        return TrueFalseAndCustomResult.pass();
                    if (!(target instanceof Player targetPlayer)) {
                        return TrueFalseAndCustomResult.pass();
                    }
                    if (targetPlayer.distanceToSqr(self) > 40 * 40)
                        return TrueFalseAndCustomResult.disallow();
                    var douse = DousedPlayerComponent.KEY.get(targetPlayer);
                    return douse.getDoused() ? TrueFalseAndCustomResult.custom(SERoles.ARSONIST.color())
                            : TrueFalseAndCustomResult.custom(Color.GRAY.getRGB());
                });
        // 推理者：无法透视玩家
        RoleInstinctEvents.OBSERVER_HIGHLIGHT_EVENT.register(ModRoles.REASONER_ID,
                (client, viewer, target, isInstinctEnabled) -> {
                    if (GameUtils.isPlayerAliveAndSurvival(viewer) && target instanceof Player) {
                        return TrueFalseAndCustomResult.disallow();
                    }
                    return TrueFalseAndCustomResult.pass();
                });

        // 布谷鸟：无法透视玩家；可以透视自己的蛋
        RoleInstinctEvents.OBSERVER_HIGHLIGHT_EVENT.register(ModRoles.CUCKOO_ID,
                (client, viewer, target, isInstinctEnabled) -> {
                    if (!GameUtils.isPlayerAliveAndSurvival(viewer))
                        return TrueFalseAndCustomResult.pass();
                    if (target instanceof Player) {
                        return TrueFalseAndCustomResult.disallow();
                    }
                    if (target instanceof Display.BlockDisplay blockDisplay) {
                        if (!isInstinctEnabled)
                            return TrueFalseAndCustomResult.pass();
                        try {
                            if (CuckooEggData.isOwnEggClient(blockDisplay)) {
                                return TrueFalseAndCustomResult.custom(ModRoles.CUCKOO.color());
                            }
                        } catch (Exception ignored) {
                        }
                    }
                    return TrueFalseAndCustomResult.pass();
                });

        // 红尘客
        RoleInstinctEvents.OBSERVER_HIGHLIGHT_EVENT.register(ModRoles.WAYFARER_ID,
                (client, viewer, target, isInstinctEnabled) -> {
                    if (target instanceof Player targetPlayer && GameUtils.isPlayerAliveAndSurvival(targetPlayer)) {
                        var wayC = WayfarerPlayerComponent.KEY.get(viewer);
                        if (wayC.phase == 1 && wayC.killer != null && targetPlayer.getUUID().equals(wayC.killer)) {
                            return TrueFalseAndCustomResult.custom(Color.RED.getRGB());
                        }
                    }
                    return TrueFalseAndCustomResult.pass();
                });

        // JOJO
        RoleInstinctEvents.OBSERVER_HIGHLIGHT_EVENT.register(ModRoles.JOJO_ID,
                (client, viewer, target, isInstinctEnabled) -> {
                    if (target instanceof Player targetPlayer && GameUtils.isPlayerAliveAndSurvival(targetPlayer)) {
                        if (targetPlayer.distanceTo(viewer) <= 3) {
                            if (SREClient.gameComponent.isRole(targetPlayer, ModRoles.DIO)) {
                                if (viewer.hasEffect(ModEffects.SKILL_BANED))
                                    return TrueFalseAndCustomResult.pass();
                                return TrueFalseAndCustomResult.custom(ModRoles.DIO.color());
                            }
                        }
                    }
                    return TrueFalseAndCustomResult.pass();
                });

        // 更好的义警：背水一战激活时蓝色
        RoleInstinctEvents.OBSERVER_HIGHLIGHT_EVENT.register(ModRoles.BETTER_VIGILANTE_ID,
                (client, viewer, target, isInstinctEnabled) -> {
                    var betterC = BetterVigilantePlayerComponent.KEY.get(viewer);
                    if (betterC.lastStandActivated) {
                        return TrueFalseAndCustomResult.custom(Color.BLUE.getRGB());
                    }
                    return TrueFalseAndCustomResult.pass();
                });

        // 帕秋莉 -> 芙兰朵露
        RoleInstinctEvents.OBSERVER_HIGHLIGHT_EVENT.register(RedHouseRoles.PACHURI_ID,
                (client, viewer, target, isInstinctEnabled) -> {
                    if (viewer.hasEffect(ModEffects.SAFE_TIME))
                        return TrueFalseAndCustomResult.pass();
                    if (target instanceof Player targetPlayer && target.distanceToSqr(viewer) <= 25) {
                        if (SREClient.gameComponent.isRole(targetPlayer, RedHouseRoles.FURANDORU)) {
                            return TrueFalseAndCustomResult.custom(RedHouseRoles.FURANDORU.color());
                        }
                    }
                    return TrueFalseAndCustomResult.pass();
                });

        // 厨师
        RoleInstinctEvents.OBSERVER_HIGHLIGHT_EVENT.register(ModRoles.CHEF_ID,
                (client, viewer, target, isInstinctEnabled) -> {
                    if (viewer.hasEffect(ModEffects.SAFE_TIME))
                        return TrueFalseAndCustomResult.pass();
                    if (target instanceof Player targetPlayer) {
                        int t = FoodDrinkGlowComponent.KEY.get(viewer).glowTicks
                                .getOrDefault(targetPlayer.getScoreboardName(), new HashMap<>())
                                .getOrDefault(1, 0);
                        if (t > 0)
                            return TrueFalseAndCustomResult.custom(Color.GREEN.getRGB());
                    }
                    return TrueFalseAndCustomResult.pass();
                });

        // 酒保
        RoleInstinctEvents.OBSERVER_HIGHLIGHT_EVENT.register(ModRoles.BARTENDER_ID,
                (client, viewer, target, isInstinctEnabled) -> {
                    if (viewer.hasEffect(ModEffects.SAFE_TIME))
                        return TrueFalseAndCustomResult.pass();
                    if (target instanceof Player targetPlayer) {
                        var armorComp = SREArmorPlayerComponent.KEY.get(targetPlayer);
                        var poisonComp = SREPlayerPoisonComponent.KEY.get(targetPlayer);
                        var weakArmorComp = io.wifi.starrailexpress.cca.SREWeakArmorPlayerComponent.KEY
                                .get(targetPlayer);
                        boolean hasWeakArmor = weakArmorComp != null && weakArmorComp.getWeakArmor() > 0;
                        var targetRole = SREClient.gameComponent.getRole(targetPlayer);

                        if (armorComp.getArmor() > 0 && poisonComp.poisonTicks > 0)
                            return TrueFalseAndCustomResult.custom(new Color(186, 255, 65).getRGB());
                        if ((armorComp.getArmor() > 0 || hasWeakArmor) &&
                                (targetRole == null || !targetRole.identifier().equals(ModRoles.WATCHER_ID)))
                            return TrueFalseAndCustomResult.custom(Color.BLUE.getRGB());
                        if (poisonComp.poisonTicks > 0)
                            return TrueFalseAndCustomResult.custom(Color.RED.getRGB());
                        int t = FoodDrinkGlowComponent.KEY.get(viewer).glowTicks
                                .getOrDefault(targetPlayer.getScoreboardName(), new HashMap<>())
                                .getOrDefault(0, 0);
                        if (t > 0)
                            return TrueFalseAndCustomResult.custom(Color.GREEN.getRGB());
                    }
                    return TrueFalseAndCustomResult.pass();
                });

        // 毒师/酒保看中毒玩家红色
        RoleInstinctEvents.OBSERVER_HIGHLIGHT_EVENT.register(ModRoles.POISONER_ID,
                (client, viewer, target, isInstinctEnabled) -> {
                    if (target instanceof Player targetPlayer) {
                        if (SREPlayerPoisonComponent.KEY.get(targetPlayer).poisonTicks > 0)
                            return TrueFalseAndCustomResult.custom(Color.RED.getRGB());
                    }
                    return TrueFalseAndCustomResult.pass();
                });

        // 刽子手
        RoleInstinctEvents.OBSERVER_HIGHLIGHT_EVENT.register(ModRoles.EXECUTIONER_ID,
                (client, viewer, target, isInstinctEnabled) -> {
                    ExecutionerPlayerComponent comp = ExecutionerPlayerComponent.KEY.get(viewer);
                    if (comp != null && comp.target != null && target instanceof Player targetPlayer) {
                        if (comp.target.equals(targetPlayer.getUUID())
                                && !SREClient.gameComponent.isRole(targetPlayer, ModRoles.GHOST))
                            return TrueFalseAndCustomResult.custom(new Color(0, 254, 254).getRGB());
                    }
                    return TrueFalseAndCustomResult.pass();
                });

        // 操纵者
        RoleInstinctEvents.OBSERVER_HIGHLIGHT_EVENT.register(ModRoles.MANIPULATOR_ID,
                (client, viewer, target, isInstinctEnabled) -> {
                    ManipulatorPlayerComponent comp = ManipulatorPlayerComponent.KEY.get(viewer);
                    if (comp != null && comp.target != null && target instanceof Player targetPlayer) {
                        if (comp.target.equals(targetPlayer.getUUID()))
                            return TrueFalseAndCustomResult.custom(Color.orange.getRGB());
                    }
                    return TrueFalseAndCustomResult.pass();
                });

        // 爱慕者（绑定目标）
        RoleInstinctEvents.OBSERVER_HIGHLIGHT_EVENT.register(ModRoles.ADMIRER_ID,
                (client, viewer, target, isInstinctEnabled) -> {
                    AdmirerPlayerComponent comp = AdmirerPlayerComponent.KEY.get(viewer);
                    if (comp != null && comp.getBoundTarget() != null && target instanceof Player targetPlayer) {
                        if (comp.getBoundTarget().getUUID().equals(targetPlayer.getUUID())) {
                            if (isInstinctEnabled) {
                                return TrueFalseAndCustomResult.custom(Color.CYAN.getRGB());
                            }
                            return TrueFalseAndCustomResult.custom(Color.PINK.getRGB());
                        }
                    }
                    return TrueFalseAndCustomResult.pass();
                });

        // 监视者
        RoleInstinctEvents.OBSERVER_HIGHLIGHT_EVENT.register(ModRoles.MONITOR_ID,
                (client, viewer, target, isInstinctEnabled) -> {
                    MonitorPlayerComponent comp = MonitorPlayerComponent.KEY.get(viewer);
                    if (comp != null && comp.getMarkedTarget() != null && target instanceof Player targetPlayer) {
                        if (comp.getMarkedTarget().equals(targetPlayer.getUUID()))
                            return TrueFalseAndCustomResult.custom(Color.CYAN.getRGB());
                    }
                    return TrueFalseAndCustomResult.pass();
                });

        // 鹈鹕
        RoleInstinctEvents.OBSERVER_HIGHLIGHT_EVENT.register(ModRoles.PELICAN_ID,
                (client, viewer, target, isInstinctEnabled) -> {
                    if (!isInstinctEnabled)
                        return TrueFalseAndCustomResult.pass();
                    if (target instanceof Player targetPlayer) {
                        double distSq = targetPlayer.distanceToSqr(viewer);
                        if (distSq > PelicanPlayerComponent.INSTINCT_RANGE * PelicanPlayerComponent.INSTINCT_RANGE)
                            return TrueFalseAndCustomResult.disallow();
                        PelicanPlayerComponent comp = PelicanPlayerComponent.KEY.get(viewer);
                        if (comp != null && comp.uniqueEaten.contains(targetPlayer.getUUID()))
                            return TrueFalseAndCustomResult.custom(Color.ORANGE.getRGB());
                        return TrueFalseAndCustomResult.custom(ModRoles.PELICAN.color());
                    }
                    return TrueFalseAndCustomResult.pass();
                });

        // 芙兰朵露
        RoleInstinctEvents.OBSERVER_HIGHLIGHT_EVENT.register(RedHouseRoles.FURANDORU_ID,
                (client, viewer, target, isInstinctEnabled) -> {
                    if (target instanceof Player targetPlayer) {
                        var targetRole = SREClient.gameComponent.getRole(targetPlayer);
                        if (RoleUtils.compareRole(targetRole, RedHouseRoles.PACHURI))
                            return TrueFalseAndCustomResult.custom(RedHouseRoles.PACHURI.color());
                        if (isInstinctEnabled)
                            return TrueFalseAndCustomResult.custom(new Color(2, 224, 2).getRGB());
                    }
                    return TrueFalseAndCustomResult.pass();
                });

        // 傀儡师
        RoleInstinctEvents.OBSERVER_HIGHLIGHT_EVENT.register(ModRoles.PUPPETEER_ID,
                (client, viewer, target, isInstinctEnabled) -> {
                    var comp = ModComponents.PUPPETEER.get(viewer);
                    if (comp.isControllingPuppet && SREClient.isPlayerAliveAndInSurvivalIgnoreShitSplit())
                        return TrueFalseAndCustomResult.custom(ModRoles.PUPPETEER.color());
                    if (comp.isPuppeteerMarked && SREClient.isPlayerAliveAndInSurvivalIgnoreShitSplit()
                            && comp.phase >= 1)
                        return TrueFalseAndCustomResult.pass();
                    return TrueFalseAndCustomResult.pass();
                });

        // 冤魂刺客
        RoleInstinctEvents.OBSERVER_HIGHLIGHT_EVENT.register(ModRoles.WRAITH_ASSASSIN_ID,
                (client, viewer, target, isInstinctEnabled) -> {
                    if (!isInstinctEnabled)
                        return TrueFalseAndCustomResult.pass();
                    if (target instanceof Player targetPlayer && targetPlayer != viewer
                            && SREClient.isPlayerAliveAndInSurvival()) {
                        int san = Math.round(SREPlayerMoodComponent.KEY.get(targetPlayer).getMood() * 100.0f);
                        if (san < 10)
                            return TrueFalseAndCustomResult.custom(0x2C8DFF);
                        if (san < 30)
                            return TrueFalseAndCustomResult.custom(0xFFD84A);
                    }
                    return TrueFalseAndCustomResult.pass();
                });

        // 记录员
        RoleInstinctEvents.OBSERVER_HIGHLIGHT_EVENT.register(ModRoles.RECORDER_ID,
                (client, viewer, target, isInstinctEnabled) -> {
                    if (!isInstinctEnabled)
                        return TrueFalseAndCustomResult.pass();
                    if (target instanceof Player targetPlayer) {
                        if (targetPlayer.distanceToSqr(viewer) > 20 * 20 || targetPlayer == viewer)
                            return TrueFalseAndCustomResult.disallow();
                        var recorder = ModComponents.RECORDER.get(viewer);
                        if (recorder.getGuesses().containsKey(targetPlayer.getUUID()))
                            return TrueFalseAndCustomResult.custom(0xFFFF55);
                        return TrueFalseAndCustomResult.custom(0x0000AA);
                    }
                    return TrueFalseAndCustomResult.pass();
                });

        // // 小丑/亡命徒系列
        // RoleInstinctEvents.OBSERVER_HIGHLIGHT_EVENT.register(ModRoles.JESTER_ID,
        // (client, viewer, target, isInstinctEnabled) -> {
        // if (!SREClient.isPlayerAliveAndInSurvival())
        // return TrueFalseAndCustomResult.pass();
        // if (target instanceof Player targetPlayer) {
        // if (SREClient.gameComponent.isRole(targetPlayer, ModRoles.GHOST))
        // return TrueFalseAndCustomResult.disallow();
        // return TrueFalseAndCustomResult.custom(Color.PINK.getRGB());
        // }
        // return TrueFalseAndCustomResult.pass();
        // });

        // 家族本能（为所有家族职业注册通用逻辑）
        for (var familyRole : ModRoles.getAllMafiaRoles()) { // 假设有方法获取所有家族职业 ID
            RoleInstinctEvents.OBSERVER_HIGHLIGHT_EVENT.register(familyRole.getIdentifier(),
                    (client, viewer, target, isInstinctEnabled) -> {
                        if (!SREClient.isPlayerAliveAndInSurvival())
                            return TrueFalseAndCustomResult.pass();
                        if (!isInstinctEnabled)
                            return TrueFalseAndCustomResult.pass();
                        var selfRole = SREClient.gameComponent.getRole(viewer);
                        if (selfRole == null || !selfRole.isMafiaTeam())
                            return TrueFalseAndCustomResult.pass();
                        if (target instanceof Player targetPlayer) {
                            var targetRole = SREClient.gameComponent.getRole(targetPlayer);
                            if (targetRole != null && targetRole.isMafiaTeam()) {
                                if (SREClient.gameComponent.isRole(targetPlayer, ModRoles.GODFATHER))
                                    return TrueFalseAndCustomResult.custom(new Color(135, 206, 235).getRGB());
                                return TrueFalseAndCustomResult.custom(new Color(139, 69, 19).getRGB());
                            }
                            if (viewer.distanceTo(targetPlayer) > 20.0D)
                                return TrueFalseAndCustomResult.disallow();
                        }
                        return TrueFalseAndCustomResult.pass();
                    });
        }

        // --- 以下从 registerSpecialLogic 迁移过来的角色相关逻辑 ---
        // 皮革噶的：疯魔模式高亮周围玩家
        RoleInstinctEvents.OBSERVER_HIGHLIGHT_EVENT.register(ModRoles.LEATHER_PIG_ID,
                (client, viewer, target, isInstinctEnabled) -> {
                    if (!(target instanceof Player targetPlayer))
                        return TrueFalseAndCustomResult.pass();
                    var comp = LeatherPigPlayerComponent.KEY.maybeGet(viewer).orElse(null);
                    if (comp == null || !comp.isFrenzyActive())
                        return TrueFalseAndCustomResult.pass();
                    if (targetPlayer == viewer)
                        return TrueFalseAndCustomResult.pass();
                    if (isTargetInvisibleToInstinct(targetPlayer))
                        return TrueFalseAndCustomResult.disallow();
                    if (targetPlayer.distanceToSqr(viewer) > LeatherPigPlayerComponent.INSTINCT_RANGE
                            * LeatherPigPlayerComponent.INSTINCT_RANGE)
                        return TrueFalseAndCustomResult.pass();
                    return TrueFalseAndCustomResult.custom(Color.RED.getRGB());
                });

        // 鬼眼·杨间：扫描期间白色轮廓
        RoleInstinctEvents.OBSERVER_HIGHLIGHT_EVENT.register(ModRoles.GHOST_EYE_ID,
                (client, viewer, target, isInstinctEnabled) -> {
                    if (!(target instanceof Player targetPlayer))
                        return TrueFalseAndCustomResult.pass();
                    if (!isGhostEyeRole(viewer) || !isGhostEyeScanActive(viewer))
                        return TrueFalseAndCustomResult.pass();
                    if (targetPlayer == viewer)
                        return TrueFalseAndCustomResult.pass();
                    if (targetPlayer.distanceToSqr(viewer) > GhostEyePlayerComponent.SCAN_RADIUS
                            * GhostEyePlayerComponent.SCAN_RADIUS)
                        return TrueFalseAndCustomResult.pass();
                    return TrueFalseAndCustomResult.custom(Color.WHITE.getRGB());
                });

        // 渡鸦
        RoleInstinctEvents.OBSERVER_HIGHLIGHT_EVENT.register(ModRoles.RAVEN_ID,
                (client, viewer, target, isInstinctEnabled) -> {
                    if (!(target instanceof Player) || !isInstinctEnabled)
                        return TrueFalseAndCustomResult.pass();
                    var raven = ModComponents.RAVEN.get(viewer);
                    if (raven.isHunting())
                        return TrueFalseAndCustomResult.custom(Color.WHITE.getRGB());
                    if (viewer.distanceTo(target) <= 10.0)
                        return TrueFalseAndCustomResult.custom(Color.WHITE.getRGB());
                    return TrueFalseAndCustomResult.disallow();
                });

        // 记者（近距离死亡时间渐变）
        RoleInstinctEvents.OBSERVER_HIGHLIGHT_EVENT.register(ModRoles.AWESOME_BINGLUS_ID,
                (client, viewer, target, isInstinctEnabled) -> {
                    if (!(target instanceof Player targetPlayer))
                        return TrueFalseAndCustomResult.pass();
                    if (targetPlayer.isInvisibleTo(viewer))
                        return TrueFalseAndCustomResult.pass();
                    if (targetPlayer.distanceTo(viewer) <= 5) {
                        var awpc = AwesomePlayerComponent.KEY.get(targetPlayer);
                        if (awpc.nearByDeathTime <= 1)
                            return TrueFalseAndCustomResult.pass();
                        int redDepth = (int) (255 * ((float) awpc.nearByDeathTime
                                / (float) AwesomePlayerComponent.nearByDeathTimeRecordTime));
                        redDepth = Math.clamp(redDepth, 0, 255);
                        return TrueFalseAndCustomResult.custom(new Color(redDepth, 0, 0).getRGB());
                    }
                    return TrueFalseAndCustomResult.pass();
                });

        // 侦探（共谋者高亮）
        RoleInstinctEvents.OBSERVER_HIGHLIGHT_EVENT.register(ModRoles.AGENT_ID,
                (client, viewer, target, isInstinctEnabled) -> {
                    if (!(target instanceof Player targetPlayer))
                        return TrueFalseAndCustomResult.pass();
                    if (!SREClient.gameComponent.isRole(targetPlayer, ModRoles.CONSPIRATOR))
                        return TrueFalseAndCustomResult.pass();
                    var awpc = AgentPlayerComponent.KEY.get(viewer);
                    if (awpc.conspiratorInstinctTime <= 0)
                        return TrueFalseAndCustomResult.pass();
                    return TrueFalseAndCustomResult.custom(ModRoles.AGENT.color());
                });

        // 愚者（异端目标）
        RoleInstinctEvents.OBSERVER_HIGHLIGHT_EVENT.register(ModRoles.THE_FOOL_ID,
                (client, viewer, target, isInstinctEnabled) -> {
                    if (!(target instanceof Player targetPlayer))
                        return TrueFalseAndCustomResult.pass();
                    var comp = FoolPlayerComponent.KEY.get(viewer);
                    if (comp.hereticTarget == null || !comp.hereticTarget.equals(targetPlayer.getUUID()))
                        return TrueFalseAndCustomResult.pass();
                    return TrueFalseAndCustomResult.custom(0xF2C56A);
                });
    }

    // ---------- 被看高亮（TARGET） ----------
    private static void registerTargetHighlights() {

        RoleInstinctEvents.TARGET_HIGHLIGHT_EVENT.register(ModRoles.MERCENARY_ID,
                (client, self, target, hasInstinct) -> {
                    if (SREClient.gameComponent.isKillerTeam(self)) {
                        return TrueFalseAndCustomResult.disallow();
                    }
                    return TrueFalseAndCustomResult.pass();
                });
        // 对明星的高亮
        RoleInstinctEvents.TARGET_HIGHLIGHT_EVENT.register(ModRoles.SUPERSTAR_ID,
                (client, self, target, hasInstinct) -> {
                    if (!(target instanceof Player targetPlayer))
                        return TrueFalseAndCustomResult.pass();
                    var stack = MCItemsUtils.getFirstMatchedItem(self, it -> it.getItem() instanceof SignedPaperItem);
                    if (stack != null) {
                        String owner = stack.getOrDefault(SREDataComponentTypes.OWNER, "NULL");
                        if (targetPlayer.getScoreboardName().equals(owner))
                            return TrueFalseAndCustomResult.custom(Color.WHITE.getRGB());
                    }
                    return TrueFalseAndCustomResult.pass();
                });

        // 小透明：杀手无法看到
        RoleInstinctEvents.TARGET_HIGHLIGHT_EVENT.register(ModRoles.GHOST_ID,
                (client, viewer, target, isInstinctEnabled) -> {
                    if (SREClient.gameComponent != null && isKillerTeam(SREClient.gameComponent.getRole(viewer))
                            && SREClient.isPlayerAliveAndInSurvival())
                        return TrueFalseAndCustomResult.disallow();
                    return TrueFalseAndCustomResult.pass();
                });
        // 秉烛人：杀手无法透视
        RoleInstinctEvents.TARGET_HIGHLIGHT_EVENT.register(ModRoles.CANDLE_BEARER_ID,
                (client, viewer, target, isInstinctEnabled) -> {
                    if (SREClient.gameComponent != null && isKillerTeam(SREClient.gameComponent.getRole(viewer))
                            && SREClient.isPlayerAliveAndInSurvival())
                        return TrueFalseAndCustomResult.disallow();
                    return TrueFalseAndCustomResult.pass();
                });

        // 怀旧者（里世界）：杀手无法透视
        RoleInstinctEvents.TARGET_HIGHLIGHT_EVENT.register(ModRoles.NOSTALGIST_ID,
                (client, viewer, target, isInstinctEnabled) -> {
                    if (target instanceof Player targetPlayer && targetPlayer.hasEffect(ModEffects.NOSTALGIST_BACKWORLD)
                            && SREClient.gameComponent != null && isKillerTeam(SREClient.gameComponent.getRole(viewer))
                            && SREClient.isPlayerAliveAndInSurvival())
                        return TrueFalseAndCustomResult.disallow();
                    return TrueFalseAndCustomResult.pass();
                });

        // 黑白熊（熊形态）：对所有人隐藏
        RoleInstinctEvents.TARGET_HIGHLIGHT_EVENT.register(ModRoles.MONOKUMA_ID,
                (client, viewer, target, isInstinctEnabled) -> {
                    if (target instanceof Player targetPlayer && MonokumaEventHandler.isMonokumaBearForm(targetPlayer)
                            && SREClient.isPlayerAliveAndInSurvival())
                        return TrueFalseAndCustomResult.disallow();
                    return TrueFalseAndCustomResult.pass();
                });

        // 咸鱼：隐身时无法被看
        RoleInstinctEvents.TARGET_HIGHLIGHT_EVENT.register(ModRoles.SALTED_FISH_ID,
                (client, viewer, target, isInstinctEnabled) -> {
                    if (target instanceof Player targetPlayer && targetPlayer.isInvisible())
                        return TrueFalseAndCustomResult.disallow();
                    return TrueFalseAndCustomResult.pass();
                });
    }

    // ---------- 通用兜底逻辑 ----------
    public static void registerNormalLogic() {
        CommonInstinctEvents.ALIVE_COMMON_BEFORE_EVENT.register((self, target, hasInstinct) -> {
            if (target == null)
                return TrueFalseAndCustomResult.pass();
            if (target instanceof SaltedFishBodyEntity)
                return TrueFalseAndCustomResult.disallow();
            // 通用隐身与不可透视保护
            if (target instanceof Player targetPlayer && isTargetInvisibleToInstinct(targetPlayer))
                return TrueFalseAndCustomResult.disallow();
            // 低 SAN 非杀手玩家看到冤魂高亮（全局效果）
            if (target instanceof Player targetPlayer && targetPlayer != self) {
                if (SREClient.gameComponent.isRole(targetPlayer, ModRoles.WRAITH_ASSASSIN)) {
                    SRERole selfRole = SREClient.gameComponent != null ? SREClient.gameComponent.getRole(self) : null;
                    if (selfRole != null && !selfRole.isKiller()) {
                        int viewerSan = Math.round(SREPlayerMoodComponent.KEY.get(self).getMood() * 100.0f);
                        if (viewerSan < 40) {
                            return TrueFalseAndCustomResult.custom(0xAA66FF);
                        }
                    }
                }
            }

            // 殡仪员：透视物品实体
            if (SREClient.gameComponent != null && SREClient.gameComponent.isRole(self, ModRoles.MORTICIAN)) {
                if (target instanceof ItemEntity itemEntity) {
                    double dx = self.getX() - itemEntity.getX();
                    double dz = self.getZ() - itemEntity.getZ();
                    double dy = self.getY() - itemEntity.getY();
                    if (Math.sqrt(dx * dx + dz * dz) <= 10.0 && Math.abs(dy) <= 3.0)
                        return TrueFalseAndCustomResult.custom(getGradientColor(itemEntity.getId()));
                }
            }

            // 记者便签
            if (SREClient.gameComponent != null && SREClient.gameComponent.isRole(self, ModRoles.AWESOME_BINGLUS)) {
                if (target instanceof io.wifi.starrailexpress.content.entity.NoteEntity note)
                    return TrueFalseAndCustomResult.custom(getGradientColor(note.getId()));
            }

            return TrueFalseAndCustomResult.pass();
        });

        // 保留旁观通用事件
        CommonInstinctEvents.SPECTATOR_COMMON_EVENT.register((self, target, hasInstinct) -> {
            if (!hasInstinct)
                return TrueFalseAndCustomResult.pass();
            if (target instanceof Player player
                    && RoleUtils.isPlayerTheModifier(player, SpecialGameModeModifiers.TNT_TAGGED))
                return TrueFalseAndCustomResult.custom(Color.RED.getRGB());
            return TrueFalseAndCustomResult.pass();
        });
    }

    // 特殊逻辑中保留不直接属于职业的全局事件
    public static void registerSpecialLogic() {
        TouhouInstincts.registerEvents();

        // 鬼祟修饰符
        CommonInstinctEvents.ALIVE_COMMON_BEFORE_EVENT.register((self, target, hasInstinct) -> {
            if (!(target instanceof Player targetPlayer) || SREClient.gameComponent == null)
                return TrueFalseAndCustomResult.pass();
            WorldModifierComponent mods = WorldModifierComponent.KEY.get(targetPlayer.level());
            if (mods != null && mods.isModifier(targetPlayer.getUUID(), TraitorAndModifiers.SNEAKY)) {
                if (self.distanceTo(targetPlayer) <= 8.0)
                    return TrueFalseAndCustomResult.disallow();
            }
            return TrueFalseAndCustomResult.pass();
        });

        // 游戏模式特殊处理
        CommonInstinctEvents.ALIVE_COMMON_BEFORE_EVENT.register((self, target, hasInstinct) -> {
            if (!(target instanceof Player targetPlayer) || !SREClient.gameComponent.isRunning())
                return TrueFalseAndCustomResult.pass();
            if (SREClient.gameComponent.gameMode.identifier.equals(SREGameModes.HIDE_AND_SEEK_MODE.identifier)) {
                if (SREClient.gameComponent.isKillerTeam(self)) {
                    if (SREClient.gameComponent.isKillerTeam(targetPlayer))
                        return TrueFalseAndCustomResult.custom(TMMRoles.KILLER.color());
                } else if (self.hasEffect(ModEffects.SAFE_TIME) && SREClient.gameComponent.isKillerTeam(targetPlayer)) {
                    return TrueFalseAndCustomResult.custom(TMMRoles.KILLER.color());
                }
                if (self.hasEffect(MobEffects.GLOWING))
                    return TrueFalseAndCustomResult.custom(TMMRoles.VIGILANTE.color());
                return TrueFalseAndCustomResult.disallow();
            }
            if (SREClient.gameComponent.gameMode.identifier.equals(SREGameModes.TNT_TAG_MODE.identifier)) {
                if (SREClient.modifierComponent.isModifier(targetPlayer, SpecialGameModeModifiers.TNT_TAGGED))
                    return TrueFalseAndCustomResult.custom(SpecialGameModeModifiers.TNT_TAGGED.color());
                return TrueFalseAndCustomResult.custom(TMMRoles.CIVILIAN.color());
            }
            return TrueFalseAndCustomResult.pass();
        });

        // 恋人
        CommonInstinctEvents.ALIVE_COMMON_MIDDLE_EVENT.register((self, target, hasInstinct) -> {
            if (!(target instanceof Player)
                    || !WorldModifierComponent.KEY.get(self.level()).isModifier(self, SEModifiers.LOVERS))
                return TrueFalseAndCustomResult.pass();
            var lc = LoversComponent.KEY.get(self);
            if (lc.getLover() != null && lc.getLover().equals(target.getUUID()))
                return TrueFalseAndCustomResult.custom(SEModifiers.LOVERS.color());
            return TrueFalseAndCustomResult.pass();
        });
    }

    // ---------- 工具方法 ----------
    private static boolean isKillerTeam(SRERole role) {
        return SREClient.gameComponent != null && SREClient.gameComponent.isKillerTeamRole(role);
    }

    private static boolean isGhostEyeRole(Player self) {
        if (self == null || SREClient.gameComponent == null)
            return false;
        SRERole role = SREClient.gameComponent.getRole(self);
        return role != null && role.identifier().equals(ModRoles.GHOST_EYE_ID);
    }

    private static boolean isGhostEyeScanActive(Player self) {
        if (self == null || self.level() == null)
            return false;
        GhostEyePlayerComponent comp = GhostEyePlayerComponent.KEY.maybeGet(self).orElse(null);
        if (comp != null && comp.revealTicks > 0)
            return true;
        int intervalTicks = GameConstants.getInTicks(0, NoellesRolesConfig.HANDLER.instance().ghostEyeScanInterval);
        if (intervalTicks <= 0)
            return false;
        return self.level().getGameTime() % intervalTicks < GhostEyePlayerComponent.REVEAL_TICKS;
    }

    public static boolean isTargetInvisibleToInstinct(Player target) {
        if (SREClient.gameComponent == null || target == null)
            return false;
        return SREClient.gameComponent.isRole(target, ModRoles.GHOST)
                || SREClient.gameComponent.isRole(target, ModRoles.CANDLE_BEARER)
                || SREClient.gameComponent.isRole(target, ModRoles.PRANKSTER)
                || SREClient.gameComponent.isRole(target, ModRoles.GAMBLER);
    }

    // 渐变颜色相关（同原文件）
    private static final int[] GRADIENT_COLORS = {
            new Color(255, 0, 0).getRGB(),
            new Color(255, 85, 0).getRGB(),
            new Color(255, 170, 0).getRGB(),
            new Color(255, 255, 0).getRGB(),
            new Color(255, 170, 0).getRGB(),
            new Color(255, 85, 0).getRGB(),
    };
    private static final int GRADIENT_CYCLE = 60;

    public static int getGradientColor(int tickOffset) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null)
            return GRADIENT_COLORS[0];
        long worldTime = client.level.getGameTime();
        int cyclePosition = (int) ((worldTime + tickOffset) % GRADIENT_CYCLE);
        float progress = (float) cyclePosition / GRADIENT_CYCLE * GRADIENT_COLORS.length;
        int colorIndex = (int) progress;
        float blend = progress - colorIndex;
        int currentColor = GRADIENT_COLORS[colorIndex % GRADIENT_COLORS.length];
        int nextColor = GRADIENT_COLORS[(colorIndex + 1) % GRADIENT_COLORS.length];
        return blendColors(currentColor, nextColor, blend);
    }

    public static int blendColors(int color1, int color2, float blend) {
        int r1 = (color1 >> 16) & 0xFF, g1 = (color1 >> 8) & 0xFF, b1 = color1 & 0xFF;
        int r2 = (color2 >> 16) & 0xFF, g2 = (color2 >> 8) & 0xFF, b2 = color2 & 0xFF;
        int r = (int) (r1 + (r2 - r1) * blend);
        int g = (int) (g1 + (g2 - g1) * blend);
        int b = (int) (b1 + (b2 - b1) * blend);
        return (r << 16) | (g << 8) | b;
    }
}