package org.agmas.noellesroles.handler.utils;

import org.agmas.noellesroles.role.bouns.BounsRoles;
import org.agmas.noellesroles.role.bouns.roles.BeeFamilyRole;
import org.agmas.noellesroles.utils.MoneyUtils;
import org.agmas.noellesroles.utils.RoleUtils;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.RoleSkill;
import io.wifi.starrailexpress.api.RoleSkill.RoleSkillContext;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.PlayerBodyEntityComponent;
import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerPoisonComponent;
import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.GameUtils.WinStatus;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;

public class BeeFamilyManager {

    public static final int BEE_QUEEN_IMPROVE_PRICE = 150;
    public static final int BEE_POISON_TICKS = 15 * 20;

    public static void registerEvents() {

        RoleSkill.register(BounsRoles.BEE_WORKER,
                RoleSkill.skill(SRE.id("bee_family_poison"), "skill.noellesroles.bee_family_poison", (ctx) -> {
                    return BeeFamilyManager.triggerSkill(ctx, true);
                }).withTarget().cooldownSeconds(60).showOnHud(true).announceToSelf().build());
        RoleSkill.register(BounsRoles.BEE_WASP,
                RoleSkill.skill(SRE.id("bee_family_poison"), "skill.noellesroles.bee_family_poison", (ctx) -> {
                    return BeeFamilyManager.triggerSkill(ctx, false);
                }).showOnHud(true).withTarget().cooldownSeconds(30).announceToSelf().build());
        RoleSkill.register(BounsRoles.BEE_QUEEN,
                RoleSkill.skill(SRE.id("bee_queen"), "skill.noellesroles.bee_queen", (ctx) -> {
                    final var player = ctx.player();
                    if (!MoneyUtils.hasBalance(player, BeeFamilyManager.BEE_QUEEN_IMPROVE_PRICE)) {
                        player.displayClientMessage(Component.translatable("skill.noellesroles.bee_queen.no_money",
                                BeeFamilyManager.BEE_QUEEN_IMPROVE_PRICE).withStyle(ChatFormatting.RED), true);
                        return false;
                    }
                    final var cca = SREAbilityPlayerComponent.KEY.get(player);
                    if (cca.status >= 1) {
                        player.displayClientMessage(Component.translatable("skill.noellesroles.bee_queen.already")
                                .withStyle(ChatFormatting.RED), true);
                        return false;
                    }
                    MoneyUtils.addToBalance(player, -BeeFamilyManager.BEE_QUEEN_IMPROVE_PRICE);
                    cca.status = 1;
                    return true;
                }).noCastCCA(true).recordReplay().showOnHud(true).cooldownSeconds(30).announceToSelf().build());
        UseEntityCallback.EVENT.register(((player, level, interactionHand, entity, entityHitResult) -> {

            if (!(player instanceof ServerPlayer interacting)) {
                return InteractionResult.PASS;
            }
            if (!GameUtils.isPlayerAliveAndSurvival(interacting)) {
                return InteractionResult.PASS;
            }
            SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(player.level());
            if (!gameWorldComponent.isRole(player, BounsRoles.BEE_QUEEN)) {
                return InteractionResult.PASS;
            }
            if (!(entity instanceof PlayerBodyEntity body)) {
                return InteractionResult.PASS;
            }
            // 检查是否是葬仪伪造的尸体，不能复活伪造的尸体
            if (PlayerBodyEntityComponent.KEY.get(body).isFakeBody) {
                player.displayClientMessage(
                        Component.translatable("message.stupid_express.necromancer.cannot_revive_fake_body")
                                .withStyle(ChatFormatting.RED),
                        true);
                return InteractionResult.PASS;
            }
            if (!gameWorldComponent.isSkillAvailable) {
                // 技能不可用
                player.displayClientMessage(
                        Component.translatable("message.stupid_express.generic.skill_not_available")
                                .withStyle(ChatFormatting.RED),
                        true);
                return InteractionResult.PASS;
            }
            var serverLevel = (ServerLevel) level;

            // check if the selected body can be revived
            var revived = (ServerPlayer) serverLevel.getPlayerByUUID(body.getPlayerUuid());
            if (revived == null) {
                return InteractionResult.PASS;
            }

            if (!revived.isSpectator()) {
                return InteractionResult.PASS;
            }
            // activate cooldown
            SREAbilityPlayerComponent cca = SREAbilityPlayerComponent.KEY.get(player);
            if (cca.hasCooldown()) {
                return InteractionResult.PASS;
            }
            cca.setCooldown(60 * 20);

            SRERole reviveRole = BounsRoles.BEE_WORKER;
            if (cca.status == 1) {
                reviveRole = BounsRoles.BEE_WASP;
                cca.status = 0;
            }
            final SRERole selectedRole = reviveRole;
            serverLevel.players().forEach(
                    a -> {
                        a.playNotifySound(SoundEvents.BEE_LOOP_AGGRESSIVE, revived.getSoundSource(), 1.2f, 1f);
                        if (gameWorldComponent.getRole(a) instanceof BeeFamilyRole) {
                            a.displayClientMessage(Component.translatable("hud.noellesroles.bee.revived_player",
                                    RoleUtils.getRoleOrModifierNameWithColor(BounsRoles.BEE_QUEEN),
                                    RoleUtils.getRoleOrModifierNameWithColor(selectedRole))
                                    .withStyle(ChatFormatting.GOLD), true);
                        }
                    });
            revived.getInventory().clearContent();
            RoleUtils.changeRole(revived, selectedRole);
            GameUtils.revivePlayer(revived, body.getX(), body.getY(), body.getZ());
            body.discard(); // like it never existed

            RoleUtils.sendWelcomeAnnouncement(revived);

            return InteractionResult.CONSUME;
        }));
    }

    public static boolean checkBeeFamilyVictory(ServerLevel world) {
        int alive = 0, beeAlive = 0;
        var gameComponent = SREGameWorldComponent.getInstance(world);
        for (ServerPlayer p : world.players()) {
            if (!GameUtils.isPlayerAliveAndSurvival(p))
                continue;
            alive++;
            if (gameComponent.getRole(p) instanceof BeeFamilyRole) {
                beeAlive++;
            }
        }
        if (beeAlive > 0 && alive == beeAlive) {
            RoleUtils.customWinnerWin(world, WinStatus.CUSTOM, "bee_family",
                    java.util.OptionalInt.of(BounsRoles.BEE_QUEEN.color()));
            return true;
        }
        return false;
    }

    public static boolean triggerSkill(RoleSkillContext ctx, boolean willDeathAfterSkill) {
        final var player = ctx.player();
        if (ctx.target() == null) {
            player.displayClientMessage(
                    Component.translatable("tip.noellesroles.no_target").withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (!(player.level().getPlayerByUUID(ctx.target()) instanceof ServerPlayer target)) {
            player.displayClientMessage(
                    Component.translatable("tip.noellesroles.no_target").withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (!(RoleUtils.getPlayerRole(target) instanceof BeeFamilyRole)) {
            player.displayClientMessage(
                    Component.translatable("tip.noellesroles.no_target").withStyle(ChatFormatting.RED), true);
            return false;
        }
        SREPlayerPoisonComponent.KEY.get(target).setPoisonTicks(BEE_POISON_TICKS, player.getUUID());
        if (willDeathAfterSkill) {
            GameUtils.forceKillPlayer(player, true, null, GameConstants.DeathReasons.BEE_USED_OUT_SKILL);
        }
        return true;
    }
}
