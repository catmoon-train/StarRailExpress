package org.agmas.noellesroles.client.hud;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.PlayerBodyEntityComponent;
import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.client.util.ClientSkinCache;
import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.event.client.OnRenderRoleName;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.utils.client.betterrender.FakeGuiGraphics;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.CommonColors;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.noellesroles.client.NoellesrolesClient;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.game.roles.killer.insane_killer.InsaneKillerPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.utils.RoleUtils;
import pro.fazeclan.river.stupid_express.constants.SEModifiers;
import pro.fazeclan.river.stupid_express.modifier.split_personality.cca.SplitPersonalityComponent;

import java.util.UUID;

public class PlayerBodyHud {
    public static PlayerBodyEntity targetBody;
    public static Player targetFakeBody;
    public static Player hudTarget;
    /** 迟滞状态标记：防止理智在阈值附近波动时页面闪烁 */
    private static boolean coronerWasShowingWarning = false;

    /**
     * 使用迟滞效应检查是否应该显示理智不足警告
     * - 理智 < 0.50：显示警告
     * - 理智 >= 0.60：不显示警告
     * - 理智在 0.50 ~ 0.60 之间：维持上一次状态，防止闪烁
     */
    private static boolean shouldShowSanityWarning(SREPlayerMoodComponent moodComponent, boolean playerAlive) {
        if (!playerAlive)
            return false;
        float mood = moodComponent.getMood();
        if (mood < 0.50f) {
            coronerWasShowingWarning = true;
            return true;
        }
        if (mood >= 0.60f) {
            coronerWasShowingWarning = false;
            return false;
        }
        // 理智在 0.50~0.60 之间，维持上一次状态
        return coronerWasShowingWarning;
    }

    private static void renderCoronerHud(Font renderer, Player player, FakeGuiGraphics context,
            DeltaTracker tickCounter) {
        SREGameWorldComponent gameWorldComponent = (SREGameWorldComponent) SREGameWorldComponent.KEY
                .get(player.level());

        if (targetFakeBody != null) {
            SRERole selfrole = SREClient.getCachedPlayerRole();
            boolean canSeeBody = false;
            if (selfrole != null && selfrole.canSeeBodyDeathReason(SREClient.cached_player))
                canSeeBody = true;
            if (canSeeBody
                    || SREClient.isPlayerSpectatingOrCreative()) {
                context.pose().pushPose();
                context.pose().translate((float) context.guiWidth() / 2.0F, (float) context.guiHeight() / 2.0F + 6.0F,
                        0.0F);
                context.pose().scale(SREClient.bodyHUDScale, SREClient.bodyHUDScale, 1.0F);
                // 死亡惩罚
                boolean hasPenalty = ModComponents.DEATH_PENALTY.get(Minecraft.getInstance().player).hasPenalty();

                final var worldModifierComponent = WorldModifierComponent.KEY
                        .get(player.level());
                if (worldModifierComponent != null) {
                    if (worldModifierComponent.isModifier(player, SEModifiers.SPLIT_PERSONALITY)) {
                        var splitComponent = SplitPersonalityComponent.KEY.get(player);
                        if (splitComponent != null && player.isSpectator() && !splitComponent.isDeath()) {
                            hasPenalty = true;
                        }
                    }
                }
                SREPlayerMoodComponent moodComponent = (SREPlayerMoodComponent) SREPlayerMoodComponent.KEY
                        .get(Minecraft.getInstance().player);
                if (shouldShowSanityWarning(moodComponent, SREClient.isPlayerAliveAndInSurvival())) {
                    Component name = Component.translatable("hud.coroner.sanity_requirements");
                    context.drawString(renderer, name, -renderer.width(name) / 2, 32, CommonColors.YELLOW);
                    context.pose().popPose();
                    return;
                }

                SRERole role = gameWorldComponent.getRole(targetFakeBody);
                if (role == null)
                    role = TMMRoles.CIVILIAN;
                Component roleInfo = Component.translatable("hud.coroner.role_info").withColor(CommonColors.RED)
                        .append(RoleUtils.getRoleName(role.identifier()).copy().withColor(role.color()));
                if (hasPenalty) {
                    roleInfo = Component.translatable("message.noellesroles.penalty.limit")
                            .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC);
                }
                context.drawString(renderer, roleInfo, -renderer.width(roleInfo) / 2, 48, CommonColors.WHITE);

                context.pose().popPose();
                return;
            }
        }

        if (targetBody != null) {
            ForensicHud.renderCorpse(renderer, player, targetBody, context, tickCounter);
            SRERole selfrole = SREClient.getCachedPlayerRole();
            boolean canSeeBody = false;
            if (selfrole != null && selfrole.canSeeBodyDeathReason(SREClient.cached_player))
                canSeeBody = true;
            if (canSeeBody
                    || SREClient.isPlayerSpectatingOrCreative()) {
                var deathPenalty = ModComponents.DEATH_PENALTY.get(Minecraft.getInstance().player);
                boolean hasPenalty = false;
                if (deathPenalty != null)
                    hasPenalty = deathPenalty.hasPenalty();

                final var worldModifierComponent = WorldModifierComponent.KEY
                        .get(player.level());
                if (worldModifierComponent != null) {
                    if (worldModifierComponent.isModifier(player, SEModifiers.SPLIT_PERSONALITY)) {
                        var splitComponent = SplitPersonalityComponent.KEY.get(player);
                        if (splitComponent != null && player.isSpectator() && !splitComponent.isDeath()) {
                            hasPenalty = true;
                        }
                    }
                }

                context.pose().pushPose();
                context.pose().translate((float) context.guiWidth() / 2.0F, (float) context.guiHeight() / 2.0F + 6.0F,
                        0.0F);
                context.pose().scale(SREClient.bodyHUDScale, SREClient.bodyHUDScale, 1.0F);
                SREPlayerMoodComponent moodComponent = (SREPlayerMoodComponent) SREPlayerMoodComponent.KEY
                        .get(Minecraft.getInstance().player);
                if (shouldShowSanityWarning(moodComponent, SREClient.isPlayerAliveAndInSurvival())) {
                    Component name = Component.translatable("hud.coroner.sanity_requirements");
                    context.drawString(renderer, name, -renderer.width(name) / 2, 32, CommonColors.YELLOW);
                    context.pose().popPose();
                    return;
                }
                PlayerBodyEntityComponent bodyDeathReasonComponent = (PlayerBodyEntityComponent) PlayerBodyEntityComponent.KEY
                        .get(targetBody);

                // 检查是否是葬仪伪造的尸体
                if (bodyDeathReasonComponent.isFakeBody) {
                    // 显示"伪造的尸体"
                    Component fakeBodyName = Component.translatable("hud.coroner.fake_body")
                            .withColor(ModRoles.MORTICIAN_BODYMAKER.color());
                    context.drawString(renderer, fakeBodyName, -renderer.width(fakeBodyName) / 2, 32, CommonColors.RED);
                    context.pose().popPose();
                    return;
                }

                String deathReason_str = targetBody.getDeathReason();
                if (deathReason_str.isBlank() || deathReason_str.isEmpty()) {
                    deathReason_str = GameConstants.DeathReasons.GENERIC.toString();
                }
                ResourceLocation deathReason = ResourceLocation
                        .tryParse(deathReason_str);
                if (deathReason == null) {
                    deathReason = GameConstants.DeathReasons.GENERIC;
                }
                Component deathText = Component
                        .translatable("death_reason." + deathReason.toLanguageKey());
                if (BuiltInRegistries.ITEM.containsKey(deathReason)) {
                    var it = BuiltInRegistries.ITEM.get(deathReason);
                    if (it != null) {
                        deathText = it.getDescription();
                    }
                }
                String victimName = bodyDeathReasonComponent.getOwnerName();
                MutableComponent nameMessage = Component.empty();
                if (victimName != null && !victimName.isBlank()) {
                    nameMessage = Component
                            .translatable("hud.coroner.death_info.name",
                                    victimName);
                }
                MutableComponent deathMessage = Component
                        .translatable("hud.coroner.death_info.new", targetBody.tickCount / 20, deathText);
                boolean vultured = bodyDeathReasonComponent.vultured;
                final var worldModifiers = WorldModifierComponent.KEY.get(Minecraft.getInstance().player.level());
                if (worldModifiers != null) {
                    if (worldModifiers.isModifier(targetBody.getPlayerUuid(),
                            SEModifiers.SECRETIVE)) {
                        vultured = true;
                    }
                }
                if (vultured) {
                    nameMessage = Component.empty();
                    deathMessage = Component.literal("abcdefghijklmnopqrstuvwxyzaa")
                            .withStyle(ChatFormatting.OBFUSCATED);
                }
                if (hasPenalty) {
                    nameMessage = Component.empty();
                    deathMessage = Component.translatable("message.noellesroles.penalty.limit.death");
                }
                {
                    boolean canSeeBodyName = SREClient.isPlayerSpectatingOrCreative()
                            || selfrole.canSeeBodyName();
                    if (canSeeBodyName) {
                        context.drawString(renderer, nameMessage, -renderer.width(nameMessage) / 2, 16,
                                CommonColors.RED);
                    }
                }
                context.drawString(renderer, deathMessage, -renderer.width(deathMessage) / 2, 32, CommonColors.RED);
                SRERole foundRole = TMMRoles.CIVILIAN;
                for (SRERole role : TMMRoles.ROLES.values()) {
                    if (role.identifier().equals(bodyDeathReasonComponent.playerRole))
                        foundRole = role;
                }
                if ((SREClient.isPlayerSpectatingOrCreative()
                        || selfrole.canSeeBodyRoleInfo(SREClient.cached_player))
                        && !bodyDeathReasonComponent.vultured) {
                    Component roleInfo = Component.translatable("hud.coroner.role_info").withColor(CommonColors.RED)
                            .append(RoleUtils.getRoleName(bodyDeathReasonComponent.playerRole).copy()
                                    .withColor(foundRole.color()));
                    if (hasPenalty) {
                        roleInfo = Component.translatable("message.noellesroles.penalty.limit.role");
                    }
                    context.drawString(renderer, roleInfo, -renderer.width(roleInfo) / 2, 48, CommonColors.WHITE);
                }
                boolean showBodyKiller = (SREClient.isPlayerSpectatingOrCreative() || selfrole.canSeeBodyKiller());
                if (showBodyKiller && !hasPenalty) {
                    Component killerName = Component.translatable("sre.general.unknown");
                    UUID killerId = targetBody.getKillerUuid();
                    if (killerId != null) {
                        var b = ClientSkinCache.getCachedPlayerInfo(killerId);
                        if (b != null) {
                            killerName = Component.literal(b.getProfile().getName());
                        }
                    }
                    Component roleInfo;
                    if (killerId != null) {
                        roleInfo = Component.translatable("hud.coroner.body.killer", killerName)
                                .withColor(CommonColors.RED);
                    } else {
                        roleInfo = Component.translatable("hud.coroner.body.no_killer")
                                .withColor(CommonColors.RED);
                    }
                    context.drawString(renderer, roleInfo, -renderer.width(roleInfo) / 2, 64, CommonColors.WHITE);
                }
                UUID conspiratorEvidenceId = bodyDeathReasonComponent.getConspiratorEvidenceUuid();
                if (conspiratorEvidenceId != null && !bodyDeathReasonComponent.vultured && !hasPenalty) {
                    var conspiratorName = Component.translatable("sre.general.unknown");
                    var playerInfo = ClientSkinCache.getCachedPlayerInfo(conspiratorEvidenceId);
                    if (playerInfo != null) {
                        conspiratorName = Component.literal(playerInfo.getProfile().getName());
                    }
                    int evidenceY = showBodyKiller ? 80 : 64;
                    Component evidenceInfo = Component
                            .translatable("hud.coroner.body.conspirator_evidence", conspiratorName)
                            .withColor(ModRoles.CONSPIRATOR.color());
                    context.drawString(renderer, evidenceInfo, -renderer.width(evidenceInfo) / 2, evidenceY,
                            CommonColors.WHITE);
                }
                if (SREClient.isRole(ModRoles.VULTURE)) {
                    if (bodyDeathReasonComponent.vultured) {
                        Component roleInfo = Component.translatable("hud.vulture.already_consumed")
                                .withColor(ModRoles.VULTURE.color());
                        context.drawString(renderer, roleInfo, -renderer.width(roleInfo) / 2, 48, CommonColors.WHITE);
                    } else {
                        SREAbilityPlayerComponent abilityPlayerComponent = SREAbilityPlayerComponent.KEY
                                .get(player);
                        if (abilityPlayerComponent.cooldown <= 0 && SREClient.isPlayerAliveAndInSurvival()) {
                            Component roleInfo = Component
                                    .translatable("hud.vulture.eat",
                                            NoellesrolesClient.abilityBind.getTranslatedKeyMessage())
                                    .withColor(CommonColors.RED);
                            context.drawString(renderer, roleInfo, -renderer.width(roleInfo) / 2, 48,
                                    CommonColors.WHITE);
                        }
                    }
                }
                if (SREClient.isRole(ModRoles.DIO)) {
                    if (bodyDeathReasonComponent.vultured) {
                        Component roleInfo = Component.translatable("hud.vulture.already_consumed")
                                .withColor(ModRoles.VULTURE.color());
                        context.drawString(renderer, roleInfo, -renderer.width(roleInfo) / 2, 48, CommonColors.WHITE);
                    } else {

                        Component roleInfo = Component
                                .translatable("hud.dio.eat")
                                .withColor(CommonColors.RED);
                        context.drawString(renderer, roleInfo, -renderer.width(roleInfo) / 2, 48,
                                CommonColors.WHITE);

                    }
                }
                context.pose().popPose();
                return;
            }
        }
    }

    public static void registerEvents() {
        CustomRoleHud.registerEvents();
        OnRenderRoleName.RENDER_END.register((player, range, context,
                tickCounter, font) -> {
            HitResult line = ProjectileUtil.getHitResultOnViewVector(player,
                    (entity) -> entity instanceof PlayerBodyEntity || entity instanceof Player, (double) range);
            NoellesrolesClient.targetBody = null;
            NoellesrolesClient.targetPlayer = null;
            targetBody = null;
            targetFakeBody = null;
            if (line instanceof EntityHitResult ehr) {
                if (ehr.getEntity() instanceof PlayerBodyEntity playerBodyEntity) {
                    targetBody = playerBodyEntity;
                    NoellesrolesClient.targetBody = targetBody;// 用于秃鹫兼容
                } else if (ehr.getEntity() instanceof Player targetPlayer) {
                    NoellesrolesClient.targetPlayer = targetPlayer;
                    InsaneKillerPlayerComponent component = InsaneKillerPlayerComponent.KEY.get(targetPlayer);
                    if (component.isActive) {
                        targetFakeBody = targetPlayer;
                    }
                }
            }
            if (targetBody != null || targetFakeBody != null) {
                renderCoronerHud(font, player, context, tickCounter);
            }
        });

    }
}
