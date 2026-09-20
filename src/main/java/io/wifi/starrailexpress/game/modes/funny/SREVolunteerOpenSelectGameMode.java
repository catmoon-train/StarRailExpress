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

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameTimeComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.cca.SREPlayerTaskComponent;
import io.wifi.starrailexpress.cca.SRERoleWorldComponent;
import io.wifi.starrailexpress.event.OnGameTrueStarted;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.modes.SREMurderGameMode;
import io.wifi.starrailexpress.game.modes.funny.volunteer.VolunteerOpenDraftState;
import io.wifi.starrailexpress.game.roles.SpecialGameModeRoles;
import io.wifi.starrailexpress.network.CloseUiPayload;
import io.wifi.starrailexpress.network.packet.VolunteerOpenReadyC2SPacket;
import io.wifi.starrailexpress.network.packet.VolunteerOpenSelectC2SPacket;
import io.wifi.starrailexpress.network.packet.VolunteerOpenSyncS2CPacket;
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
import org.agmas.harpymodloader.modded_murder.ForceTeamInfo.ForceTeamType;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.utils.RoleUtils;

import java.util.*;

/**
 * 志愿海选模式 (haiman:volunteer_open_select)。
 *
 * <p>
 * 开局流程与职业轮选模式一致（同样的开局效果、同样的发放职业与 sendWelcome 时机），
 * 只把「选职业」的过程换成两阶段：
 * <ol>
 * <li>一阶段（8 秒）：全员填写一个志愿职业，只作为二阶段的提示；</li>
 * <li>二阶段：按序号分成至多五组，依次在「海选池」里挑选职业，每组开始时随机揭晓与
 * 本组人数相同的职业；持有阵营卡的玩家还会额外看到本阵营的职业；</li>
 * <li>确认阶段：与轮选模式一样按按钮确认。</li>
 * </ol>
 */
public class SREVolunteerOpenSelectGameMode extends SREMurderGameMode {

    private static final int DRAFT_SAFE_TIME = 5 * 60 * 20;

    private boolean isInDraftPhase = false;
    private long draftTimeout = -1;
    private VolunteerOpenDraftState draftState;

    public SREVolunteerOpenSelectGameMode(ResourceLocation identifier) {
        super(identifier, 10, 3);
    }

    @Override
    public boolean hasPreSounds() {
        return true;
    }

    public static void registerServerPacketRecievers() {
        ServerPlayNetworking.registerGlobalReceiver(VolunteerOpenSelectC2SPacket.TYPE, (packet, context) -> {
            context.player().server.execute(() -> {
                ServerPlayer player = context.player();
                if (player.level() instanceof ServerLevel serverLevel) {
                    var gameMode = SREGameWorldComponent.getInstance(serverLevel).getGameMode();
                    if (gameMode instanceof SREVolunteerOpenSelectGameMode mode) {
                        mode.handlePlayerSelect(player, packet);
                    }
                }
            });
        });
        ServerPlayNetworking.registerGlobalReceiver(VolunteerOpenReadyC2SPacket.TYPE, (packet, context) -> {
            context.player().server.execute(() -> {
                ServerPlayer player = context.player();
                if (player.level() instanceof ServerLevel serverLevel) {
                    var gameMode = SREGameWorldComponent.getInstance(serverLevel).getGameMode();
                    if (gameMode instanceof SREVolunteerOpenSelectGameMode mode) {
                        mode.handlePlayerReady(player);
                    }
                }
            });
        });
    }

    @Override
    public void initializeGame(ServerLevel world, SREGameWorldComponent gameComp, List<ServerPlayer> players) {
        gameComp.clearRoleMap(false);
        SREGameTimeComponent.KEY.get(world).setTimeFrozen(true);
        for (ServerPlayer p : players) {
            gameComp.addRole(p, SpecialGameModeRoles.CUSTOM_PENDING, false);
            p.addEffect(new MobEffectInstance(ModEffects.SAFE_TIME, DRAFT_SAFE_TIME + 40, 10, true, false, false));
            p.addEffect(
                    new MobEffectInstance(MobEffects.INVISIBILITY, DRAFT_SAFE_TIME + 40, 10, true, false, false));
            p.addEffect(new MobEffectInstance(ModEffects.MOVE_BANED, DRAFT_SAFE_TIME + 40, 10, true, false, false));
            p.addEffect(new MobEffectInstance(ModEffects.SKILL_BANED, DRAFT_SAFE_TIME + 40, 10, true, false, false));
            p.addEffect(new MobEffectInstance(ModEffects.CCA_FREEZED, DRAFT_SAFE_TIME + 40, 10, true, false, false));
        }

        // 保底（与轮选模式一致）
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
                    PlayerRoleWeightManager.forceTeam(p.getUUID(), highestWeightType, ForceTeamType.ROLE_WEIGHTS);
                }
            }
        }

        draftState = new VolunteerOpenDraftState(new ArrayList<>(players), world);
        isInDraftPhase = true;
        draftTimeout = world.getGameTime() + DRAFT_SAFE_TIME;
        broadcastSync(world);
    }

    // ==================== 同步 ====================

    private static int phaseIndex(VolunteerOpenDraftState.Phase phase) {
        return switch (phase) {
            case VOLUNTEER -> 1;
            case OPEN -> 2;
            case CONFIRM -> 3;
            case END -> 0;
        };
    }

    private VolunteerOpenSyncS2CPacket buildPacket(ServerLevel world, UUID id, long now) {
        return new VolunteerOpenSyncS2CPacket(
                phaseIndex(draftState.phase),
                draftState.totalPlayers,
                draftState.remainingTicks(now),
                draftState.phaseTimeLimit,
                draftState.confirmCountdown,
                draftState.confirmRequired,
                draftState.playerOrder,
                draftState.publicPickedRoles(),
                draftState.confirmedPlayers,
                draftState.pool.size(),
                draftState.poolRows,
                draftState.poolCols,
                draftState.visibleRolesFor(id),
                draftState.highlightedRevealsFor(id),
                draftState.hiddenVisibleFor(id),
                draftState.pickIndexOf(id),
                draftState.chosenIndices(),
                draftState.volunteerPoolIndex.getOrDefault(id, -1),
                draftState.phase == VolunteerOpenDraftState.Phase.OPEN ? draftState.groupIndex + 1 : 0,
                draftState.currentGroupMembers(),
                draftState.groups.size(),
                draftState.canPlayerSelect(world, id),
                draftState.volunteerRoleIds.getOrDefault(id, ""));
    }

    private void broadcastSync(ServerLevel world) {
        if (draftState == null) {
            return;
        }
        long now = world.getGameTime();
        for (ServerPlayer p : world.players()) {
            ServerPlayNetworking.send(p, buildPacket(world, p.getUUID(), now));
        }
    }

    /** 告知客户端本模式已结束（清空状态并关闭界面）。 */
    private void broadcastEnd(ServerLevel world) {
        VolunteerOpenSyncS2CPacket packet = new VolunteerOpenSyncS2CPacket(
                0, 0, -1, 0, -1, false,
                List.of(), Map.of(), Set.of(),
                0, 0, 0,
                Map.of(), Set.of(), Set.of(),
                -1, Set.of(), -1, 0, Set.of(), 0, false, "");
        for (ServerPlayer p : world.players()) {
            ServerPlayNetworking.send(p, packet);
        }
    }

    // ==================== 客户端请求 ====================

    public void handlePlayerSelect(ServerPlayer player, VolunteerOpenSelectC2SPacket packet) {
        if (!isInDraftPhase || draftState == null) {
            return;
        }
        if (!draftState.canPlayerParticipate(player.serverLevel(), player.getUUID())) {
            return;
        }
        boolean changed;
        if (packet.volunteer()) {
            changed = draftState.submitVolunteer(player.serverLevel(), player, packet.roleId());
        } else {
            changed = draftState.processPick(player.serverLevel(), player, packet.index());
        }
        if (changed) {
            broadcastSync(player.serverLevel());
        }
    }

    public void handlePlayerReady(ServerPlayer player) {
        if (!isInDraftPhase || draftState == null) {
            return;
        }
        if (!draftState.canPlayerParticipate(player.serverLevel(), player.getUUID())) {
            return;
        }
        if (!draftState.waitingForClients) {
            return;
        }
        int before = draftState.uiReadyPlayers.size();
        draftState.markUiReady(player.getUUID());
        if (draftState.uiReadyPlayers.size() != before) {
            broadcastSync(player.serverLevel());
        }
    }

    public void handlePlayerConfirm(ServerPlayer player) {
        if (!isInDraftPhase || draftState == null) {
            return;
        }
        if (!draftState.canPlayerParticipate(player.serverLevel(), player.getUUID())) {
            return;
        }
        if (draftState.confirm(player.getUUID())) {
            broadcastSync(player.serverLevel());
        }
    }

    // ==================== Tick ====================

    @Override
    public void tickServerGameLoop(ServerLevel world, SREGameWorldComponent gameComp) {
        if (!isInDraftPhase || draftState == null) {
            super.tickServerGameLoop(world, gameComp);
            return;
        }

        if (draftState.handleOfflinePlayers(world)) {
            broadcastSync(world);
        }

        if (world.getGameTime() >= draftTimeout) {
            forceFinishDraft(world, gameComp);
            return;
        }

        long now = world.getGameTime();

        // 一阶段：等所有玩家的界面真正打开（开局黑幕 / 动画播完）之后才开始计时，
        // 玩家没上报就最多等 CLIENT_READY_TIMEOUT，避免有人卡住导致整局不动。
        if (draftState.phase == VolunteerOpenDraftState.Phase.VOLUNTEER && draftState.waitingForClients) {
            boolean allReady = draftState.allUiReady(world);
            boolean timedOut = now - draftState.holdStartTime >= VolunteerOpenDraftState.CLIENT_READY_TIMEOUT;
            if (allReady || timedOut) {
                draftState.startPhaseTiming(now);
                broadcastSync(world);
            } else if (now % 20 == 0) {
                broadcastSync(world);
            }
            return;
        }

        switch (draftState.phase) {
            case VOLUNTEER -> {
                if (now - draftState.phaseStartTime >= draftState.phaseTimeLimit
                        || draftState.allVolunteersSubmitted(world)) {
                    draftState.startOpenPhase(world);
                    broadcastSync(world);
                } else if (now % 20 == 0) {
                    broadcastSync(world);
                }
            }
            case OPEN -> {
                if (draftState.isCurrentGroupDone()) {
                    draftState.advanceGroup(world);
                    broadcastSync(world);
                } else if (now - draftState.phaseStartTime >= draftState.phaseTimeLimit) {
                    draftState.timeoutCurrentGroup(world);
                    draftState.advanceGroup(world);
                    broadcastSync(world);
                } else if (now % 20 == 0) {
                    broadcastSync(world);
                }
            }
            case CONFIRM -> {
                if (draftState.confirmRequired && draftState.allConfirmed(world)) {
                    finishDraftPhase(world, gameComp);
                    return;
                }
                draftState.confirmCountdown--;
                if (draftState.confirmCountdown % 20 == 0) {
                    broadcastSync(world);
                }
                if (draftState.confirmCountdown <= 0) {
                    finishDraftPhase(world, gameComp);
                    return;
                }
            }
            default -> {
            }
        }
    }

    // ==================== 收尾 ====================

    private void forceFinishDraft(ServerLevel world, SREGameWorldComponent gameComp) {
        if (draftState != null) {
            draftState.forceFillRemaining();
            draftState.confirmCountdown = -1;
        }
        finishDraftPhase(world, gameComp);
    }

    private void finishDraftPhase(ServerLevel world, SREGameWorldComponent gameComp) {
        Map<UUID, SRERole> finalRoles = draftState.getFinalRoles();
        draftState.phase = VolunteerOpenDraftState.Phase.END;
        draftState.confirmCountdown = -1;
        broadcastEnd(world);

        isInDraftPhase = false;
        draftState = null;

        completeRoleSelection(world, gameComp, finalRoles);

        world.players().forEach(p -> {
            SREPlayerMoodComponent mood = SREPlayerMoodComponent.KEY.get(p);
            mood.setMood(1);
            mood.sync();
            
            SREPlayerTaskComponent task = SREPlayerTaskComponent.KEY.get(p);
            task.nextTaskTimer = GameConstants.TIME_TO_FIRST_TASK;
        });
        OnGameTrueStarted.EVENT.invoker().onGameTrueStarted(world);
        SREGameTimeComponent.KEY.get(world).setTimeFrozen(false);
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
                        Component.translatable("gui.sre.volunteer_open.selected",
                                RoleUtils.getRoleName(role).withColor(role.getColor()))
                                .withStyle(ChatFormatting.GREEN),
                        true);
            }
        }
        roleComp.syncNow();

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
        isInDraftPhase = false;
        draftTimeout = -1;
        draftState = null;
    }

    @Override
    public boolean autoTriggerGameTrueStarted() {
        return false;
    }

    @Override
    public GameUtils.WinStatus allowGameEnd(ServerLevel world, GameUtils.WinStatus winStatus, boolean looseEnds,
            SREGameWorldComponent gameComp) {
        if (isInDraftPhase)
            return GameUtils.WinStatus.NONE;

        return super.allowGameEnd(world, winStatus, looseEnds, gameComp);
    }

    @Override
    public void gameStarted(ServerLevel world, SREGameWorldComponent gameComp, ArrayList<ServerPlayer> ready) {
    }

    @Override
    public void recordPlayerStats(ServerLevel world, SREGameWorldComponent gameComp, ArrayList<ServerPlayer> ready) {
    }
}
