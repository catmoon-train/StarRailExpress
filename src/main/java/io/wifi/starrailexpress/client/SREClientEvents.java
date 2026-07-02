package io.wifi.starrailexpress.client;

import org.agmas.noellesroles.component.DeathPenaltyComponent;
import org.agmas.noellesroles.game.roles.neutral.pelican.PelicanManager;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.utils.RoleUtils;

import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import io.wifi.starrailexpress.cca.SREPlayerPsychoComponent;
import io.wifi.starrailexpress.content.item.DisguiseEffectSync;
import io.wifi.starrailexpress.event.client.OnRenderRoleName;
import io.wifi.starrailexpress.event.client.OnRenderRoleName.RenderPlayerNameInterface;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.util.TrueFalseAndCustomResult;
import io.wifi.starrailexpress.util.TrueFalseResult;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * SREClientEvents
 */
public class SREClientEvents {

    public static void registerClientEvents() {
        registerRoleNameRendererEvents();
    }

    public static void registerRoleNameRendererEvents() {
        // 杂项
        OnRenderRoleName.RENDER_ALL.register((player, context, d, font) -> {
            // Penalty 直接啥也别看了
            if (DeathPenaltyComponent.hasPenalty(player))
                return TrueFalseResult.FALSE;
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
            if (SREPlayerMoodComponent.KEY.get(player).getMood() <= 0.4) {
                // return TrueFalseAndCustomResult.custom(Component.empty());
                return TrueFalseAndCustomResult.disallow();
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
        {

            // 迷失杀手：杀手本能中不显示迷失杀手和杀手同伙信息
            RenderPlayerNameInterface lostKillerGeneral = (player, target, c, d, f) -> {
                // 此处可以直接用 SREClient.gameComponent，如果是null不会执行此event。
                if (SREClient.gameComponent.isRole(target, ModRoles.LOST_KILLER)) {
                    TrueFalseAndCustomResult.disallow();
                }
                return TrueFalseAndCustomResult.pass();
            };
            OnRenderRoleName.RENDER_PLAYER_ROLE.register(lostKillerGeneral);
            OnRenderRoleName.RENDER_PLAYER_COHORT.register(lostKillerGeneral);
        }
        // 肉汁：本能提示只对杀手（isKiller）生效
        OnRenderRoleName.RENDER_EXTRA.register((player, target, context, delta, font) -> {
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
    }
}
