package org.agmas.noellesroles.handler.utils;

import org.agmas.noellesroles.commands.BroadcastCommand;
import org.agmas.noellesroles.role.bouns.BounsRoles;
import org.agmas.noellesroles.role.bouns.roles.BeeFamilyRole;
import org.agmas.noellesroles.role_data.neutral.BeeFamilyRoleData;
import org.agmas.noellesroles.utils.MoneyUtils;
import org.agmas.noellesroles.utils.RoleUtils;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.RoleSkill;
import io.wifi.starrailexpress.api.RoleSkill.RoleSkillContext;
import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.PlayerBodyEntityComponent;
import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerPoisonComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.event.OnGameServerTick;
import io.wifi.starrailexpress.event.OnPlayerDeathWithKiller;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.GameUtils.WinStatus;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;

public class BeeFamilyManager {

    public static final int BEE_QUEEN_IMPROVE_PRICE = 150;
    public static final int BEE_QUEEN_SELECT_QUEEN_PRICE = 300;
    public static final int BEE_POISON_TICKS = 20 * 20;
    public static final int REVIVE_COST_MONEY = 75;
    public static final int REVIVE_COOLDOWN = 60 * 20;
    public static final int KILL_AWARD_TO_QUEEN = 50;
    public static final int BEE_WORKER_DEATH_TIMEOUT_TICKS = 120 * 20;

    /**
     * 领袖已招募蜂后时置为 true：场上所有蜜蜂家族职业释放技能后，
     * 中毒致死时间减半。每局开始时由 {@link #resetQueenLeaderBonus()} 复位。
     */
    public static boolean QUEEN_LEADER_BONUS = false;
    public static boolean pendingCheck = false;

    public static void setQueenLeaderBonus(boolean value) {
        QUEEN_LEADER_BONUS = value;
    }

    public static void resetQueenLeaderBonus() {
        QUEEN_LEADER_BONUS = false;
    }

    public static void registerEvents() {
        OnGameServerTick.EVENT.register((world) -> tick(world));
        // 蜜蜂频道
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, serverPlayer, bound) -> {
            SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(serverPlayer.level());
            if (gameWorldComponent.getRole(serverPlayer) instanceof BeeFamilyRole role) {
                if (!GameUtils.isPlayerAliveAndSurvivalIgnoreShitSplit(serverPlayer)) {
                    return true;
                }
                var napc = RoleData.getNullable(BeeFamilyRoleData.class, serverPlayer);
                if (napc == null)
                    return true;
                if (napc.beeChannel) { // bee频道
                    var broadcastMessage = Component
                            .translatable("message.bee_family.broadcast_prefix",
                                    Component.literal("(").append(role.getDisplayName()).append(")")
                                            .withStyle(ChatFormatting.YELLOW),
                                    Component.literal("").append(serverPlayer.getDisplayName())
                                            .withStyle(ChatFormatting.AQUA),
                                    Component.literal(message.signedContent()).withStyle(ChatFormatting.WHITE))
                            .withStyle(ChatFormatting.GOLD);
                    serverPlayer.getServer().getPlayerList().getPlayers().forEach((p) -> {
                        var prole = gameWorldComponent.getRole(p.getUUID());
                        if (prole == null)
                            return;
                        if (!GameUtils.isPlayerAliveAndSurvival(p)) {
                            p.displayClientMessage(broadcastMessage, false);
                        }
                        if (!(prole instanceof BeeFamilyRole)) {
                            return;
                        }
                        BroadcastCommand.BroadcastMessage(p, broadcastMessage);
                        p.displayClientMessage(broadcastMessage, false);
                    });
                    return false;
                }
            }
            return true;
        });

        RoleSkill.register(BounsRoles.BEE_WORKER,
                RoleSkill.skill(SRE.id("bee_family_poison"), "skill.noellesroles.bee_family_poison", (ctx) -> {
                    return BeeFamilyManager.triggerSkill(ctx, true);
                }).withTarget().cooldownSeconds(60).showOnHud(true).announceToSelf().build(),
                RoleSkill.skill(SRE.id("bee_channel"),
                        "skill.noellesroles.bee_channel", (ctx) -> changeChannel(ctx))
                        .showOnHud(true)
                        .cooldownTicks(1)
                        .toggleable(true)
                        .noAnnouncement()
                        .build());
        RoleSkill.register(BounsRoles.BEE_WASP,
                RoleSkill.skill(SRE.id("bee_family_poison"), "skill.noellesroles.bee_family_poison", (ctx) -> {
                    return BeeFamilyManager.triggerSkill(ctx, false);
                }).showOnHud(true).withTarget().cooldownSeconds(30).announceToSelf().build(),
                RoleSkill.skill(SRE.id("bee_channel"),
                        "skill.noellesroles.bee_channel", (ctx) -> changeChannel(ctx))
                        .cooldownTicks(1)
                        .showOnHud(true)
                        .toggleable(true)
                        .noAnnouncement()
                        .build());
        RoleSkill.register(BounsRoles.BEE_QUEEN,
                RoleSkill.skill(SRE.id("bee_queen/improve"), "skill.noellesroles.bee_queen.improve", (ctx) -> {
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
                }).noCastCCA(true).recordReplay().showOnHud(true).cooldownSeconds(60).announceToSelf().build(),
                RoleSkill.skill(SRE.id("bee_queen/mark"), "skill.noellesroles.bee_queen.mark", (ctx) -> {
                    final var player = ctx.player();
                    if (!MoneyUtils.hasBalance(player, BeeFamilyManager.BEE_QUEEN_SELECT_QUEEN_PRICE)) {
                        player.displayClientMessage(
                                Component.translatable("skill.noellesroles.bee_queen.select.no_money",
                                        BeeFamilyManager.BEE_QUEEN_SELECT_QUEEN_PRICE).withStyle(ChatFormatting.RED),
                                true);
                        return false;
                    }
                    final var roledata = RoleData.getNullable(BeeFamilyRoleData.class, player);
                    if (roledata == null) {
                        return false;
                    }
                    if (roledata.markTarget != null) {
                        if (player.level().getPlayerByUUID(roledata.markTarget) != null) {
                            player.displayClientMessage(
                                    Component.translatable("skill.noellesroles.bee_queen.select.already")
                                            .withStyle(ChatFormatting.RED),
                                    true);
                            return false;
                        }
                    }
                    if (ctx.target() == null) {
                        player.displayClientMessage(
                                Component.translatable("tip.noellesroles.no_target").withStyle(ChatFormatting.RED),
                                true);
                        return false;
                    }

                    if (!(ctx.player().serverLevel().getEntity(ctx.target()) instanceof PlayerBodyEntity be)) {
                        player.displayClientMessage(
                                Component.translatable("tip.noellesroles.no_target").withStyle(ChatFormatting.RED),
                                true);
                        return false;
                    }
                    if (be.getPlayerUuid() == null) {
                        player.displayClientMessage(
                                Component.translatable("tip.noellesroles.no_target").withStyle(ChatFormatting.RED),
                                true);
                        return false;
                    }
                    if (!(player.level()
                            .getPlayerByUUID(be.getPlayerUuid()) instanceof ServerPlayer marktargetplayer)) {
                        player.displayClientMessage(
                                Component.translatable("tip.noellesroles.no_target").withStyle(ChatFormatting.RED),
                                true);
                        return false;
                    }
                    if (GameUtils.isPlayerAliveAndSurvival(marktargetplayer)) {
                        player.displayClientMessage(
                                Component.translatable("tip.noellesroles.no_target").withStyle(ChatFormatting.RED),
                                true);
                        return false;
                    }
                    MoneyUtils.addToBalance(player, -BeeFamilyManager.BEE_QUEEN_SELECT_QUEEN_PRICE);
                    roledata.markSuccessor(marktargetplayer.getUUID());
                    return true;
                }).noCastCCA(true)
                        .withTarget()
                        .recordReplay()
                        .targetType((t) -> t instanceof PlayerBodyEntity)
                        .showOnHud(true)
                        .cooldownSeconds(60)
                        .announceToSelf()
                        .build(),
                RoleSkill.skill(SRE.id("bee_channel"),
                        "skill.noellesroles.bee_channel", (ctx) -> changeChannel(ctx))
                        .noCastCCA(true)
                        .cooldownTicks(1)
                        .showOnHud(true)
                        .toggleable(true)
                        .noAnnouncement()
                        .build());
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
            if (!MoneyUtils.hasBalance(interacting, REVIVE_COST_MONEY)) {
                player.displayClientMessage(
                        Component.translatable("hud.noellesroles.bee_family.money", REVIVE_COST_MONEY)
                                .withStyle(ChatFormatting.RED),
                        true);
                return InteractionResult.PASS;
            }
            MoneyUtils.addToBalance(interacting, -REVIVE_COST_MONEY);
            cca.setCooldown(REVIVE_COOLDOWN);

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
            final SRERole beforeRole = gameWorldComponent.getRole(revived);
            RoleUtils.changeRole(revived, selectedRole);
            GameUtils.revivePlayer(revived, body.getX(), body.getY(), body.getZ());
            body.discard(); // like it never existed

            RoleUtils.sendWelcomeAnnouncement(revived);
            if (!(beforeRole instanceof BeeFamilyRole))
                RoleData.ifPresent(BeeFamilyRoleData.class, revived, (data) -> data.beforeRole = beforeRole);
            return InteractionResult.CONSUME;
        }));
        OnPlayerDeathWithKiller.EVENT.register((player, killer, deathReason) -> {
            if (RoleUtils.getPlayerRole(player) instanceof BeeFamilyRole) {
                BeeFamilyRole.onDeath(player, killer, deathReason);
            }
        });
        OnPlayerDeathWithKiller.EVENT.register((player, killer, deathReason) -> {
            if (killer == null) {
                return;
            }
            var worldcca = SREGameWorldComponent.getInstance(player);
            var role = worldcca.getRole(killer);
            if (!(role instanceof BeeFamilyRole)) {
                return;
            }
            for (final var p : player.level().players()) {
                if (!GameUtils.isPlayerAliveAndSurvival(p))
                    continue;
                if (worldcca.isRole(p, BounsRoles.BEE_QUEEN)) {
                    SREPlayerShopComponent.KEY.get(p).addToBalance(KILL_AWARD_TO_QUEEN);
                }
            }
        });
    }

    private static boolean changeChannel(RoleSkillContext ctx) {
        final var player = ctx.player();
        var roledata = RoleData.getNullable(BeeFamilyRoleData.class, player);
        if (roledata == null) {
            return false;
        }
        roledata.turnChannel();
        return true;
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
        if ((RoleUtils.getPlayerRole(target) instanceof BeeFamilyRole)) {
            player.displayClientMessage(
                    Component.translatable("tip.noellesroles.no_target").withStyle(ChatFormatting.RED), true);
            return false;
        }
        int poisonTicks = QUEEN_LEADER_BONUS ? BEE_POISON_TICKS / 2 : BEE_POISON_TICKS;
        var ppc = SREPlayerPoisonComponent.KEY.get(target);
        if (ppc.poisonTicks > 0) {
            GameUtils.killPlayer(target, true, player, GameConstants.DeathReasons.POISON);
        } else {
            ppc.setPoisonTicks(poisonTicks, player.getUUID());
        }
        if (willDeathAfterSkill) {
            GameUtils.forceKillPlayer(player, true, null, GameConstants.DeathReasons.BEE_USED_OUT_SKILL);
        }
        return true;
    }

    public static boolean shouldPreventGameEnd(ServerLevel serverLevel) {
        var cca = SREGameWorldComponent.KEY.get(serverLevel);
        for (ServerPlayer p : serverLevel.players())
            if (GameUtils.isPlayerAliveAndSurvival(p) && cca.getRole(p) instanceof BeeFamilyRole)
                return true;
        return false;
    }

    /**
     * 检查蜜蜂家族是否全体死亡。如果是恢复死者原本职业。
     */
    public static void checkBeeFamilyFailure(ServerLevel serverLevel) {
        final var gamecca = SREGameWorldComponent.KEY.get(serverLevel);
        int aliveBees = 0;
        for (var player : serverLevel.players()) {
            if (!GameUtils.isPlayerAliveAndSurvival(player)) {
                continue;
            }
            if (gamecca.getRole(player) instanceof BeeFamilyRole) {
                aliveBees++;
            }
        }
        if (aliveBees <= 0) {
            beeFamilyFailed(serverLevel);
        }
    }

    public static void tick(ServerLevel world) {
        if(pendingCheck){
            pendingCheck = false;
            checkBeeFamilyFailure(world);
        }
    }

    public static void beeFamilyFailed(ServerLevel serverLevel) {
        SRE.LOGGER.info("Bee family failed! Try restore roles.");
        final var gamecca = SREGameWorldComponent.KEY.get(serverLevel);
        for (var player : serverLevel.players()) {
            if (gamecca.getRole(player) instanceof BeeFamilyRole) {
                RoleData.ifPresent(BeeFamilyRoleData.class, player, (data) -> {
                    if (data.beforeRole != null && !(data.beforeRole instanceof BeeFamilyRole)) {
                        // 变回原有角色！
                        RoleUtils.changeRole(player, data.beforeRole, true, false, false, true);
                    }
                });
            }
        }
    }

    public static void pendingCheckFailure() {
        pendingCheck = true;
    }

    public static void reset() {
        pendingCheck = false;
    }
}
