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

package io.wifi.starrailexpress.game.modes.funny;

import java.util.*;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.cca.SRERoleWorldComponent;
import io.wifi.starrailexpress.event.OnGameTrueStarted;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.modes.SREMurderGameMode;
import io.wifi.starrailexpress.game.modes.funny.rotation.SingleSelectDraftState;
import io.wifi.starrailexpress.game.roles.SpecialGameModeRoles;
import io.wifi.starrailexpress.network.CloseUiPayload;
import io.wifi.starrailexpress.network.packet.RoleRotationSyncS2CPacket;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.config.HarpyModLoaderConfig;
import org.agmas.harpymodloader.events.ModdedRoleAssigned;
import org.agmas.harpymodloader.modded_murder.PlayerRoleWeightManager;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.utils.RoleUtils;

/**
 * 职业轮抽单选模式 - 玩家按顺序逐一选择职业。
 * 与 SRERoleRotationGameMode（闪电轮抽/并行模式）的区别：
 * - 玩家 1-by-1 依次选择，每人 4 秒时限
 * - 每次给出 3 个候选 + 随机选项
 * - 复用相同的客户端 UI 和网络包
 */
public class SRERoleRotationSingleSelectGameMode extends SREMurderGameMode {

    private static final int ROTATION_SAFE_TIME = 5 * 60 * 20;
    private boolean isInRotationPhase = false;
    private long rotationTimeout = -1;
    private SingleSelectDraftState draftState;

    public SRERoleRotationSingleSelectGameMode(ResourceLocation identifier) {
        super(identifier, 10, 3);
    }

    // 网络接收器由 SRERoleRotationGameMode.registerServerPacketRecievers() 统一注册，
    // 因为它也处理 SRERoleRotationSingleSelectGameMode。

    @Override
    public void initializeGame(ServerLevel world, SREGameWorldComponent gameComp, List<ServerPlayer> players) {
        gameComp.clearRoleMap(false);
        for (ServerPlayer p : players) {
            gameComp.addRole(p, SpecialGameModeRoles.CUSTOM_PENDING, false);
            p.addEffect(new MobEffectInstance(ModEffects.SAFE_TIME, ROTATION_SAFE_TIME + 40, 10, true, false, false));
            p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, ROTATION_SAFE_TIME + 40, 10, true, false, false));
            p.addEffect(new MobEffectInstance(ModEffects.MOVE_BANED, ROTATION_SAFE_TIME + 40, 10, true, false, false));
            p.addEffect(new MobEffectInstance(ModEffects.SKILL_BANED, 40, 10, true, false, false));
            p.addEffect(new MobEffectInstance(ModEffects.CCA_FREEZED, 40, 10, true, false, false));
        }

        // 保底
        final var random = new Random(world.getGameTime());
        for (var p : players) {
            if (PlayerRoleWeightManager.ForcePlayerTeam.containsKey(p.getUUID()))
                continue;
            var manager = PlayerRoleWeightManager.playerWeights.get(p.getUUID());
            if (manager != null) {
                if (manager.getStreakCount() >= random.nextInt(4, 7)) {
                    int highestWeightType = PlayerRoleWeightManager.getHighestScoredType(p.getUUID());
                    if (highestWeightType == manager.getLastAssignedFactionGroup())
                        continue;
                    PlayerRoleWeightManager.forceTeam(p.getUUID(), highestWeightType);
                }
            }
        }

        // 初始化单选轮抽状态
        draftState = new SingleSelectDraftState();
        players.forEach(p -> draftState.playerOrder.put(p.getUUID(), 0)); // 先放入 playerOrder，稍后 assignRotationOrder 覆写
        draftState.assignRotationOrder();
        draftState.initializeRolePool(world);
        draftState.startNextRound(world);

        isInRotationPhase = true;
        rotationTimeout = world.getGameTime() + ROTATION_SAFE_TIME;
        broadcastSync(world);
    }

    private void broadcastSync(ServerLevel world) {
        // 获取当前玩家序号列表
        List<UUID> orderList = new ArrayList<>();
        for (int i = 1; i <= draftState.totalPlayers; i++) {
            for (Map.Entry<UUID, Integer> entry : draftState.playerOrder.entrySet()) {
                if (entry.getValue() == i) {
                    orderList.add(entry.getKey());
                    break;
                }
            }
        }

        RoleRotationSyncS2CPacket packet = new RoleRotationSyncS2CPacket(
                draftState.isSelecting,
                draftState.currentRotationIndex,
                draftState.totalPlayers,
                draftState.confirmCountdown,
                draftState.perPlayerTimeLimit,
                draftState.roundStartTime,
                orderList,
                draftState.getSelectedRolesAsStrings(),
                draftState.randomChoosers,
                draftState.getRoundCandidatesAsStrings());
        for (ServerPlayer p : world.players()) {
            ServerPlayNetworking.send(p, packet);
        }
    }

    public void handlePlayerSelection(ServerPlayer player, int choiceIndex) {
        if (!isInRotationPhase || draftState == null) return;
        if (draftState.processSelection(player.serverLevel(), player.getUUID(), choiceIndex)) {
            broadcastSync(player.serverLevel());
        }
    }

    @Override
    public void tickServerGameLoop(ServerLevel world, SREGameWorldComponent gameComp) {
        if (!isInRotationPhase || draftState == null) {
            super.tickServerGameLoop(world, gameComp);
            return;
        }

        // 处理离线玩家
        if (draftState.handleOfflinePlayers(world)) {
            broadcastSync(world);
        }

        // 总超时
        if (world.getGameTime() >= rotationTimeout) {
            forceFinishRotation(world, gameComp);
            return;
        }

        // 确认倒计时
        if (!draftState.isSelecting && draftState.confirmCountdown > 0) {
            draftState.confirmCountdown--;
            if (draftState.confirmCountdown % 20 == 0)
                broadcastSync(world);
            if (draftState.confirmCountdown <= 0) {
                finishRotationPhase(world, gameComp);
                return;
            }
        }

        // 单人选择超时
        if (draftState.isSelecting && draftState.isCurrentPlayerTimedOut(world)) {
            draftState.timeoutUnfinishedPlayers(world);
            broadcastSync(world);
        }
    }

    private void forceFinishRotation(ServerLevel world, SREGameWorldComponent gameComp) {
        // 为所有未选职业的玩家随机分配
        for (Map.Entry<UUID, Integer> entry : draftState.playerOrder.entrySet()) {
            UUID uuid = entry.getKey();
            if (!draftState.selectedRoles.containsKey(uuid)) {
                SRERole role = draftState.rolePool.isEmpty() ? TMMRoles.CIVILIAN : draftState.rolePool.remove(0);
                draftState.selectedRoles.put(uuid, role);
            }
        }
        finishRotationPhase(world, gameComp);
    }

    private void finishRotationPhase(ServerLevel world, SREGameWorldComponent gameComp) {
        Map<UUID, SRERole> finalRoles = new HashMap<>(draftState.selectedRoles);
        isInRotationPhase = false;
        draftState = null;
        completeRoleSelection(world, gameComp, finalRoles);

        world.players().forEach(p -> {
            SREPlayerMoodComponent mood = SREPlayerMoodComponent.KEY.get(p);
            mood.setMood(1);
            mood.sync();
        });
        OnGameTrueStarted.EVENT.invoker().onGameTrueStarted(world);

        Harpymodloader.FORCED_MODDED_ROLE.clear();
        Harpymodloader.FORCED_MODDED_MODIFIER.clear();
        PlayerRoleWeightManager.ForcePlayerTeam.clear();
    }

    private void completeRoleSelection(ServerLevel world, SREGameWorldComponent gameComp,
            Map<UUID, SRERole> selectedRoles) {
        SRERoleWorldComponent roleComp = SRERoleWorldComponent.KEY.get(world);
        for (ServerPlayer p : world.players()) {
            SRERole role = selectedRoles.get(p.getUUID());
            if (role != null) {
                gameComp.addRole(p, role, false);
                p.displayClientMessage(
                        Component.translatable("gui.sre.role_rotation.selected",
                                RoleUtils.getRoleName(role).withColor(role.getColor()))
                                .withStyle(ChatFormatting.GREEN),
                        true);
            }
        }
        roleComp.sync();

        List<ServerPlayer> alive = world.getPlayers(GameUtils::isPlayerAliveAndSurvivalIgnoreShitSplit);
        for (ServerPlayer p : alive) {
            var role = gameComp.getRole(p);
            var roleType = PlayerRoleWeightManager.getRoleType(role);
            PlayerRoleWeightManager.addWeight(p, roleType, 1);
            p.removeEffect(ModEffects.SKILL_BANED);
            p.removeEffect(ModEffects.SAFE_TIME);
            p.removeEffect(ModEffects.MOVE_BANED);
            p.removeEffect(MobEffects.INVISIBILITY);
            p.removeEffect(ModEffects.CCA_FREEZED);

            if (role != null) {
                RoleUtils.sendWelcomeAnnouncement(p);
                if (role.canUseKiller()) {
                    SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(p);
                    if (shop.balance < GameConstants.getMoneyStart()) {
                        shop.setBalance(GameConstants.getMoneyStart());
                    }
                }
                ModdedRoleAssigned.EVENT.invoker().assignModdedRole(p, role);
            }
            ServerPlayNetworking.send(p, new CloseUiPayload());
        }

        int safeTime = SREConfig.instance().safeTimeCooldown * 20;
        GameUtils.addItemCooldowns(world, safeTime);

        int modifierCount = (int) (alive.size() * HarpyModLoaderConfig.HANDLER.instance().modifierMultiplier);
        assignModifiers(modifierCount, world, gameComp, alive);
        GameUtils.recordPlayerStats(world, gameComp, new ArrayList<>(world.players()));
        SRE.REPLAY_MANAGER.updateReplayInitialRoles(alive, gameComp.getRoles());
    }

    @Override
    public void finalizeGame(ServerLevel world, SREGameWorldComponent gameComp) {
        super.finalizeGame(world, gameComp);
        isInRotationPhase = false;
        rotationTimeout = -1;
        draftState = null;
    }

    @Override
    public boolean autoTriggerGameTrueStarted() {
        return false;
    }

    @Override
    public GameUtils.WinStatus allowGameEnd(ServerLevel world, GameUtils.WinStatus winStatus, boolean looseEnds,
            SREGameWorldComponent gameComp) {
        if (isInRotationPhase) return GameUtils.WinStatus.NONE;
        
        return super.allowGameEnd(world, winStatus, looseEnds, gameComp);
    }

    @Override
    public void gameStarted(ServerLevel world, SREGameWorldComponent gameComp, ArrayList<ServerPlayer> ready) {}

    @Override
    public void recordPlayerStats(ServerLevel world, SREGameWorldComponent gameComp, ArrayList<ServerPlayer> ready) {}
}
