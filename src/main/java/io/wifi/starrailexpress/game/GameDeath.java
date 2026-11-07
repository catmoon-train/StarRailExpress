/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * This program is distributed WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See <https://www.gnu.org/licenses/> for the GNU General Public License.
 */
package io.wifi.starrailexpress.game;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.cca.PlayerBodyEntityComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.compat.TrainVoicePlugin;
import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.event.GameUtilityEvents;
import io.wifi.starrailexpress.index.TMMEntities;
import io.wifi.starrailexpress.progression.ProgressionDataManager;
import net.exmo.sre.meeting.MeetingManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.component.DeathPenaltyComponent;
import org.agmas.noellesroles.component.DefibrillatorComponent;
import org.agmas.noellesroles.game.fake_steve.FakeSteveDirector;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.utils.AnatmanAsatyaCorpseGuard;
import org.agmas.noellesroles.utils.RoleUtils;
import org.jetbrains.annotations.Nullable;

import java.util.stream.Collectors;

import static io.wifi.starrailexpress.game.GameUtils.*;

/** 死亡调度、尸体生成及复活；强杀上下文仅在服务端主线程的结算栈内生效。 */
final class GameDeath {
    private static boolean forcedDeathInProgress = false;

    private GameDeath() {
    }

    static PlayerBodyEntity spawnBodyEntity(Player victim, @Nullable Player killer, @Nullable SRERole victimRole,
            ResourceLocation deathReason, boolean saveItems) {
        return spawnBodyEntity(victim, killer, victimRole, deathReason, saveItems,
                victim.position().add(victim.getLookAngle().normalize().scale(1)), victim.getYHeadRot(), 0f);
    }

    static PlayerBodyEntity spawnBodyEntity(Player victim, @Nullable Player killer, @Nullable SRERole victimRole,
            ResourceLocation deathReason) {
        return spawnBodyEntity(victim, killer, victimRole, deathReason, SREConfig.instance().savePlayerBodyItems,
                victim.position().add(victim.getLookAngle().normalize().scale(1)), victim.getYHeadRot(), 0f);
    }

    static PlayerBodyEntity spawnBodyEntity(Player victim, @Nullable Player killer, @Nullable SRERole victimRole,
            ResourceLocation deathReason, boolean saveItems, Vec3 spawnPos, float yHeadRot, float xHeadRot) {
        if (victim == null) return null;
        PlayerBodyEntity body = TMMEntities.PLAYER_BODY.create(victim.level());
        if (body != null) {
            double scale = victim.getAttributeValue(Attributes.SCALE);
            victim.stopRiding();
            victim.stopSleeping();
            body.getAttribute(Attributes.SCALE).setBaseValue(scale);
            PlayerBodyEntityComponent bodycca = body.getComponent();
            if (killer != null) bodycca.setKillerUuid(killer.getUUID(), false);
            bodycca.setDeathReason(deathReason.toString(), false);
            body.setPlayerUuid(victim.getUUID());
            bodycca.setOwnerName(victim.getScoreboardName(), false);
            body.moveTo(spawnPos.x(), victim.getY(), spawnPos.z(), yHeadRot, xHeadRot);
            body.setYRot(victim.getYHeadRot());
            body.setYHeadRot(victim.getYHeadRot());
            victim.level().addFreshEntity(body);
            if (saveItems) body.setCorpseInventoryFromPlayerInventory(victim.getInventory(), false);
            var role = victimRole;
            if (role == null) role = RoleUtils.getPlayerRole(victim);
            if (role != null) bodycca.playerRole = role.identifier();
            // 被动复活可能先于尸体生成；统一同步前设置不可交互，防止重复复活或打开背包。
            if (DefibrillatorComponent.KEY.get(victim).isDead) bodycca.setNonInteractable(true, false);
            // 无我/无妄死者仍可还魂，保留待复活保护。
            AnatmanAsatyaCorpseGuard.onBodySpawned(body, victim);
            bodycca.sync();
        }
        return body;
    }

    @Nullable
    static PlayerBodyEntity findPlayerBodyEntity(ServerPlayer player) {
        // 返回顺序与原遍历相同；无需为一个结果分配临时列表。
        for (var entity : player.serverLevel().getAllEntities()) {
            if (entity instanceof PlayerBodyEntity body && body.getPlayerUuid().equals(player.getUUID())) return body;
        }
        return null;
    }

    static boolean isForcedDeathInProgress() {
        return forcedDeathInProgress;
    }

    static void killPlayer(Player victim, boolean spawnBody, @Nullable Player _killer,
            ResourceLocation deathReason, boolean forceDeath) {
        // 原 HEAD 注入在参数校验、线程转交及伪人强杀升级之前运行。
        if (!GameUtilityEvents.BEFORE_KILL.invoker().allow(victim, spawnBody, _killer, deathReason, forceDeath)) return;
        if (victim == null || victim.level() == null) return;
        // 傀儡尸体、Dummy 等不参与死亡结算。
        if (!(victim instanceof ServerPlayer serverPlayer)) return;
        var server = serverPlayer.getServer();
        if (server == null) return;
        // 主线程 execute 内联执行，调用方仍可同步获取死亡结果。
        server.execute(() -> killPlayerOnServerThread(victim, spawnBody, _killer, deathReason, forceDeath));
    }

    private static void killPlayerOnServerThread(Player victim, boolean spawnBody, @Nullable Player _killer,
            ResourceLocation deathReason, boolean forceDeath) {
        var gameMode = SREGameWorldComponent.KEY.get(victim.level()).getGameMode();
        if (gameMode == null) return;
        // 覆盖 AI、手控与投射物，在任何模式否决事件之前升级。
        forceDeath = forceDeath || FakeSteveDirector.isForcedHuntAttack(_killer, victim);
        boolean previousForced = forcedDeathInProgress;
        forcedDeathInProgress = forceDeath;
        try {
            gameMode.killPlayer(victim, spawnBody, _killer, deathReason, forceDeath);
        } catch (Exception e) {
            String victimName = victim.getScoreboardName();
            String victimRole = RoleUtils.getPlayerRole(victim) instanceof SRERole role
                    ? role.getName().getString() : "null";
            String victimModifiers = RoleUtils.getPlayerModifier(victim).stream()
                    .map(f -> f.getName().getString()).collect(Collectors.joining(","));
            String killerName = _killer != null ? _killer.getScoreboardName() : "null";
            String killerRole = _killer != null && RoleUtils.getPlayerRole(_killer) instanceof SRERole role
                    ? role.getName().getString() : "null";
            String killerModifiers = _killer != null ? RoleUtils.getPlayerModifier(_killer).stream()
                    .map(f -> f.getName().getString()).collect(Collectors.joining(",")) : "null";
            SRE.LOGGER.error(
                    "Error while killPlayer: [victim={}({})[{}], spawnBody={}, killer={}({})[{}], deathReason={}, forceDeath={}]",
                    victimName, victimRole, victimModifiers, spawnBody, killerName, killerRole, killerModifiers,
                    deathReason, forceDeath);
            throw e;
        } finally {
            forcedDeathInProgress = previousForced;
        }
    }

    static void revivePlayerToItsRoom(ServerPlayer player) {
        DeathPenaltyComponent.KEY.get(player).clear();
        DefibrillatorComponent.KEY.get(player).clear();
        // 部分复活路径不销毁尸体，解除旧尸体不可交互标记。
        PlayerBodyEntity body = DefibrillatorComponent.findPlayerBodyEntity(player);
        if (body != null) body.setNonInteractable(false);
        GameUtils.teleportBackToRoom(player);
        player.setGameMode(GameType.ADVENTURE);
        restoreMapGravity(player);
        TrainVoicePlugin.resetPlayer(player.getUUID());
        if (shouldGiveSpectatorsDeathPenalty(player)) pendingReJudgeSpectatorsPenalty();
        SRE.REPLAY_MANAGER.recordPlayerRevival(player.getUUID(), null);
        player.addEffect(ModEffects.of(ModEffects.SAFE_TIME, 10, 1, false, false, true));
        if (MeetingManager.isActive()) {
            DefibrillatorComponent.KEY.get(player).triggerDeath(10, null, player.position());
        }
        player.updateFluidOnEyes();
        player.updateInWaterStateAndDoFluidPushing();
    }

    static void revivePlayer(ServerPlayer player, double x, double y, double z) {
        DeathPenaltyComponent.KEY.get(player).clear();
        DefibrillatorComponent.KEY.get(player).clear();
        PlayerBodyEntity body = DefibrillatorComponent.findPlayerBodyEntity(player);
        if (body != null) body.setNonInteractable(false);
        restoreMapGravity(player);
        player.addEffect(ModEffects.of(ModEffects.SAFE_TIME, 10, 1, false, false, true));
        player.teleportTo(x, y, z);
        player.setGameMode(GameType.ADVENTURE);
        TrainVoicePlugin.resetPlayer(player.getUUID());
        if (shouldGiveSpectatorsDeathPenalty(player)) pendingReJudgeSpectatorsPenalty();
        SRE.REPLAY_MANAGER.recordPlayerRevival(player.getUUID(), null);
        if (MeetingManager.isActive()) {
            DefibrillatorComponent.KEY.get(player).triggerDeath(10, null, player.position());
        }
        player.updateFluidOnEyes();
        player.updateInWaterStateAndDoFluidPushing();
        ProgressionDataManager.onRevived(player);
    }

    private static void restoreMapGravity(ServerPlayer player) {
        var gravity = player.getAttribute(Attributes.GRAVITY);
        if (gravity != null) {
            var areas = AreasWorldComponent.KEY.get(player.level());
            AttributeModifier modifier = new AttributeModifier(SRE.id("map"),
                    areas.areasSettings.gravityModifier, AttributeModifier.Operation.ADD_VALUE);
            gravity.addOrReplacePermanentModifier(modifier);
        }
    }
}
