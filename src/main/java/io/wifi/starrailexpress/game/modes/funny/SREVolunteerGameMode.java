package io.wifi.starrailexpress.game.modes.funny;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.cca.SRERoleWorldComponent;
import io.wifi.starrailexpress.event.AllowGameEnd;
import io.wifi.starrailexpress.event.OnGameTrueStarted;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.modes.SREMurderGameMode;
import io.wifi.starrailexpress.game.modes.funny.volunteer.VolunteerDraftState;
import io.wifi.starrailexpress.game.roles.SpecialGameModeRoles;
import io.wifi.starrailexpress.network.CloseUiPayload;
import io.wifi.starrailexpress.network.packet.VolunteerCommitC2SPacket;
import io.wifi.starrailexpress.network.packet.VolunteerDraftSyncS2CPacket;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import org.agmas.harpymodloader.config.HarpyModLoaderConfig;
import org.agmas.harpymodloader.events.ModdedRoleAssigned;
import org.agmas.harpymodloader.modded_murder.PlayerRoleWeightManager;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.utils.RoleUtils;

import java.util.*;

public class SREVolunteerGameMode extends SREMurderGameMode {

    private static final int ROTATION_SAFE_TIME = 5 * 60 * 20;
    private boolean isInDraftPhase = false;
    private long draftTimeout = -1;
    private VolunteerDraftState draftState;

    public static final ResourceLocation TNT_TAG_MODE_ID = ResourceLocation.fromNamespaceAndPath(SRE.MOD_ID,
            "volunteer");

    public SREVolunteerGameMode(ResourceLocation identifier) {
        super(identifier, 10, 3);
    }

    public static void registerServerPacketRecievers() {
        ServerPlayNetworking.registerGlobalReceiver(VolunteerCommitC2SPacket.TYPE, (packet, context) -> {
            context.player().server.execute(() -> {
                ServerPlayer player = context.player();
                if (player.level() instanceof ServerLevel serverLevel) {
                    var gameMode = SREGameWorldComponent.getInstance(serverLevel).getGameMode();
                    if (gameMode instanceof SREVolunteerGameMode mode) {
                        mode.handlePlayerCommit(player, packet.preferences());
                    }
                }
            });
        });
    }

    @Override
    public void initializeGame(ServerLevel world, SREGameWorldComponent gameComp, List<ServerPlayer> players) {
        gameComp.clearRoleMap(false);
        for (ServerPlayer p : players) {
            gameComp.addRole(p, SpecialGameModeRoles.CUSTOM_PENDING, false);
            p.addEffect(new MobEffectInstance(ModEffects.SAFE_TIME, ROTATION_SAFE_TIME + 40, 10, true, false, false));
            p.addEffect(
                    new MobEffectInstance(MobEffects.INVISIBILITY, ROTATION_SAFE_TIME + 40, 10, true, false, false));
            p.addEffect(new MobEffectInstance(ModEffects.MOVE_BANED, ROTATION_SAFE_TIME + 40, 10, true, false, false));
            p.addEffect(new MobEffectInstance(ModEffects.SKILL_BANED, 40, 10, true, false, false));
            p.addEffect(new MobEffectInstance(ModEffects.CCA_FREEZED, 40, 10, true, false, false));
            RoleUtils.sendWelcomeAnnouncement(p);
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
        draftState = new VolunteerDraftState(new ArrayList<>(players), world);
        draftState.startCommitPhase(world.getGameTime()); // 立刻进入志愿提交阶段
        isInDraftPhase = true;
        draftTimeout = world.getGameTime() + ROTATION_SAFE_TIME;
        broadcastSync(world); // 发送 COMMIT 状态给所有客户端
    }

    private void broadcastSync(ServerLevel world) {

        for (ServerPlayer p : world.players()) {
            UUID pid = p.getUUID();
            VolunteerDraftState.Phase phase = draftState.getPhase();
            int remaining = 0;
            if (draftState.getPhase() == VolunteerDraftState.Phase.COMMIT) {
                long elapsed = world.getGameTime() - draftState.getPhaseStartTime();
                remaining = (int) Math.max(0, draftState.getCommitTimeLimit() - elapsed);
            } else if (draftState.getPhase() == VolunteerDraftState.Phase.ADJUST) {
                long elapsed = world.getGameTime() - draftState.getPhaseStartTime();
                remaining = (int) Math.max(0, draftState.getAdjustTimeLimit() - elapsed);
            }
            VolunteerDraftSyncS2CPacket packet = new VolunteerDraftSyncS2CPacket(
                    phase,
                    remaining,
                    draftState.getMyCandidateIds(pid),
                    draftState.getPhase() == VolunteerDraftState.Phase.RESULT ? draftState.getFinalRolesAsStrings()
                            : Map.of(),
                    draftState.getPhase() == VolunteerDraftState.Phase.RESULT ? draftState.getMyFinalRoleId(pid) : "",
                    draftState.getVolunteerCount());
            ServerPlayNetworking.send(p, packet);
        }
    }

    private void handlePlayerCommit(ServerPlayer player, List<Integer> preferences) {
        if (!isInDraftPhase || draftState == null)
            return;
        boolean phaseChanged = draftState.submitPreference(player.getUUID(), preferences);
        if (phaseChanged) {
            broadcastSync(player.serverLevel()); // 仅阶段变化时广播
        } else {
            // 仅告知提交者本人：禁用按钮（客户端收到同一个 phase 的包后会保持状态）
            // 不需要额外发包，因为客户端提交后已经本地禁用。
        }
    }

    @Override
    public void tickServerGameLoop(ServerLevel world, SREGameWorldComponent gameComp) {
        if (!isInDraftPhase || draftState == null)
            return;
        // 处理玩家退出
        boolean phaseChanged = false;
        for (ServerPlayer p : new ArrayList<>(world.players())) {
            if (p.isRemoved()) {
                if (draftState.removePlayer(p.getUUID())) {
                    phaseChanged = true;
                }
            }
        }
        if (phaseChanged) {
            broadcastSync(world);
        }

        // 总超时
        if (world.getGameTime() >= draftTimeout) {
            forceFinishDraft(world, gameComp);
            return;
        }

        // 提交超时 → 自动提交并强制进入 ADJUST
        if (draftState.getPhase() == VolunteerDraftState.Phase.COMMIT) {
            long elapsed = world.getGameTime() - draftState.getPhaseStartTime();
            if (elapsed >= draftState.getCommitTimeLimit()) {
                for (ServerPlayer p : world.players()) {
                    if (!draftState.submittedPlayers.contains(p.getUUID())) {
                        autoSubmitRandom(p);
                    }
                }
                // 手动切换阶段并广播
                draftState.setPhase(VolunteerDraftState.Phase.ADJUST); // 需要添加 setter
                draftState.setPhaseStartTime(world.getGameTime());
                broadcastSync(world);
            }
        }

        // ADJUST 阶段：仅在结束时广播
        if (draftState.getPhase() == VolunteerDraftState.Phase.ADJUST) {
            long elapsed = world.getGameTime() - draftState.getPhaseStartTime();
            if (elapsed >= draftState.getAdjustTimeLimit()) {
                draftState.runAssignment();
                // runAssignment 内部会设置 phase = RESULT
                broadcastSync(world);
            }
        } else if (draftState.getPhase() == VolunteerDraftState.Phase.RESULT) {
            long elapsed = world.getGameTime() - draftState.getPhaseStartTime();
            if (elapsed >= draftState.getResultTimeLimit()) {
                completeDraft(world, gameComp);
                completeResult(world, gameComp);
            } else if (elapsed == 0) { // 刚进入 RESULT，立即广播一次
                broadcastSync(world);
            }
        }

        super.tickServerGameLoop(world, gameComp);
    }

    // 为提交超时自动生成随机志愿的工具方法
    private void autoSubmitRandom(ServerPlayer player) {
        int count = draftState.getVolunteerCount();
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < count; i++)
            indices.add(i);
        Collections.shuffle(indices);
        // 不加入随机志愿，直接提交具体顺序
        draftState.submitPreference(player.getUUID(), indices);
    }

    private void forceFinishDraft(ServerLevel world, SREGameWorldComponent gameComp) {
        for (ServerPlayer p : world.players()) {
            if (!draftState.submittedPlayers.contains(p.getUUID())) {
                int count = draftState.getVolunteerCount();
                List<Integer> indices = new ArrayList<>();
                for (int i = 0; i < count; i++)
                    indices.add(i);
                Collections.shuffle(indices);
                draftState.submitPreference(p.getUUID(), indices);
            }
        }
        completeDraft(world, gameComp);
    }

    private void completeResult(ServerLevel world, SREGameWorldComponent gameComp) {
        draftState = null;
        for (ServerPlayer p : world.players()) {
            ServerPlayNetworking.send(p, new CloseUiPayload());
        }
    }

    private void completeDraft(ServerLevel world, SREGameWorldComponent gameComp) {
        if (!isInDraftPhase)
            return;
        isInDraftPhase = false;

        Map<UUID, SRERole> finalRoles = new HashMap<>(draftState.getFinalAssignment());

        SRERoleWorldComponent roleComp = SRERoleWorldComponent.KEY.get(world);
        for (ServerPlayer p : world.players()) {
            SRERole role = finalRoles.get(p.getUUID());
            if (role != null) {
                gameComp.addRole(p, role, false);
                p.displayClientMessage(
                        Component.translatable("gui.sre.volunteer.selected",
                                RoleUtils.getRoleName(role).withColor(role.getColor()))
                                .withStyle(ChatFormatting.GREEN),
                        true);
            } else {
                gameComp.addRole(p, TMMRoles.CIVILIAN, false);
            }
        }
        roleComp.sync();

        List<ServerPlayer> alive = world.getPlayers(GameUtils::isPlayerAliveAndSurvivalIgnoreShitSplit);
        for (ServerPlayer p : alive) {
            SRERole role = roleComp.getRole(p);
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
        }

        int safeTime = SREConfig.instance().safeTimeCooldown * 20 + draftState.getResultTimeLimit();
        GameUtils.addItemCooldowns(world, safeTime);

        int modifierCount = (int) (alive.size() * HarpyModLoaderConfig.HANDLER.instance().modifierMultiplier);
        assignModifiers(modifierCount, world, gameComp, alive);
        GameUtils.recordPlayerStats(world, gameComp, new ArrayList<>(world.players()));
        SRE.REPLAY_MANAGER.updateReplayInitialRoles(alive, gameComp.getRoles());

        world.players().forEach(p -> {
            SREPlayerMoodComponent mood = SREPlayerMoodComponent.KEY.get(p);
            mood.setMood(1);
            mood.sync();
        });
        OnGameTrueStarted.EVENT.invoker().onGameTrueStarted(world);
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
        return AllowGameEnd.EVENT.invoker().allowGameEnd(world, winStatus, false);
    }

    @Override
    public void gameStarted(ServerLevel world, SREGameWorldComponent gameComp, ArrayList<ServerPlayer> ready) {
    }

    @Override
    public void recordPlayerStats(ServerLevel world, SREGameWorldComponent gameComp, ArrayList<ServerPlayer> ready) {
    }
}