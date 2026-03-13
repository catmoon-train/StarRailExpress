package org.agmas.noellesroles.mixin.client.roles.coroner;

import io.wifi.starrailexpress.api.Role;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.StarGameWorldComponent;
import io.wifi.starrailexpress.cca.StarPlayerMoodComponent;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.client.gui.RoleNameRenderer;
import io.wifi.starrailexpress.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.game.GameConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.CommonColors;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import pro.fazeclan.river.stupid_express.constants.SEModifiers;
import pro.fazeclan.river.stupid_express.modifier.split_personality.cca.SplitPersonalityComponent;

import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.noellesroles.client.NoellesrolesClient;
import org.agmas.noellesroles.component.StarAbilityPlayerComponent;
import org.agmas.noellesroles.component.InsaneKillerPlayerComponent;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.roles.coroner.BodyDeathReasonComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RoleNameRenderer.class)
public abstract class CoronerHudMixin {

    @Shadow
    private static float nametagAlpha;

    @Shadow
    private static Component nametag;

    @Inject(method = "renderHud", at = @At("TAIL"))
    private static void coronerRoleNameRenderer(Font renderer, LocalPlayer player, GuiGraphics context,
            DeltaTracker tickCounter, CallbackInfo ci) {
        StarGameWorldComponent gameWorldComponent = (StarGameWorldComponent) StarGameWorldComponent.KEY.get(player.level());

        if (NoellesrolesClient.targetFakeBody != null) {
            if (gameWorldComponent.isRole(Minecraft.getInstance().player, ModRoles.CORONER)
                    || gameWorldComponent.isRole(Minecraft.getInstance().player, ModRoles.VULTURE)
                    || SREClient.isPlayerSpectatingOrCreative()) {
                context.pose().pushPose();
                context.pose().translate((float) context.guiWidth() / 2.0F, (float) context.guiHeight() / 2.0F + 6.0F,
                        0.0F);
                context.pose().scale(0.6F, 0.6F, 1.0F);
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
                StarPlayerMoodComponent moodComponent = (StarPlayerMoodComponent) StarPlayerMoodComponent.KEY
                        .get(Minecraft.getInstance().player);
                if (moodComponent.isLowerThanMid() && SREClient.isPlayerAliveAndInSurvival()) {
                    Component name = Component.translatable("hud.coroner.sanity_requirements");
                    context.drawString(renderer, name, -renderer.width(name) / 2, 32, CommonColors.YELLOW);
                    context.pose().popPose();
                    return;
                }

                Role role = gameWorldComponent.getRole(NoellesrolesClient.targetFakeBody);
                if (role == null)
                    role = TMMRoles.CIVILIAN;
                Component roleInfo = Component.translatable("hud.coroner.role_info").withColor(CommonColors.RED)
                        .append(Component.translatable("announcement.star.role." + role.identifier().getPath())
                                .withColor(role.color()));
                if (hasPenalty) {
                    roleInfo = Component.translatable("message.noellesroles.penalty.limit")
                            .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC);
                }
                context.drawString(renderer, roleInfo, -renderer.width(roleInfo) / 2, 48, CommonColors.WHITE);

                context.pose().popPose();
                return;
            }
        }

        if (NoellesrolesClient.targetBody != null) {
            if (gameWorldComponent.isRole(Minecraft.getInstance().player, ModRoles.CORONER)
                    || gameWorldComponent.isRole(Minecraft.getInstance().player, ModRoles.VULTURE)
                    || gameWorldComponent.isRole(Minecraft.getInstance().player, ModRoles.WAYFARER)
                    || gameWorldComponent.isRole(Minecraft.getInstance().player, ModRoles.DIO)
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
                context.pose().scale(0.6F, 0.6F, 1.0F);
                StarPlayerMoodComponent moodComponent = (StarPlayerMoodComponent) StarPlayerMoodComponent.KEY
                        .get(Minecraft.getInstance().player);
                if (moodComponent.isLowerThanMid() && SREClient.isPlayerAliveAndInSurvival()) {
                    // Text name = Text.literal("50% sanity required to use ability");
                    Component name = Component.translatable("hud.coroner.sanity_requirements");
                    context.drawString(renderer, name, -renderer.width(name) / 2, 32, CommonColors.YELLOW);
                    return;
                }
                BodyDeathReasonComponent bodyDeathReasonComponent = (BodyDeathReasonComponent) BodyDeathReasonComponent.KEY
                        .get(NoellesrolesClient.targetBody);
                String deathReason_str = NoellesrolesClient.targetBody.getDeathReason();
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

                MutableComponent name = Component
                        .translatable("hud.coroner.death_info", NoellesrolesClient.targetBody.tickCount / 20)
                        .append(deathText);
                boolean vultured = bodyDeathReasonComponent.vultured;
                final var worldModifiers = WorldModifierComponent.KEY.get(Minecraft.getInstance().player.level());
                if (worldModifiers != null) {
                    if (worldModifiers.isModifier(NoellesrolesClient.targetBody.getPlayerUuid(),
                            SEModifiers.SECRETIVE)) {
                        vultured = true;
                    }
                }
                if (vultured) {
                    name = Component.literal("abcdefghijklmnopqrstuvwxyzaa").withStyle(ChatFormatting.OBFUSCATED);
                }
                if (hasPenalty) {
                    name = Component.translatable("message.noellesroles.penalty.limit.death");
                }
                context.drawString(renderer, name, -renderer.width(name) / 2, 32, CommonColors.RED);
                Role foundRole = TMMRoles.CIVILIAN;
                for (Role role : TMMRoles.ROLES.values()) {
                    if (role.identifier().equals(bodyDeathReasonComponent.playerRole))
                        foundRole = role;
                }
                if ((SREClient.isPlayerSpectatingOrCreative()
                        || gameWorldComponent.isRole(Minecraft.getInstance().player, ModRoles.CORONER))
                        && !bodyDeathReasonComponent.vultured) {
                    Component roleInfo = Component.translatable("hud.coroner.role_info").withColor(CommonColors.RED)
                            .append(Component
                                    .translatable("announcement.star.role." + bodyDeathReasonComponent.playerRole.getPath())
                                    .withColor(foundRole.color()));
                    if (hasPenalty) {
                        roleInfo = Component.translatable("message.noellesroles.penalty.limit.role");
                    }
                    context.drawString(renderer, roleInfo, -renderer.width(roleInfo) / 2, 48, CommonColors.WHITE);
                }
                if (gameWorldComponent.isRole(Minecraft.getInstance().player, ModRoles.VULTURE)) {
                    if (bodyDeathReasonComponent.vultured) {
                        Component roleInfo = Component.translatable("hud.vulture.already_consumed")
                                .withColor(ModRoles.VULTURE.color());
                        context.drawString(renderer, roleInfo, -renderer.width(roleInfo) / 2, 48, CommonColors.WHITE);
                    } else {
                        StarAbilityPlayerComponent abilityPlayerComponent = StarAbilityPlayerComponent.KEY
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
                if (gameWorldComponent.isRole(Minecraft.getInstance().player, ModRoles.DIO)) {
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

    @Inject(method = "renderHud", at = @At(value = "INVOKE", target = "Lio/wifi/starrailexpress/game/GameFunctions;isPlayerSpectatingOrCreative(Lnet/minecraft/world/entity/player/Player;)Z"))
    private static void customRaycast(Font renderer, LocalPlayer player, GuiGraphics context, DeltaTracker tickCounter,
            CallbackInfo ci) {
        float range = RoleNameRenderer.getPlayerRange(player);
        HitResult line = ProjectileUtil.getHitResultOnViewVector(player,
                (entity) -> entity instanceof PlayerBodyEntity || entity instanceof Player, (double) range);
        NoellesrolesClient.targetBody = null;
        NoellesrolesClient.targetFakeBody = null;

        if (line instanceof EntityHitResult ehr) {
            if (ehr.getEntity() instanceof PlayerBodyEntity playerBodyEntity) {
                NoellesrolesClient.targetBody = playerBodyEntity;
            } else if (ehr.getEntity() instanceof Player targetPlayer) {
                InsaneKillerPlayerComponent component = InsaneKillerPlayerComponent.KEY.get(targetPlayer);
                if (component.isActive) {
                    NoellesrolesClient.targetFakeBody = targetPlayer;
                }
            }
        }
    }
}
