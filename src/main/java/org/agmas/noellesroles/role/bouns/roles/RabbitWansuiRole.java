package org.agmas.noellesroles.role.bouns.roles;

import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.noellesroles.game.modifier.NRModifiers;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.packet.RefreshDimensionsS2CPacket;
import org.agmas.noellesroles.role.bouns.BounsRoles;
import org.agmas.noellesroles.utils.MoneyUtils;
import org.agmas.noellesroles.utils.RoleUtils;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.CustomWinnerRole;
import io.wifi.starrailexpress.api.EggRoleInterface;
import io.wifi.starrailexpress.api.RoleSkill;
import io.wifi.starrailexpress.api.replay.GameReplayUtils;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.GameUtils.WinStatus;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;

import org.jetbrains.annotations.Nullable;

public class RabbitWansuiRole extends CustomWinnerRole implements EggRoleInterface {

    private static final int COST = 75;

    public RabbitWansuiRole(ResourceLocation identifier, int color, RoleType roleType, MoodType moodType,
            int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, roleType, moodType, maxSprintTime, canSeeTime);
        // 避免游戏结束
        this.canIncreaseSurvivingInnocents = true;
        this.canIncreaseSurvivingKillers = true;
    }

    @Override
    public WinStatus checkWin(ServerPlayer player, WinStatus winStatus) {
        if (!GameUtils.isPlayerAliveAndSurvival(player))
            return WinStatus.NOT_MODIFY;
        final var players = player.serverLevel().getPlayers(GameUtils::isPlayerAliveAndSurvival);
        int count = 0, totalCount = players.size();
        var wmcca = WorldModifierComponent.KEY.get(player.level());
        for (var p : players) {
            if (p.getUUID().equals(player.getUUID()))
                continue;
            if (wmcca.isModifier(p, NRModifiers.RABBIT_SHAPE)) {
                count++;
            }
        }
        if (count >= totalCount - 1 && totalCount >= 1)
            return WinStatus.CUSTOM;
        return WinStatus.NOT_MODIFY;
    };

    @Override
    public void onDeath(Player victim, boolean spawnBody, @Nullable Player killer, ResourceLocation deathReason,
            boolean forceDeath) {
        if (victim instanceof ServerPlayer player) {
            var wmcca = WorldModifierComponent.KEY.get(player.level());
            final var players = player.serverLevel().getPlayers(GameUtils::isPlayerAliveAndSurvival);
            for (var p : players) {
                if (wmcca.isModifier(p, NRModifiers.RABBIT_SHAPE)) {
                    SRE.REPLAY_MANAGER.recordCustomEvent(
                            Component.translatable("replay.event.rabbit.restore",
                                    GameReplayUtils.getReplayPlayerDisplayText(player, true)));
                    wmcca.removeModifier(p, NRModifiers.RABBIT_SHAPE);

                    ServerPlayNetworking.send(player, new RefreshDimensionsS2CPacket());
                    player.refreshDimensions();
                }
            }
        }
        return;
    }

    public static void registerEvents() {
        RoleSkill.register(BounsRoles.RABBIT_WANSUI,
                RoleSkill.skill(SRE.id("rabbit_wansui/target"), "skill.noellesroles.rabbit_wansui.target", (ctx) -> {
                    final var player = ctx.player();

                    final var target = ctx.getTargetAsPlayer();
                    var wmcca = WorldModifierComponent.KEY.get(player.level());
                    if (target == null || wmcca.isModifier(target, NRModifiers.RABBIT_SHAPE)
                            || RoleUtils.isPlayerTheJob(target, BounsRoles.RABBIT_WANSUI)) {
                        ctx.displayNoTargetMessage();
                        return false;
                    }
                    if (!MoneyUtils.cost(player, COST)) {
                        MoneyUtils.sendNotEnoughtMoneyMessage(player, COST);
                        return false;
                    }
                    wmcca.addModifier(target, NRModifiers.RABBIT_SHAPE);
                    target.displayClientMessage(Component.translatable("skill.noellesroles.rabbit_wansui.target.tip")
                            .withStyle(ChatFormatting.AQUA), true);
                    target.addEffect(ModEffects.of(MobEffects.DARKNESS, 5 * 20, 0, false, false, true));
                    target.addEffect(ModEffects.of(MobEffects.BLINDNESS, 5 * 20, 0, false, false, true));
                    target.addEffect(ModEffects.of(ModEffects.USED_BANED, 15 * 20, 0, false, false, true));
                    target.addEffect(ModEffects.of(ModEffects.MOVE_BANED, 5 * 20, 0, false, false, true));
                    target.addEffect(ModEffects.of(ModEffects.JUMP_DECREASE, 5 * 20, 10, false, false, true));
                    target.addEffect(ModEffects.of(ModEffects.CHAT_BAN, 30 * 20, 10, false, false, true));
                    target.addEffect(ModEffects.of(ModEffects.VOICE_SILENCE, 30 * 20, 10, false, false, true));
                    target.addEffect(ModEffects.of(ModEffects.NO_INSTINCT, 120 * 20, 10, false, false, true));
                    target.addEffect(ModEffects.of(ModEffects.INVENTORY_BANED, 30 * 20, 10, false, false, true));
                    target.addEffect(ModEffects.of(ModEffects.NO_STAMINA, 60 * 20, 10, false, false, true));
                    ServerPlayNetworking.send(player, new RefreshDimensionsS2CPacket());
                    player.refreshDimensions();
                    return true;
                })
                        .withTarget()
                        .recordReplay()
                        .showOnHud(true)
                        .cooldownSeconds(30)
                        .announceToSelf().build(),
                RoleSkill.skill(SRE.id("rabbit_wansui/self"), "skill.noellesroles.rabbit_wansui.self", (ctx) -> {
                    final var player = ctx.player();
                    var wmcca = WorldModifierComponent.KEY.get(player.level());
                    if (wmcca.isModifier(player, NRModifiers.RABBIT_SHAPE))
                        wmcca.removeModifier(player, NRModifiers.RABBIT_SHAPE);
                    else
                        wmcca.addModifier(player, NRModifiers.RABBIT_SHAPE);

                    ServerPlayNetworking.send(player, new RefreshDimensionsS2CPacket());
                    player.refreshDimensions();
                    return true;
                })
                        .showOnHud(true)
                        .cooldownSeconds(2)
                        .announceToSelf().build());
    }
}
