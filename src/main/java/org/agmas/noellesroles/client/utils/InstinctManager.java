package org.agmas.noellesroles.client.utils;

import java.util.ArrayList;
import java.util.OptionalInt;

import org.agmas.noellesroles.content.entity.PuppeteerBodyEntity;
import org.agmas.noellesroles.game.roles.innocence.magician.MagicianPlayerComponent;
import org.agmas.noellesroles.game.roles.killer.ma_chen_xu.MaChenXuPlayerComponent;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role.touhou.RedHouseRoles;
import org.agmas.noellesroles.utils.RoleUtils;

import io.wifi.starrailexpress.api.InstinctType;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.SREGameTimeComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.content.entity.FirecrackerEntity;
import io.wifi.starrailexpress.content.entity.NoteEntity;
import io.wifi.starrailexpress.event.client.CommonInstinctEvents;
import io.wifi.starrailexpress.event.client.RoleInstinctEvents;
import io.wifi.starrailexpress.event.client.RoleInstinctEvents.InnerRoleInstinctFunction;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.util.Color;
import io.wifi.starrailexpress.util.TrueFalseAndCustomResult;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import pro.fazeclan.river.stupid_express.StupidExpress;
import pro.fazeclan.river.stupid_express.constants.SERoles;

/**
 * 高亮获取顺序：
 * <li>通用逻辑：{@link CommonInstinctEvents#ALIVE_COMMON_BEFORE_EVENT}</li>
 * <li>被看逻辑：{@link RoleInstinctEvents#TARGET_HIGHLIGHT_EVENT}</li>
 * <li>被看职业逻辑 {@link SRERole#setBeSeenInstinctType(InstinctType,InstinctType)}</li>
 * <li>看人逻辑：{@link RoleInstinctEvents#OBSERVER_HIGHLIGHT_EVENT}</li>
 * <li>看人职业逻辑 {@link SRERole#setInstinctType(InstinctType,InstinctType)}</li>
 * <li>通用逻辑：{@link CommonInstinctEvents#ALIVE_COMMON_AFTER_EVENT}</li>
 * <li>杀手直觉（杀手默认）</li>
 */
public class InstinctManager {
    /**
     * 使用新的OptionalInt.empty()代替-1，避免白色==-1的问题
     * 
     * @param target
     * @return
     */
    public static OptionalInt getInstinctHighlight(Entity target) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null || SREClient.gameComponent == null) {
            return OptionalInt.empty();
        }
        boolean instinctEnabled = SREClient.isInstinctEnabled();
        {
            int deathPenaltyType = SREClient.getDeathPenaltyType(client.player);
            if (deathPenaltyType == 1) {
                if (instinctEnabled)
                    return OptionalInt.of(Color.WHITE.getRGB());
                return OptionalInt.empty();
            } else if (deathPenaltyType == 2) {
                return OptionalInt.empty();
            }
        }
        var self = client.player;
        if (GameUtils.isPlayerAliveAndSurvival(self)) {
            var result = getCommonAliveInstinct(self, target, instinctEnabled, client);
            if (result.isFalse())
                return OptionalInt.empty();
            if (result.isCustom())
                return OptionalInt.of(result.getContent().orElse(-1));
        } else {
            var result = CommonInstinctEvents.SPECTATOR_COMMON_EVENT.invoker().getInstinctHighlight(self, target,
                    instinctEnabled);
            if (result.isCustom()) {
                int color = result.getContent().orElse(-1);
                return OptionalInt.of(color);
            } else if (result.isFalse()) {
                return OptionalInt.empty();
            }
        }
        if (!instinctEnabled) {
            return OptionalInt.empty();
        }
        SREGameWorldComponent gameWorldComponent = (SREGameWorldComponent) SREGameWorldComponent.KEY
                .get(Minecraft.getInstance().player.level());
        // if (target instanceof PlayerBodyEntity) return 0x606060;
        if (target instanceof ItemEntity || target instanceof NoteEntity || target instanceof FirecrackerEntity)
            return OptionalInt.of(0xDB9D00);
        // 渲染傀儡高亮
        if (target instanceof PuppeteerBodyEntity) {
            if (GameUtils.isPlayerSpectatingOrCreativeIgnoreShitSplit(Minecraft.getInstance().player)) {
                // new Color(181, 255, 231).getRGB()
                return OptionalInt.of(-4849689);
            }
        }
        if (target instanceof Player targetPlayer) {
            if (!(targetPlayer).isSpectator()) {
                if (GameUtils.isPlayerSpectatingOrCreativeIgnoreShitSplit(Minecraft.getInstance().player)) {
                    SRERole role = gameWorldComponent.getRole(targetPlayer);
                    if (role == null) {
                        return OptionalInt.of(TMMRoles.DISCOVERY_CIVILIAN.color());
                    } else {
                        return OptionalInt.of(role.color());
                    }
                } else {
                    return OptionalInt.of(TMMRoles.CIVILIAN.color());
                }

            }
        }
        return OptionalInt.empty();
    }

    public static TrueFalseAndCustomResult<Integer> getCommonAliveInstinct(LocalPlayer self, Entity target,
            boolean instinctEnabled, Minecraft client) {
        {
            var result = CommonInstinctEvents.ALIVE_COMMON_BEFORE_EVENT.invoker().getInstinctHighlight(self, target,
                    instinctEnabled);
            if (result.isCustom()) {
                int color = result.getContent().orElse(-1);
                return TrueFalseAndCustomResult.custom(color);
            } else if (result.isFalse()) {
                return TrueFalseAndCustomResult.disallow();
            }
        }

        SRERole selfRole = SREClient.gameComponent.getRole(self);
        SRERole targetRole = null;
        if (target instanceof Player targetPlayer) {
            targetRole = SREClient.gameComponent.getRole(targetPlayer);
            if (targetRole != null) {
                ArrayList<InnerRoleInstinctFunction> funcs = RoleInstinctEvents.TARGET_HIGHLIGHT_EVENT
                        .getFunctions(targetRole.getIdentifier());
                for (var f : funcs) {
                    var result = f.getInstinctHighlight(client, self, target, instinctEnabled);
                    if (result.isFalse()) {
                        return TrueFalseAndCustomResult.no();
                    } else if (result.isTrue()) {
                        break;
                    } else if (result.isCustom()) {
                        return result;
                    }
                }

                InstinctType targetInstinct = instinctEnabled ? targetRole.getToggledOnBeSeenInstinctType()
                        : targetRole.getToggledOffBeSeenInstinctType();
                targetInstinct = targetInstinct.getTrueInstinct(self, targetPlayer, selfRole, targetRole);
                if (targetInstinct.isNone()) {
                    return TrueFalseAndCustomResult.no();
                } else if (targetInstinct.isTargetRoleColor()) {
                    return TrueFalseAndCustomResult.custom(targetRole.getColor());
                } else if (targetInstinct.isObserverRoleColor()) {
                    if (selfRole == null)
                        return TrueFalseAndCustomResult.disallow();
                    return TrueFalseAndCustomResult.custom(selfRole.getColor());
                } else if (targetInstinct.isCustom()) {
                    return TrueFalseAndCustomResult.custom(targetInstinct.getColor());
                } else if (targetInstinct.isCustomWithFunction()) {
                    return TrueFalseAndCustomResult
                            .disallow();
                } else if (targetInstinct.isKillerInstinct()) {
                    return getDefaultKillerAliveInstinct(self, target, selfRole, targetRole);
                }
            }
        }
        if (selfRole == null)
            return TrueFalseAndCustomResult.pass();
        {
            ArrayList<InnerRoleInstinctFunction> funcs = RoleInstinctEvents.OBSERVER_HIGHLIGHT_EVENT
                    .getFunctions(selfRole.getIdentifier());
            for (var f : funcs) {
                var result = f.getInstinctHighlight(client, self, target, instinctEnabled);
                if (result.isFalse()) {
                    return TrueFalseAndCustomResult.no();
                } else if (result.isTrue()) {
                    break;
                } else if (result.isCustom()) {
                    return result;
                }
            }
            if (target instanceof Player targetPlayer) {
                InstinctType selfInstinct = instinctEnabled ? selfRole.getToggledOnInstinctType()
                        : selfRole.getToggledOffInstinctType();
                selfInstinct = selfInstinct.getTrueInstinct(self, targetPlayer, selfRole, targetRole);
                if (selfInstinct.isNone()) {
                    return TrueFalseAndCustomResult.no();
                } else if (selfInstinct.isTargetRoleColor()) {
                    if (targetRole == null)
                        return TrueFalseAndCustomResult.disallow();
                    return TrueFalseAndCustomResult.custom(targetRole.getColor());
                } else if (selfInstinct.isObserverRoleColor()) {
                    return TrueFalseAndCustomResult.custom(selfRole.getColor());
                } else if (selfInstinct.isCustom()) {
                    return TrueFalseAndCustomResult.custom(selfInstinct.getColor());
                } else if (selfInstinct.isCustomWithFunction()) {
                    return TrueFalseAndCustomResult.disallow();
                } else if (selfInstinct.isKillerInstinct()) {
                    return getDefaultKillerAliveInstinct(self, target, selfRole, targetRole);
                }
            }
        }
        {
            var result = CommonInstinctEvents.ALIVE_COMMON_AFTER_EVENT.invoker().getInstinctHighlight(self, target,
                    instinctEnabled);
            if (result.isCustom()) {
                int color = result.getContent().orElse(-1);
                return TrueFalseAndCustomResult.custom(color);
            } else if (result.isFalse()) {
                return TrueFalseAndCustomResult.disallow();
            }
        }

        // 杀手直觉，最后
        if (instinctEnabled && selfRole.isKillerTeam() && selfRole.canUseInstinct()) {
            return getDefaultKillerAliveInstinct(self, target, selfRole, targetRole);
        }

        return TrueFalseAndCustomResult.pass();
    }

    private static boolean isKillerTeam(SRERole role) {
        if (SREClient.gameComponent == null) {
            return false;
        }
        return SREClient.gameComponent.isKillerTeamRole(role);
    }

    private static int getRoleColor(SRERole target_role) {
        if (target_role == null)
            return TMMRoles.CIVILIAN.color();
        return target_role.color();
    }

    // 杀手直觉
    public static TrueFalseAndCustomResult<Integer> getDefaultKillerAliveInstinct(LocalPlayer self, Entity target,
            SRERole self_role, SRERole target_role) {
        if (self == null || target == null)
            return TrueFalseAndCustomResult.pass();
        if (!(target instanceof Player target_player)) {
            return TrueFalseAndCustomResult.pass();
        }
        if (self_role == null || target_role == null)
            return TrueFalseAndCustomResult.pass();
        // 布袋鬼：里世界期间无杀手直觉
        if (SREClient.gameComponent.isRole(self, ModRoles.MA_CHEN_XU)) {
            MaChenXuPlayerComponent macComp = MaChenXuPlayerComponent.KEY.get(self);
            if (macComp != null && macComp.otherworldActive) {
                return TrueFalseAndCustomResult.disallow();
            }
        }
        // 强盗直觉：只能透视半径10格内的玩家，透视杀手队友无距离限制
        if (SREClient.gameComponent.isRole(self, ModRoles.BANDIT)) {
            // 检查目标是否是杀手队友
            if (target_role != null && SREClient.gameComponent.isKillerTeamRole(target_role)) {
                // 杀手队友无距离限制
                // 迷失杀手不能看太远的
                if (RoleUtils.compareRole(target_role, ModRoles.LOST_KILLER)
                        && target_player.distanceTo(self) >= 10) {
                    return TrueFalseAndCustomResult.disallow();
                }
            } else {
                // 普通玩家只能透视10格内
                if (target_player.distanceTo(self) >= 10) {
                    return TrueFalseAndCustomResult.disallow();
                }
            }
        }

        // 魔术师：杀手看魔术师时显示红色边框（像看其他杀手一样）
        if (SREClient.gameComponent.isRole(target_player, ModRoles.MAGICIAN)) {
            target_role = RoleUtils
                    .getRole(MagicianPlayerComponent.KEY.get(target_player).getDisguiseRoleId());
        }

        if (RoleUtils.compareRole(target_role, ModRoles.PUPPETEER)) {
            return TrueFalseAndCustomResult.custom(ModRoles.PUPPETEER.color());
        }
        if (SREClient.gameComponent.isRole(self, ModRoles.COMMANDER)) {
            if (isKillerTeam(target_role)) {
                return TrueFalseAndCustomResult.custom(getRoleColor(target_role));
            }
            if (target_player.distanceTo(self) <= 5) {
                var role = SREClient.gameComponent.getRole(target_player);
                if (role != null && role.isVigilanteTeam()) {
                    return TrueFalseAndCustomResult.custom(new Color(63, 72, 204).getRGB());
                }
            }
        }
        if (RoleUtils.compareRole(target_role, ModRoles.VULTURE)) {
            return TrueFalseAndCustomResult.custom(ModRoles.VULTURE.color());
        }
        if (RoleUtils.compareRole(target_role, ModRoles.ADMIRER)) {
            return TrueFalseAndCustomResult.custom(ModRoles.ADMIRER.color());
        }
        if (RoleUtils.compareRole(target_role, ModRoles.EXECUTIONER)) {
            return TrueFalseAndCustomResult.custom(ModRoles.EXECUTIONER.color());
        }
        if (RoleUtils.compareRole(target_role, ModRoles.JESTER)) {
            return TrueFalseAndCustomResult.custom(Color.PINK.getRGB());
        }
        if (RoleUtils.compareRole(target_role, ModRoles.LOST_KILLER)) {
            return TrueFalseAndCustomResult.custom(TMMRoles.CIVILIAN.color());
        }
        if (RoleUtils.compareRole(target_role, ModRoles.PRANKSTER)) {
            return TrueFalseAndCustomResult.disallow();
        }
        if (RoleUtils.compareRole(target_role, SERoles.AMNESIAC)) {
            if (StupidExpress.CONFIG.rolesSection.amnesiacSection.amnesiacGlowsDifferently) {
                return TrueFalseAndCustomResult.custom(SERoles.AMNESIAC.color());
            }
        }

        if (SREClient.gameComponent.isRole(self, RedHouseRoles.REMILIA)) {
            if (!self.hasEffect(ModEffects.SAFE_TIME)) {
                if (target.distanceToSqr(self) <= 25) {
                    if (RoleUtils.compareRole(target_role, RedHouseRoles.PACHURI)) {
                        return TrueFalseAndCustomResult.custom(RedHouseRoles.PACHURI.color());
                    } else if (RoleUtils.compareRole(target_role, RedHouseRoles.FURANDORU)) {
                        return TrueFalseAndCustomResult.custom(RedHouseRoles.FURANDORU.color());
                    }
                }
            }
        }
        // 疫使：杀手本能中透视的框为深绿色
        if (SREClient.gameComponent.isRole(target_player, ModRoles.INFECTED)) {
            return TrueFalseAndCustomResult.custom(new Color(0, 100, 0).getRGB()); // 深绿色
        }
        // 葬仪：杀手本能中透视的框为淡灰色
        if (SREClient.gameComponent.isRole(target_player, ModRoles.MORTICIAN_BODYMAKER)) {
            return TrueFalseAndCustomResult.custom(new Color(180, 180, 180).getRGB()); // 淡灰色
        }
        // 肉汁：当杀手在4格范围内时，该杀手的透视框变为深蓝色
        if (RoleUtils.compareRole(target_role, ModRoles.MEATBALL)) {
            if (self.distanceTo(target_player) <= 4.0) {
                return TrueFalseAndCustomResult.custom(new Color(0, 0, 180).getRGB()); // 深蓝色
            }
        }

        // 默认fallback
        if (target_role == null)
            return TrueFalseAndCustomResult.custom(Color.WHITE.getRGB());
        if (target_role.canUseKiller()) {
            return TrueFalseAndCustomResult.custom(Color.RED.getRGB());
        } else if (target_role.isNeutralForKiller()) {
            return TrueFalseAndCustomResult.custom(Color.ORANGE.getRGB());
        } else {
            if (SREClient.gameComponent.isRole(self, ModRoles.MA_CHEN_XU)) {
                if (SREPlayerMoodComponent.KEY.get(target_player).getMood() <= 0.1) {
                    return TrueFalseAndCustomResult.custom(Color.CYAN.getRGB());
                }
            }
            if (SREClient.gameComponent.isRole(self, ModRoles.DIO)) {
                if (RoleUtils.compareRole(target_role, ModRoles.JOJO)) {
                    return TrueFalseAndCustomResult.custom(Color.CYAN.getRGB());
                }
            }
            if (SREGameTimeComponent.KEY.get(self.level()).getTime() >= GameConstants
                    .getFurandoruSafeLine()) {
                if (SREClient.gameComponent.isRole(target_player, RedHouseRoles.FURANDORU)) {
                    return TrueFalseAndCustomResult.disallow();
                }
            }
            if (SREClient.gameComponent.isRole(target_player, ModRoles.GAMBLER)) {
                return TrueFalseAndCustomResult.disallow();
            }
            return TrueFalseAndCustomResult.custom(TMMRoles.CIVILIAN.color());
        }

    }
}
