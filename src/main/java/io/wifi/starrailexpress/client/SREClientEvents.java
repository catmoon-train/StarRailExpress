/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.wifi.starrailexpress.client;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import io.wifi.starrailexpress.cca.SREPlayerPsychoComponent;
import io.wifi.starrailexpress.client.util.ClientSkinCache;
import io.wifi.starrailexpress.content.item.DisguiseEffectSync;
import io.wifi.starrailexpress.event.client.OnGameFinishedClient;
import io.wifi.starrailexpress.event.client.OnGameStartedClient;
import io.wifi.starrailexpress.event.client.OnRenderRoleName;
import io.wifi.starrailexpress.event.client.OnRenderRoleName.RenderPlayerNameInterface;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.util.TrueFalseAndCustomResult;
import io.wifi.starrailexpress.util.TrueFalseResult;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.PlayerTeam;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.noellesroles.ConfigWorldComponent;
import org.agmas.noellesroles.client.ClientAmonState;
import org.agmas.noellesroles.client.ClientEmbalmerState;
import org.agmas.noellesroles.client.ClientSkincrawlerState;
import org.agmas.noellesroles.client.NoellesrolesClient;
import org.agmas.noellesroles.client.hud.ForensicHud;
import org.agmas.noellesroles.client.hud.PlayerBodyHud;
import org.agmas.noellesroles.component.DeathPenaltyComponent;
import io.wifi.starrailexpress.api.data.RoleData;
import org.agmas.noellesroles.game.roles.neutral.pelican.PelicanManager;
import org.agmas.noellesroles.role_data.killer.MorphlingRoleData;
import org.agmas.noellesroles.role_data.innocence.MagicianRoleData;
import org.agmas.noellesroles.role_data.neutral.WayfarerRoleData;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.init.RoleShopHandler;
import org.agmas.noellesroles.role.BounsRoles;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role.touhou.THLostForestRoles;
import org.agmas.noellesroles.role.touhou.THRedHouseRoles;
import org.agmas.noellesroles.utils.RoleUtils;
import pro.fazeclan.river.stupid_express.constants.SEModifiers;
import pro.fazeclan.river.stupid_express.constants.SERoles;
import pro.fazeclan.river.stupid_express.modifier.split_personality.cca.SplitPersonalityComponent;
import pro.fazeclan.river.stupid_express.role.arsonist.cca.DousedPlayerComponent;
import pro.fazeclan.river.stupid_express.role.necromancer.cca.NecromancerComponent;

import java.util.UUID;

/**
 * SREClientEvents
 */
public class SREClientEvents {

    private static Component getDisplayName(PlayerInfo playerInfo) {
        MutableComponent mutableComponent = PlayerTeam.formatNameForTeam(playerInfo.getTeam(),
                Component.literal(playerInfo.getProfile().getName()));
        return mutableComponent;
    }

    private static UUID getShuffledTarget(Player player) {
        var worldModifiers = WorldModifierComponent.KEY.get(player.level());
        if (worldModifiers != null && worldModifiers.isModifier(player, SEModifiers.JEB_)) {
            return NoellesrolesClient.JEB_SHUFFLED_PLAYER_ENTRIES_CACHE.get(player.getUUID());
        }
        if (SREClient.moodComponent == null) {
            return null;
        }
        if (!NoellesrolesClient.SHUFFLED_PLAYER_ENTRIES_CACHE.containsKey(player.getUUID())) {
            return null;
        }
        if ((ConfigWorldComponent.KEY.get(player.level())).insaneSeesMorphs
                && SREClient.moodComponent.isLowerThanDepressed()) {
            return NoellesrolesClient.SHUFFLED_PLAYER_ENTRIES_CACHE.get(player.getUUID());
        }
        return null;
    }

    public static void registerClientEvents() {
        registerRoleNameRendererEvents();
        OnGameStartedClient.EVENT.register(() -> {
            NoellesrolesClient.clearTimeStopCache();
            if (!Minecraft.getInstance().isLocalServer()) {
                SRE.LOGGER.info("[CLIENT] Re-register shop entries.");
                RoleShopHandler.shopRegister();
            }
        });
        OnGameFinishedClient.EVENT.register(() -> {
            NoellesrolesClient.clearTimeStopCache();
            Minecraft.getInstance().getSoundManager().stop();
        });
    }

    public static void registerRoleNameRendererEvents() {
        PlayerBodyHud.registerEvents();
        ForensicHud.registerEvents();
        // 大小姐的仆从显示大小姐
        OnRenderRoleName.RENDER_PLAYER_ROLE.register((player, target, context, tickCounter, renderer) -> {
            if (target == null)
                return TrueFalseAndCustomResult.pass();
            if (!GameUtils.isPlayerAliveAndSurvival(player))
                return TrueFalseAndCustomResult.pass();
            if (SREClient.gameComponent != null) {
                if (SREClient.gameComponent.isRole(player, THRedHouseRoles.REMILIA_BLOOD_SERVANT)
                        && SREClient.gameComponent.isRole(target, THRedHouseRoles.REMILIA)) {
                    return TrueFalseAndCustomResult.custom(THRedHouseRoles.REMILIA.getDisplayNameWithColor());
                }
            }
            return TrueFalseAndCustomResult.pass();
        });
        // 魔术师
        // 显示职业
        OnRenderRoleName.RENDER_PLAYER_ROLE.register((player, target, context, tickCounter, renderer) -> {
            if (target == null)
                return null;
            if (SREClient.gameComponent != null) {
                var selfRole = SREClient.gameComponent.getRole(player);
                if (SREClient.gameComponent.isRole(target, ModRoles.MAGICIAN)
                        || SREClient.gameComponent.isRole(target, THLostForestRoles.KAGUYA)) {
                    var roleR = RoleData.getOptional(MagicianRoleData.class, target)
                            .map(MagicianRoleData::getDisguiseRoleId).orElse(null);
                    if (roleR == null) {
                        roleR = SERoles.NECROMANCER.getIdentifier();
                    }
                    if (SREClient.isPlayerSpectatingOrCreative()) {
                        return TrueFalseAndCustomResult.custom(Component.translatable(
                                "message.magician.magician_are_playing_as",
                                RoleUtils.getRoleNameWithColor(roleR)));
                    }
                    if (SREGameWorldComponent.isKillerTeamRoleStatic(selfRole) && selfRole.canSeeTeammateKillerRole()) {
                        return TrueFalseAndCustomResult.custom(RoleUtils.getRoleName(roleR));
                    }
                }
            }
            return TrueFalseAndCustomResult.pass();
        });
        // 显示同伙
        OnRenderRoleName.RENDER_PLAYER_COHORT.register((player, target, context, tickCounter, renderer) -> {
            if (target == null)
                return null;
            if (SREClient.gameComponent != null) {
                if (SREClient.gameComponent.isRole(player, THLostForestRoles.KAGUYA)) {
                    if (SREClient.gameComponent.isKillerTeam(target)) {
                        return TrueFalseAndCustomResult.allow();
                    }
                } else if (SREClient.gameComponent.isKillerTeam(player)) {
                    if (SREClient.gameComponent.isRole(target, ModRoles.MAGICIAN)
                            || SREClient.gameComponent.isRole(target, THLostForestRoles.KAGUYA)) {
                        return TrueFalseAndCustomResult.allow();
                    }
                }
            }
            return TrueFalseAndCustomResult.pass();
        });

        // 杂项
        OnRenderRoleName.RENDER_ALL.register((player, context, d, font) -> {
            // Penalty 直接啥也别看了
            if (DeathPenaltyComponent.hasPenalty(player))
                return TrueFalseResult.FALSE;
            if (SREClient.modifierComponent != null) {
                if (SREClient.modifierComponent.isModifier(player, SEModifiers.SPLIT_PERSONALITY)) {
                    var splitComponent = SplitPersonalityComponent.KEY.get(player);
                    if (splitComponent.isDeath()) {
                        return TrueFalseResult.FALSE;
                    }
                }
            }
            // 亡命徒也是
            if (RoleUtils.isPlayerTheJob(player, TMMRoles.LOOSE_END)
                    && GameUtils.isPlayerAliveAndSurvivalIgnoreShitSplit(player)) {
                return TrueFalseResult.FALSE;
            }
            // 鹈鹕肚内玩家不能通过准星查看玩家身份
            if (PelicanManager.isStashed(player))
                return TrueFalseResult.FALSE;
            if (DisguiseEffectSync.HAD_DISGUISE.getOrDefault(player.getUUID(), false)) {
                return TrueFalseResult.FALSE;
            }
            return TrueFalseResult.PASS;
        });
        // 低理智
        OnRenderRoleName.RENDER_PLAYER_NAME.register((player, target, c, d, f) -> {
            if (GameUtils.isPlayerAliveAndSurvival(player)) {
                if (SREPlayerMoodComponent.KEY.get(player).getMood() <= 0.4) {
                    // return TrueFalseAndCustomResult.custom(Component.empty());
                    return TrueFalseAndCustomResult.disallow();
                }
            }
            return TrueFalseAndCustomResult.pass();
        });
        // 狂暴模糊名字
        OnRenderRoleName.RENDER_PLAYER_NAME.register((player, target, c, d, f) -> {
            boolean shouldObfuscate = SREPlayerPsychoComponent.KEY.get(target).getPsychoTicks() > 0;
            if (shouldObfuscate) {
                return TrueFalseAndCustomResult
                        .custom(Component.literal("urscrewed" + "X".repeat(player.getRandom().nextInt(8)))
                                .withStyle(style -> style.applyFormats(ChatFormatting.OBFUSCATED,
                                        ChatFormatting.DARK_RED)));
            }
            return TrueFalseAndCustomResult.pass();
        });
        // 有DISGUISE效果的渡鸦不显示
        OnRenderRoleName.RENDER_PLAYER.register((player, target, context, delta, font) -> {
            if (RoleUtils.isPlayerTheJob(target, ModRoles.RAVEN)
                    && target.hasEffect(ModEffects.DISGUISE)) {
                return TrueFalseResult.FALSE;
            }
            return TrueFalseResult.PASS;
        });
        {
            // 被靠近查看时隐藏身份与杀手同伙（如迷失杀手）
            RenderPlayerNameInterface hideRoleInfoWhenSeen = (player, target, c, d, f) -> {
                // 此处可以直接用 SREClient.gameComponent，如果是null不会执行此event。
                var role = SREClient.gameComponent.getRole(target);
                if (role != null && role.isHideRoleInfoWhenSeen()) {
                    return TrueFalseAndCustomResult.disallow();
                }
                return TrueFalseAndCustomResult.pass();
            };
            OnRenderRoleName.RENDER_PLAYER_ROLE.register(hideRoleInfoWhenSeen);
            OnRenderRoleName.RENDER_PLAYER_COHORT.register(hideRoleInfoWhenSeen);
        }
        // 肉汁：本能提示只对杀手（isKiller）生效
        OnRenderRoleName.RENDER_PLAYER_EXTRA.register((player, target, context, delta, font) -> {
            // 此处可以直接用 SREClient.gameComponent，如果是null不会执行此event。
            SREGameWorldComponent component = SREClient.gameComponent;
            if (component.isRole(target, ModRoles.MEATBALL) && component.canUseKillerFeatures(player)) {
                // 显示肉汁提示
                context.pose().translate(0, 20 + font.lineHeight, 0);
                MutableComponent meatballTip = Component.translatable("game.tip.meatball_role");
                int meatballTipWidth = font.width(meatballTip);
                context.drawString(font, meatballTip, -meatballTipWidth / 2, 0,
                        Mth.color(1f, 0.5f, 0f) | ((int) (1 * 255) << 24));

                // 检查附近是否有其他玩家，如果有则显示无法攻击的提示
                boolean nearbyPlayers = false;
                for (Player nearbyPlayer : player.level().players()) {
                    if (nearbyPlayer != null && nearbyPlayer != target
                            && nearbyPlayer.distanceTo(target) <= 4.0D) {
                        nearbyPlayers = true;
                        break;
                    }
                }

                if (nearbyPlayers) {
                    // 无法在人群中攻击的提示
                    context.pose().translate(0, 20 + font.lineHeight, 0);
                    MutableComponent crowdTip = Component
                            .translatable("game.tip.meatball_cannot_attack_in_crowd");
                    int crowdTipWidth = font.width(crowdTip);
                    context.drawString(font, crowdTip, -crowdTipWidth / 2, 0,
                            Mth.color(1f, 0f, 0f) | ((int) (1 * 255) << 24));
                }
            }
        });

        // 阿蒙名字替换：夺舍后，其他玩家看到的名牌显示为被夺舍宿主的名字。
        OnRenderRoleName.RENDER_PLAYER_NAME.register((___player, target, ctx, delta, font) -> {
            UUID disguiseTarget = ClientAmonState.disguiseTargetFor(target.getUUID());
            if (disguiseTarget != null) {
                PlayerInfo targetInfo = ClientSkinCache.getCachedPlayerInfo(disguiseTarget);
                if (targetInfo != null && targetInfo.getProfile() != null && targetInfo.getProfile().getId() != null) {
                    return TrueFalseAndCustomResult.custom(getDisplayName(targetInfo));
                }
                if (disguiseTarget.equals(target.getUUID())) {
                    return TrueFalseAndCustomResult.custom(target.getDisplayName());
                }
            }
            return TrueFalseAndCustomResult.pass();
        });
        OnRenderRoleName.RENDER_PLAYER_NAME.register((___player, target, ctx, delta, font) -> {
            UUID stolenTarget = ClientSkincrawlerState.stolenSkinFor(target.getUUID());
            if (stolenTarget != null) {
                PlayerInfo targetInfo = ClientSkinCache.getCachedPlayerInfo(stolenTarget);
                if (targetInfo != null && targetInfo.getProfile() != null && targetInfo.getProfile().getId() != null) {
                    return TrueFalseAndCustomResult.custom(getDisplayName(targetInfo));
                }
                if (stolenTarget.equals(target.getUUID())) {
                    return TrueFalseAndCustomResult.custom(target.getDisplayName());
                }
            }
            return TrueFalseAndCustomResult.pass();
        });

        OnRenderRoleName.RENDER_PLAYER_NAME.register((___player, target, ctx, delta, font) -> {
            if (getShuffledTarget(target) != null) {
                return TrueFalseAndCustomResult.custom(Component.literal("??!?!").withStyle(ChatFormatting.OBFUSCATED));
            }
            // 嬉命人变装 - 鼠标朝向的玩家名字显示皮肤对应的名称
            UUID embalmerTarget = ClientEmbalmerState.replacement(target.getUUID());
            if (embalmerTarget != null && ClientEmbalmerState.isActive()) {
                PlayerInfo targetInfo = ClientSkinCache.getCachedPlayerInfo(embalmerTarget);
                if (targetInfo != null && targetInfo.getProfile() != null && targetInfo.getProfile().getId() != null) {
                    return TrueFalseAndCustomResult.custom(getDisplayName(targetInfo));
                }
            }
            var morphOpt = RoleData.getOptional(MorphlingRoleData.class, target);
            if (morphOpt.isPresent() && morphOpt.get().getMorphTicks() > 0) {
                var mocca = morphOpt.get();
                PlayerInfo targetInfo = ClientSkinCache.getCachedPlayerInfo(mocca.disguise);
                if (targetInfo != null && targetInfo.getProfile() != null && targetInfo.getProfile().getId() != null) {
                    return TrueFalseAndCustomResult.custom(getDisplayName(targetInfo));
                } else {
                    // Log.info(LogCategory.GENERAL, "Morphling disguise is null!!!");
                }
                if (mocca.disguise != null && mocca.disguise.equals(target.getUUID())) {
                    return TrueFalseAndCustomResult.custom(target.getDisplayName());
                }
            }
            return TrueFalseAndCustomResult.pass();
        });
        OnRenderRoleName.RENDER_END.register((player, range, context,
                tickCounter, font) -> {

            if (NoellesrolesClient.targetPlayer == null) {
                return;
            }

            if (SREClient.isPlayerSpectatingOrCreative()) {
                return;
            }
            if (SREClient.isRole(SERoles.ARSONIST)) {
                context.pose().pushPose();
                context.pose().translate(context.guiWidth() / 2.0f, context.guiHeight() / 2.0f + 6.0f, 0.0f);
                context.pose().scale(0.6f, 0.6f, 1.0f);

                DousedPlayerComponent component = DousedPlayerComponent.KEY.get(NoellesrolesClient.targetPlayer);
                Component status = Component
                        .translatable("hud.stupid_express.arsonist.doused." + component.getDoused());
                context.drawString(font, status, -font.width(status) / 2, 32,
                        component.getDoused() ? 0xfc9526 : java.awt.Color.GRAY.getRGB());

                context.pose().popPose();
            }
        });

        OnRenderRoleName.RENDER_END.register((player, range, context,
                tickCounter, font) -> {
            if (NoellesrolesClient.targetBody == null) {
                return;
            }

            if (SREClient.isPlayerSpectatingOrCreative()) {
                return;
            }
            if (SREClient.isRole(ModRoles.WAYFARER)) {
                var wayC = RoleData.getNullable(WayfarerRoleData.class, player);
                context.pose().pushPose();
                context.pose().translate(context.guiWidth() / 2.0f, context.guiHeight() / 2.0f + 24.0f, 0.0f);
                context.pose().scale(0.6f, 0.6f, 1.0f);
                if (wayC != null && wayC.phase != 0) {
                    context.pose().popPose();
                    return;
                }
                Component status = Component.translatable("hud.noellesroles.wayfarer.select");

                if (wayC != null && wayC.phase > 1) {
                    context.pose().popPose();
                    return;
                }
                context.drawString(font, status, -font.width(status) / 2, 32, 0x9457ff);

                context.pose().popPose();
            } else if (SREClient.isRole(SERoles.AMNESIAC)) {
                context.pose().pushPose();
                context.pose().translate(context.guiWidth() / 2.0f, context.guiHeight() / 2.0f + 6.0f, 0.0f);
                context.pose().scale(0.6f, 0.6f, 1.0f);

                Component status = Component.translatable("hud.stupid_express.amnesiac.select_body");
                context.drawString(font, status, -font.width(status) / 2, 32, 0x9baae8);
                context.pose().popPose();
            } else if (SREClient.isRole(SERoles.NECROMANCER)
                    || SREClient.isRole(BounsRoles.CAT_NECROMANCER)) {
                context.pose().pushPose();
                context.pose().translate(context.guiWidth() / 2.0f, context.guiHeight() / 2.0f + 6.0f, 0.0f);
                context.pose().scale(0.6f, 0.6f, 1.0f);

                Component status = Component.translatable("hud.stupid_express.necromancer.possible_revive");

                NecromancerComponent nc = NecromancerComponent.KEY.get(player.level());
                if (nc.getAvailableRevives() < 1) {
                    status = Component.translatable("hud.stupid_express.necromancer.no_possible_revive");
                }
                SREAbilityPlayerComponent cooldown = SREAbilityPlayerComponent.KEY.get(player);
                if (cooldown.hasCooldown()) {
                    status = Component.translatable("hud.stupid_express.necromancer.cooldown",
                            cooldown.getCooldown() / 20);
                }
                context.drawString(font, status, -font.width(status) / 2, 32, 0x9457ff);

                context.pose().popPose();

            }
        });
    }

}
