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

package org.agmas.noellesroles.game.fake_steve;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.event.OnPlayerDeathWithKiller;
import io.wifi.starrailexpress.cca.SREPlayerPsychoComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.api.replay.GameReplayUtils;
import io.wifi.starrailexpress.event.AllowGameEnd;
import io.wifi.starrailexpress.event.AllowPlayerWin;
import io.wifi.starrailexpress.event.OnGameEnd;
import io.wifi.starrailexpress.event.OnKillPlayerTriggered;
import io.wifi.starrailexpress.event.OnRoleSkillUse;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.modes.SREMurderGameMode;
import io.wifi.starrailexpress.util.SRENetworkMessageUtils;
import io.wifi.starrailexpress.util.TrueFalseResult;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.exmo.sre.meeting.MeetingManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.game.modifier.NRModifiers;
import org.agmas.noellesroles.init.ModEffects;
// import org.agmas.noellesroles.packet.FakeSteveHuntS2CPacket;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.utils.RoleUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Server authority for the Fake Steve faction. The modifier is the durable
 * player-facing marker; this class owns round-local decisions and AI state.
 */
public final class FakeSteveDirector {
    public static final String WINNER_ID = "fake_steve";
    private static final int CONTROL_EFFECT_TICKS = 30;
    /** Longer than any round, without synchronizing the psycho component every tick. */
    private static final int PERMANENT_PSYCHO_TICKS = Integer.MAX_VALUE;
    private static final Map<ResourceLocation, Session> SESSIONS = new HashMap<>();
    private static boolean registered;

    private FakeSteveDirector() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        FakeSteveAi.register();
        FakeSteveTrailRecorder.register();

        OnGameEnd.EVENT.register((level, game) -> clear(level));
        ServerTickEvents.END_WORLD_TICK.register(FakeSteveDirector::tick);
        // 手控只开放身体，不把原职业技能还给玩家（包括忽略技能禁用效果的调用路径）。
        OnRoleSkillUse.BEFORE.register((player, role) -> !isReplaced(player)
                || !(player instanceof ServerPlayer serverPlayer) || !isManualControl(serverPlayer));

        OnKillPlayerTriggered.EVENT.register((victim, spawnBody, killer, reason, force) -> {
            if (!(victim instanceof ServerPlayer serverPlayer)) {
                return TrueFalseResult.PASS;
            }
            boolean enabled = isActive(serverPlayer.serverLevel())
                    && canGenerate(serverPlayer.serverLevel());

            if (killer == null
                    && reason != null
                    && GameConstants.DeathReasons.SHOT_INNOCENT.getPath().equals(reason.getPath())
                    && enabled) {
                replace(serverPlayer, ReplacementCause.TEAMKILL);
                return TrueFalseResult.FALSE;
            }
            return TrueFalseResult.PASS;
        });

        // 只在最终真实死亡后排队；不可在死亡调用栈中复活，后续仍可能写回死亡状态。
        OnPlayerDeathWithKiller.FINAL_EVENT.register((victim, killer, reason) -> {
            if (!(victim instanceof ServerPlayer player) || !isReallyDead(player)) {
                return;
            }
            ServerLevel level = player.serverLevel();
            Session session = session(level);
            if (session == null) return;
            if (isReplaced(player)) {
                finishManualControl(player, session, true);
                return;
            }
            if (!session.active || session.victoryDeclared || !canGenerate(level)
                    || !isRoundAcceptingCommands(level)
                    || SREGameWorldComponent.KEY.get(level).getRole(player) == null) return;
            if (killer != null && killer != player && killer.level() == level && isReplaced(killer)) {
                session.pendingDeathRevives.remove(player.getUUID());
                session.deathReviveAttempted.add(player.getUUID());
                session.pendingInfections.putIfAbsent(player.getUUID(),
                        new PendingInfection(player, player.position(), level.getGameTime() + 1L));
            } else if (session.deathReviveAttempted.add(player.getUUID())) {
                session.pendingDeathRevives.put(player.getUUID(),
                        level.getGameTime() + FakeSteveRules.DEATH_REVIVE_DELAY_TICKS);
            }
        });

        AllowGameEnd.EVENT_END.register((level, proposed, looseEnds) -> {
            Session session = session(level);
            if (session == null || !session.active || session.victoryDeclared) {
                return GameUtils.WinStatus.NOT_MODIFY;
            }
            // 给下一 tick 的感染转换留下机会；超时结算仍可正常结束本局。
            if (!session.pendingInfections.isEmpty() && proposed != GameUtils.WinStatus.TIME) {
                return GameUtils.WinStatus.NONE;
            }
            if (!session.huntPhase && shouldStartHunt(level, session)) {
                startHunt(level, session);
            }
            if (session.huntPhase) {
                if (FakeSteveRules.shouldDeclareHuntVictory(livingHumanCount(level))) {
                    declareVictory(level, session);
                    return GameUtils.WinStatus.CUSTOM;
                }
                // While at least one possessed body remains, ordinary team win
                // conditions cannot cut the hunt short. Timeouts and a wiped
                // fake faction fall back to the normal game verdict.
                if (livingFakeCount(level) > 0 && proposed != GameUtils.WinStatus.TIME) {
                    return GameUtils.WinStatus.NONE;
                }
            }
            return GameUtils.WinStatus.NOT_MODIFY;
        });

        AllowPlayerWin.EVENT.register((level, player, role, status, roundEnd, game) -> {
            if (status != GameUtils.WinStatus.CUSTOM || roundEnd == null
                    || !WINNER_ID.equals(roundEnd.CustomWinnerID)) {
                return TrueFalseResult.PASS;
            }
            return isReplaced(player) ? TrueFalseResult.TRUE : TrueFalseResult.FALSE;
        });
    }

    /**
     * 假史蒂夫事件的掷骰回调，由 {@code ModRoles.FAKE_STEVE} 通过
     * {@link SRERole#setEventEnableChance(java.util.function.BiConsumer, java.util.function.Consumer, java.util.function.IntSupplier)}
     * 注册（概率读配置 {@code fakeSteveEnableChance}）。
     * <p>
     * 每局正式开局调用一次，<b>没掷中也会调用</b>：总是先按当前存活人数重建本局的 {@link Session}，
     * 掷中且当前模式允许时才激活本局派系事件（{@code active}、首个待触发事件、公告），并据
     * {@code ModRoles.FAKE_STEVE.wasEventForceEnabled()} 区分命令强开与自然掷中。
     * 局末的收尾在 {@link #onEventRoundEnd(ServerLevel)}。
     */
    public static void onEventRollResult(ServerLevel level, boolean enabled) {
        if (!(SREGameWorldComponent.KEY.get(level).getGameMode() instanceof SREMurderGameMode)) {
            return;
        }
        Session session = resetSession(level);
        if (canGenerate(level) && enabled) {
            boolean forced = ModRoles.FAKE_STEVE.wasEventForceEnabled();
            SRE.LOGGER.info(forced
                    ? "[Fake Steve] Event is enabled by next-round command!"
                    : "[Fake Steve] Event is enabled!");
            session.active = true;
            session.pendingEvents = 1;
            session.activationSource = forced
                    ? ActivationSource.COMMAND_NEXT
                    : ActivationSource.NATURAL_ROLL;
            announceNaturalEvent(level);
        }
    }

    /**
     * 假史蒂夫事件的局末回调：只在本局掷中过时调用，把本局 Session 复位成未激活。
     * <p>
     * 真正的清收（取消幻象、清理被替换者的控制器）仍由 {@code OnGameEnd} 上的
     * {@link #clear(ServerLevel)} 负责，这里只保证状态不跨局残留。
     */
    public static void onEventRoundEnd(ServerLevel level) {
        if (!(SREGameWorldComponent.KEY.get(level).getGameMode() instanceof SREMurderGameMode)) {
            return;
        }
        resetSession(level);
    }

    /** 按当前存活人数重建本局的 {@link Session}（未激活），返回新实例。 */
    private static Session resetSession(ServerLevel level) {
        int startingPlayers = (int) level.getPlayers((p) -> GameUtils.isPlayerAliveAndSurvival(p)).stream().count();
        Session session = new Session(level, startingPlayers);
        Session previous = SESSIONS.put(level.dimension().location(), session);
        if (previous != null) {
            previous.pendingDeathRevives.clear();
            previous.deathReviveAttempted.clear();
            previous.pendingInfections.clear();
            clearSessionControls(level, previous);
        }
        return session;
    }

    public static boolean isEnabled() {
        return !Noellesroles.isRoleDisabled(ModRoles.FAKE_STEVE);
    }

    public static boolean canGenerate(ServerLevel level) {
        if (level == null || !isEnabled()) {
            return false;
        }
        return SREGameWorldComponent.KEY.get(level).getGameMode() instanceof SREMurderGameMode;
    }

    public static boolean isActive(ServerLevel level) {
        Session session = session(level);
        return session != null && session.active;
    }

    /** True after the 60% threshold has converted the round into the final hunt. */
    public static boolean isHuntPhase(ServerLevel level) {
        Session session = session(level);
        return session != null && session.huntPhase;
    }

    /** 最终狩猎中伪人对人类的有效攻击统一强杀，AI 与手控共用。 */
    public static boolean isForcedHuntAttack(Player attacker, Player victim) {
        if (!(attacker instanceof ServerPlayer fake) || !(victim instanceof ServerPlayer human)
                || fake == human || fake.serverLevel() != human.serverLevel()
                || !GameUtils.isPlayerAliveAndSurvival(fake) || !GameUtils.isPlayerAliveAndSurvival(human)
                || !isReplaced(fake) || isReplaced(human)) {
            return false;
        }
        ServerLevel level = fake.serverLevel();
        Session session = session(level);
        return session != null && session.active && session.huntPhase && !session.victoryDeclared
                && canGenerate(level) && isRoundAcceptingCommands(level) && !MeetingManager.isActive();
    }

    public static boolean activate(ServerLevel level, ActivationSource source) {
        if (!canGenerate(level) || !isRoundAcceptingCommands(level)) {
            return false;
        }
        Session session = session(level);
        if (session == null) {
            return false;
        }
        session.active = true;
        session.activationSource = source;
        return true;
    }

    /** Marks the next murder round on this world to start the Fake Steve event. */
    public static boolean forceNextRound(ServerLevel level) {
        if (!canGenerate(level)) {
            return false;
        }
        return ModRoles.FAKE_STEVE.forceEventEnableNextRound();
    }

    /** 是否已经排过「强制下一局」（事件状态维度通用，因此不需要世界参数）。 */
    public static boolean isNextRoundForced() {
        return ModRoles.FAKE_STEVE.isEventForcePending();
    }

    public static boolean queueApparition(ServerLevel level) {
        if (!activate(level, ActivationSource.COMMAND_EVENT)) {
            return false;
        }
        session(level).pendingEvents++;
        return true;
    }

    /** Implemented by the apparition networking slice. */
    public static boolean spawnApparition(ServerPlayer target) {
        if (target == null || !isValidTarget(target)
                || !activate(target.serverLevel(), ActivationSource.COMMAND_SPAWN)) {
            return false;
        }
        return FakeSteveApparitions.spawnFor(target, true);
    }

    public static boolean replace(ServerPlayer player, ReplacementCause cause) {
        if (player == null || !canGenerate(player.serverLevel()) || !isValidTarget(player)) {
            return false;
        }
        Session session = session(player.serverLevel());
        if (session == null) {
            return false;
        }
        if (cause == ReplacementCause.COMMAND) {
            if (!activate(player.serverLevel(), ActivationSource.COMMAND_REPLACE)) {
                return false;
            }
        } else if (!session.active) {
            return false;
        }

        SRENetworkMessageUtils.sendBroadcast(player, Component
                .translatable("message.noellesroles.fake_steve.victim.replaced").withStyle(ChatFormatting.RED));
        SRENetworkMessageUtils.sendCODSubtitleToPlayer(player, Component
                .translatable("message.noellesroles.fake_steve.victim.replaced").withStyle(ChatFormatting.RED));
        SRENetworkMessageUtils.sendActionbar(player, Component
                .translatable("message.noellesroles.fake_steve.victim.replaced").withStyle(ChatFormatting.RED));
        WorldModifierComponent modifiers = WorldModifierComponent.KEY.get(player.serverLevel());
        modifiers.addModifier(player.getUUID(), NRModifiers.FAKE_STEVE_REPLACED);
        // 回放事件：记录「XX 被伪人替代」，只进回放时间线，不做全服播报（替代本身对其他玩家是隐藏信息）
        SRE.REPLAY_MANAGER.recordCustomEvent(
                Component.translatable("replay.event.fake_steve.replaced",
                        GameReplayUtils.getReplayPlayerDisplayText(player, true)));
        var originalRole = SREGameWorldComponent.KEY.get(player.serverLevel()).getRole(player);
        if (originalRole != null && originalRole.canUseKiller()) {
            SREPlayerShopComponent.KEY.get(player).addToBalance(200);
        }
        FakeSteveAgentState agentState = new FakeSteveAgentState(player.getUUID(), cause);
        // 把「生前」的移动路线交给 AI：替换后空闲时优先沿它巡逻，
        // 不再依赖 A*（见 FakeSteveAi#driveTrail），也就不会再卡在原地转圈。
        FakeSteveTrailRecorder.capture(player, agentState);
        session.agents.put(player.getUUID(), agentState);
        session.manualControls.put(player.getUUID(), new FakeSteveManualControl());
        player.sendSystemMessage(Component.translatable("message.noellesroles.fake_steve.control.hint"));
        applyControl(player);
        checkVictory(player.serverLevel(), session);
        return true;
    }

    public static boolean isReplaced(Player player) {
        return player != null && WorldModifierComponent.KEY.get(player.level())
                .isModifier(player, NRModifiers.FAKE_STEVE_REPLACED);
    }

    /** 服务端唯一的手控放行条件；网络输入不能靠客户端效果或自报状态取得控制权。 */
    public static boolean isManualControl(ServerPlayer player) {
        Session session = session(player.serverLevel());
        FakeSteveManualControl control = session == null ? null : session.manualControls.get(player.getUUID());
        return session != null && session.active && !session.victoryDeclared
                && canGenerate(player.serverLevel()) && isRoundAcceptingCommands(player.serverLevel())
                && !MeetingManager.isActive() && GameUtils.isPlayerAliveAndSurvival(player)
                && isReplaced(player) && control != null && control.isActive(player.serverLevel().getGameTime());
    }

    /** 返回 true 表示已消费伪人的技能输入，失败、冷却中或死亡时也不得落到原职业技能。 */
    public static boolean handleAbilityInput(ServerPlayer player) {
        if (!isReplaced(player)) return false;
        ServerLevel level = player.serverLevel();
        Session session = session(level);
        if (session == null || !session.active || session.victoryDeclared || !canGenerate(level)
                || !isRoundAcceptingCommands(level) || MeetingManager.isActive()
                || !GameUtils.isPlayerAliveAndSurvival(player) || !session.agents.containsKey(player.getUUID())) {
            player.displayClientMessage(Component.translatable("message.noellesroles.fake_steve.control.unavailable"), true);
            return true;
        }
        long now = level.getGameTime();
        FakeSteveManualControl control = session.manualControls.get(player.getUUID());
        if (control == null) return true;
        if (control.hasControl() && !control.isActive(now)) finishManualControl(player, session, false);
        if (control.isActive(now)) {
            player.displayClientMessage(Component.translatable("message.noellesroles.fake_steve.control.active",
                    (control.remainingTicks(now) + 19L) / 20L), true);
        } else if (!control.start(now)) {
            player.displayClientMessage(Component.translatable("message.noellesroles.fake_steve.control.cooldown",
                    (control.cooldownTicks(now) + 19L) / 20L), true);
        } else {
            FakeSteveMotionController.clear(player, session.agents.get(player.getUUID()));
            applyManualControl(player);
            player.displayClientMessage(Component.translatable("message.noellesroles.fake_steve.control.started"), true);
        }
        return true;
    }

    private static void finishManualControl(ServerPlayer player, Session session, boolean cancelled) {
        FakeSteveManualControl control = session.manualControls.get(player.getUUID());
        long now = session.level.getGameTime();
        if (control == null || !control.finish(now)) return;
        FakeSteveAgentState state = session.agents.get(player.getUUID());
        FakeSteveMotionController.clear(player, state);
        if (state != null) {
            state.path.clear();
            state.pathGoal = null;
            state.nextPathTick = now;
            FakeSteveAi.resetStuckTracking(player, state, now);
        }
        if (GameUtils.isPlayerAliveAndSurvival(player) && player.serverLevel() == session.level) applyControl(player);
        else removeControl(player);
        player.displayClientMessage(Component.translatable(cancelled
                ? "message.noellesroles.fake_steve.control.cancelled"
                : "message.noellesroles.fake_steve.control.ended"), true);
    }

    /** 仅身体交还给玩家；技能禁用保留，以阻断原职业的专用发包入口。 */
    private static void applyManualControl(ServerPlayer player) {
        player.removeEffect(ModEffects.MOVE_BANED);
        player.removeEffect(ModEffects.USED_BANED);
        player.removeEffect(ModEffects.INVENTORY_BANED);
        player.removeEffect(ModEffects.TURN_BANED);
        player.removeEffect(ModEffects.VOICE_SILENCE);
        player.removeEffect(ModEffects.CHAT_BAN);
        addControl(player, ModEffects.SKILL_BANED);
    }

    public static int pendingEvents(ServerLevel level) {
        Session session = session(level);
        return session == null ? 0 : session.pendingEvents;
    }

    public static int startingPlayers(ServerLevel level) {
        Session session = session(level);
        return session == null ? 0 : session.startingPlayers;
    }

    public static FakeSteveAgentState agent(ServerLevel level, UUID player) {
        Session session = session(level);
        return session == null ? null : session.agents.get(player);
    }

    static void consumePendingEvent(ServerLevel level) {
        Session session = session(level);
        if (session != null && session.pendingEvents > 0) {
            session.pendingEvents--;
        }
    }

    static void requeuePendingEvent(ServerLevel level) {
        Session session = session(level);
        if (session != null && session.active) {
            session.pendingEvents++;
        }
    }

    /** 排除分裂人格、回溯等仍被游戏判定为存活的旁观状态。 */
    private static boolean isReallyDead(ServerPlayer player) {
        return player.isSpectator() && !GameUtils.isPlayerAliveAndSurvival(player);
    }

    /** 当前局下一 tick 在死亡位置复活并感染，AI 与手控击杀共用同一最终死亡入口。 */
    private static void tickInfections(ServerLevel level, Session session) {
        if (session(level) != session || !session.active || session.victoryDeclared
                || !canGenerate(level) || !isRoundAcceptingCommands(level) || MeetingManager.isActive()) {
            session.pendingInfections.clear();
            return;
        }
        for (UUID id : Set.copyOf(session.pendingInfections.keySet())) {
            PendingInfection pending = session.pendingInfections.get(id);
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
            if (player != pending.player() || player.serverLevel() != level || !isReallyDead(player)
                    || isReplaced(player) || SREGameWorldComponent.KEY.get(level).getRole(player) == null) {
                session.pendingInfections.remove(id);
                continue;
            }
            if (level.getGameTime() < pending.dueTick()) continue;
            session.pendingInfections.remove(id);
            Vec3 pos = pending.position();
            GameUtils.revivePlayer(player, pos.x, pos.y, pos.z);
            if (session(level) != session || session.victoryDeclared || !isRoundAcceptingCommands(level)
                    || !GameUtils.isPlayerAliveAndSurvival(player)) continue;
            clearPlayerBodies(level, player);
            if (replace(player, ReplacementCause.INFECTION)) {
                player.sendSystemMessage(Component.translatable("message.noellesroles.fake_steve.infected"));
            }
        }
    }
    /** 仅推进当前局的计时；提前复活、离线或换世界即作废，结算后不重复掷骰。 */
    private static void tickDeathRevives(ServerLevel level, Session session) {
        if (session(level) != session || !session.active || session.victoryDeclared
                || !canGenerate(level) || !isRoundAcceptingCommands(level)) {
            session.pendingDeathRevives.clear();
            return;
        }
        for (UUID id : Set.copyOf(session.pendingDeathRevives.keySet())) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
            if (player == null || player.serverLevel() != level || !isReallyDead(player) || isReplaced(player)) {
                session.pendingDeathRevives.remove(id);
                continue;
            }
            if (level.getGameTime() < session.pendingDeathRevives.get(id)) {
                continue;
            }
            session.pendingDeathRevives.remove(id);
            tryReviveDeadPlayer(level, session, player);
        }
    }

    /** 35 秒后仍真正死亡时按 10% 概率在自己房间复活并被伪人夺舍；上下文变化均取消。 */
    private static void tryReviveDeadPlayer(ServerLevel level, Session session, ServerPlayer player) {
        if (session(level) != session || !session.active || session.victoryDeclared
                || !canGenerate(level) || !isRoundAcceptingCommands(level) || MeetingManager.isActive()
                || player.serverLevel() != level || !isReallyDead(player) || isReplaced(player)
                || SREGameWorldComponent.KEY.get(level).getRole(player) == null) {
            return;
        }
        if (GameUtils.getPlayerRoomSpawnPos(player) == null
                || !FakeSteveRules.shouldReviveAfterDeath(level.random.nextInt(100))) {
            return;
        }
        GameUtils.revivePlayerToItsRoom(player);
        if (!GameUtils.isPlayerAliveAndSurvival(player)) return;
        clearPlayerBodies(level, player);
        // 复活只恢复玩家实体，必须走正式夺舍入口才能登记修饰符、AI 和控制状态。
        if (session(level) != session || session.victoryDeclared || !isRoundAcceptingCommands(level)
                || !replace(player, ReplacementCause.DEATH_REVIVE)) return;
        SRENetworkMessageUtils.sendActionbar(player, Component
                .translatable("message.noellesroles.fake_steve.death.revive")
                .withStyle(ChatFormatting.RED));
    }

    /** 快照遍历后清掉该玩家在场上的全部旧尸体，不影响其他玩家尸体。 */
    private static void clearPlayerBodies(ServerLevel level, ServerPlayer player) {
        ArrayList<PlayerBodyEntity> bodies = new ArrayList<>();
        for (var entity : level.getAllEntities()) {
            if (entity instanceof PlayerBodyEntity body && player.getUUID().equals(body.getPlayerUuid())) {
                bodies.add(body);
            }
        }
        bodies.forEach(PlayerBodyEntity::discard);
    }

    private static void tick(ServerLevel level) {
        Session session = session(level);
        if (session == null) {
            return;
        }
        boolean generating = canGenerate(level);
        if (!generating) {
            session.pendingEvents = 0;
            // 事件已不可生成：丢弃待处理的死亡复活计时，避免跨局残留。
            session.pendingDeathRevives.clear();
            session.pendingInfections.clear();
            FakeSteveApparitions.cancelAll(level);
        }
        if (!generating || !session.active || session.victoryDeclared
                || SREGameWorldComponent.KEY.get(level).getGameStatus() != SREGameWorldComponent.GameStatus.ACTIVE) {
            // 本局尚未开始或已经结束：计时器一律作废。
            session.pendingDeathRevives.clear();
            session.pendingInfections.clear();
            clearSessionControls(level, session);
            return;
        }
        tickInfections(level, session);
        if (session(level) != session || session.victoryDeclared || !isRoundAcceptingCommands(level)) return;
        tickDeathRevives(level, session);

        if (generating) {
            FakeSteveApparitions.tick(level, session.pendingEvents > 0);
        }
        if (session.huntPhase && FakeSteveRules.shouldRecallHuntPlayers(level.getGameTime(),
                session.nextHuntRecallTick)) {
            recallHuntPlayers(level, session, true);
            session.nextHuntRecallTick = level.getGameTime() + FakeSteveRules.HUNT_ROOM_RECALL_INTERVAL_TICKS;
        }
        for (UUID id : Set.copyOf(session.agents.keySet())) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
            FakeSteveManualControl control = session.manualControls.get(id);
            if (player == null || player.serverLevel() != level) {
                if (control != null && control.hasControl()) {
                    if (player != null) finishManualControl(player, session, true);
                    else control.finish(level.getGameTime());
                }
                continue;
            }
            if (control != null && control.hasControl()
                    && (!control.isActive(level.getGameTime()) || MeetingManager.isActive()
                            || !GameUtils.isPlayerAliveAndSurvival(player))) {
                finishManualControl(player, session, control.isActive(level.getGameTime()));
            }
            if (GameUtils.isPlayerAliveAndSurvival(player)) {
                // 会议期间：伪人作为参会者坐在自己的席位上，AI 完全暂停（不移动、不行为、
                // 不判定卡住）。控制类效果照旧刷新，避免禁言/禁聊在会议中失效。
                if (MeetingManager.isActive()) {
                    applyControl(player);
                    FakeSteveMotionController.clear(player, session.agents.get(id));
                    // 会议静止不计入 AI 卡住时长，散会后重新开始追踪。
                    FakeSteveAi.resetStuckTracking(player, session.agents.get(id), level.getGameTime());
                    continue;
                }
                if (session.huntPhase) {
                    enforcePermanentPsycho(player);
                }
                if (isManualControl(player)) {
                    applyManualControl(player);
                    if (level.getGameTime() % 20L == 0L) {
                        player.displayClientMessage(Component.translatable("message.noellesroles.fake_steve.control.active",
                                (control.remainingTicks(level.getGameTime()) + 19L) / 20L), true);
                    }
                    continue;
                }
                applyControl(player);
                FakeSteveMotionController.applyServerMotion(player, session.agents.get(id));
                FakeSteveAi.tick(level, player, session.agents.get(id));
            } else {
                FakeSteveMotionController.clear(player, session.agents.get(id));
                removeControl(player);
            }
        }
        if (level.getGameTime() % 20L == 0L) {
            checkVictory(level, session);
        }
    }

    private static void applyControl(ServerPlayer player) {
        addControl(player, ModEffects.MOVE_BANED);
        addControl(player, ModEffects.USED_BANED);
        addControl(player, ModEffects.INVENTORY_BANED);
        addControl(player, ModEffects.SKILL_BANED);
        addControl(player, ModEffects.TURN_BANED);
        addControl(player, ModEffects.VOICE_SILENCE);
        addControl(player, ModEffects.CHAT_BAN);
    }

    private static void addControl(ServerPlayer player,
            net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect) {
        player.addEffect(new MobEffectInstance(effect, CONTROL_EFFECT_TICKS, 0, false, false, false));
    }

    private static void removeControl(ServerPlayer player) {
        player.removeEffect(ModEffects.MOVE_BANED);
        player.removeEffect(ModEffects.USED_BANED);
        player.removeEffect(ModEffects.INVENTORY_BANED);
        player.removeEffect(ModEffects.SKILL_BANED);
        player.removeEffect(ModEffects.TURN_BANED);
        player.removeEffect(ModEffects.VOICE_SILENCE);
        player.removeEffect(ModEffects.CHAT_BAN);
    }

    private static void clear(ServerLevel level) {
        Session removed = SESSIONS.remove(level.dimension().location());
        FakeSteveApparitions.cancelAll(level);
        FakeSteveVoiceDetector.clear();
        FakeSteveTrailRecorder.clearAll();
        if (removed != null && removed.huntPhase) {
            sendHuntScene(level, false);
        }
        if (removed != null) {
            removed.pendingDeathRevives.clear();
            removed.deathReviveAttempted.clear();
            removed.pendingInfections.clear();
            removed.manualControls.clear();
            for (UUID id : removed.agents.keySet()) {
                ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
                if (player != null) {
                    FakeSteveMotionController.clear(player, removed.agents.get(id));
                    removeControl(player);
                    if (removed.huntPhase) {
                        player.removeEffect(MobEffects.MOVEMENT_SPEED);
                    }
                }
            }
        }
    }

    private static void clearSessionControls(ServerLevel level, Session session) {
        if (session.manualControls.isEmpty()) return;
        session.manualControls.clear();
        for (UUID id : session.agents.keySet()) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
            if (player != null) {
                FakeSteveMotionController.clear(player, session.agents.get(id));
                removeControl(player);
                if (session.huntPhase) player.removeEffect(MobEffects.MOVEMENT_SPEED);
            }
        }
    }

    private static boolean isValidTarget(ServerPlayer target) {
        return !target.isSpectator() && GameUtils.isPlayerAliveAndSurvival(target) && !isReplaced(target)
                && SREGameWorldComponent.KEY.get(target.serverLevel()).getRole(target) != null;
    }

    private static boolean isRoundAcceptingCommands(ServerLevel level) {
        return SREGameWorldComponent.KEY.get(level).getGameStatus() == SREGameWorldComponent.GameStatus.ACTIVE;
    }

    private static Session snapshot(ServerLevel level) {
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(level);
        int count = 0;
        for (ServerPlayer player : level.players()) {
            SRERole role = game.getRole(player);
            if (role != null) {
                count++;
            }
        }
        return new Session(level, count);
    }

    private static Session session(ServerLevel level) {
        Session session = level == null ? null : SESSIONS.get(level.dimension().location());
        return session != null && session.level == level ? session : null;
    }

    private static int livingFakeCount(ServerLevel level) {
        int count = 0;
        for (ServerPlayer player : level.players()) {
            if (isReplaced(player) && GameUtils.isPlayerAliveAndSurvival(player)) {
                count++;
            }
        }
        return count;
    }

    private static boolean shouldStartHunt(ServerLevel level, Session session) {
        return FakeSteveRules.shouldStartHunt(livingFakeCount(level), livingPlayerCount(level));
    }

    private static void announceNaturalEvent(ServerLevel level) {
        Component title = Component.translatable("message.noellesroles.fake_steve.event.title")
                .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD);
        Component subtitle = Component.translatable("message.noellesroles.fake_steve.event.subtitle")
                .withStyle(ChatFormatting.GRAY);
        Component broadcast = Component.translatable("message.noellesroles.fake_steve.event.broadcast")
                .withStyle(ChatFormatting.RED);
        for (ServerPlayer player : level.players()) {
            SRENetworkMessageUtils.sendTitleTime(player, 10, 80, 20);
            SRENetworkMessageUtils.sendTitle(player, title);
            SRENetworkMessageUtils.sendSubtitle(player, subtitle);
            SRENetworkMessageUtils.sendBroadcast(player, broadcast);
            player.playNotifySound(SoundEvents.SCULK_SHRIEKER_SHRIEK,
                    SoundSource.MASTER, 0.75F, 0.72F);
        }
    }

    private static int livingPlayerCount(ServerLevel level) {
        int count = 0;
        for (ServerPlayer player : level.players()) {
            if (GameUtils.isPlayerAliveAndSurvival(player)) {
                count++;
            }
        }
        return count;
    }

    private static int livingHumanCount(ServerLevel level) {
        int count = 0;
        for (ServerPlayer player : level.players()) {
            if (GameUtils.isPlayerAliveAndSurvival(player) && !isReplaced(player)) {
                count++;
            }
        }
        return count;
    }

    private static void checkVictory(ServerLevel level, Session session) {
        if (session.victoryDeclared || !session.pendingInfections.isEmpty()) {
            return;
        }
        if (!session.huntPhase && shouldStartHunt(level, session)) {
            startHunt(level, session);
        }
        if (session.huntPhase && FakeSteveRules.shouldDeclareHuntVictory(livingHumanCount(level))) {
            declareVictory(level, session);
        }
    }

    /** Teleports the living cast home, then turns every possessed body into a permanent hunter. */
    private static void startHunt(ServerLevel level, Session session) {
        if (session.huntPhase) {
            return;
        }
        session.huntPhase = true;
        session.nextHuntRecallTick = level.getGameTime() + FakeSteveRules.HUNT_ROOM_RECALL_INTERVAL_TICKS;
        Component title = Component.translatable("message.noellesroles.fake_steve.hunt.title")
                .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD);
        Component subtitle = Component.translatable("message.noellesroles.fake_steve.hunt.subtitle")
                .withStyle(ChatFormatting.RED);
        Component broadcast = Component.translatable("message.noellesroles.fake_steve.hunt.broadcast")
                .withStyle(ChatFormatting.RED);
        recallHuntPlayers(level, session, false);
        sendHuntScene(level, true);
        for (ServerPlayer player : level.players()) {
            if (!GameUtils.isPlayerAliveAndSurvival(player)) {
                continue;
            }
            SRENetworkMessageUtils.sendTitleTime(player, 10, 80, 20);
            SRENetworkMessageUtils.sendTitle(player, title);
            SRENetworkMessageUtils.sendSubtitle(player, subtitle);
            SRENetworkMessageUtils.sendBroadcast(player, broadcast);
            player.playNotifySound(SoundEvents.WITHER_SPAWN, SoundSource.MASTER, 0.7F, 0.75F);
        }
    }

    /** Reunites the living cast in their own rooms and clears stale AI routes before every hunt cycle. */
    private static void recallHuntPlayers(ServerLevel level, Session session, boolean announce) {
        for (ServerPlayer player : level.players()) {
            if (!GameUtils.isPlayerAliveAndSurvival(player)) {
                continue;
            }
            GameUtils.teleportBackToRoom(player);
            if (isReplaced(player)) {
                enforcePermanentPsycho(player);
                resetHuntAgent(session.agents.get(player.getUUID()), level.getGameTime());
            }
            if (announce) {
                SRENetworkMessageUtils.sendActionbar(player, Component
                        .translatable("message.noellesroles.fake_steve.hunt.recall")
                        .withStyle(ChatFormatting.DARK_RED));
            }
        }
    }

    private static void resetHuntAgent(FakeSteveAgentState state, long gameTime) {
        if (state == null) {
            return;
        }
        state.path.clear();
        state.pathGoal = null;
        state.focusTarget = null;
        state.committedTarget = null;
        state.ambushGoal = null;
        state.ambushTarget = null;
        state.nextPathTick = gameTime;
        // 被拉回房间后轨迹下标已失效：清掉计时与朝向，下一 tick 会重新吸附到最近的轨迹点。
        state.trailWaypointTick = 0L;
        state.hasStableRouteYaw = false;
        state.brain.disengage();
    }

    private static void sendHuntScene(ServerLevel level, boolean active) {
        // FakeSteveHuntS2CPacket packet = new FakeSteveHuntS2CPacket(active);
        // for (ServerPlayer player : level.players()) {
        //     ServerPlayNetworking.send(player, packet);
        // }
    }

    private static void enforcePermanentPsycho(ServerPlayer player) {
        SREPlayerPsychoComponent psycho = SREPlayerPsychoComponent.KEY.get(player);
        if (!psycho.inPsycho()) {
            psycho.startPsycho_time(PERMANENT_PSYCHO_TICKS,
                    GameConstants.getPsychoModeArmour(), true);
        }
        // A pre-existing temporary psycho needs replacing once. The component
        // then has years of ticks remaining, while round cleanup still resets it.
        if (psycho.getPsychoTicks() < PERMANENT_PSYCHO_TICKS / 2) {
            psycho.setPsychoTicks(PERMANENT_PSYCHO_TICKS);
        }
        // Amplifier 1 is Minecraft's Speed II. Renew with the other control
        // effects so it lasts exactly for the hunt and never leaks to a round.
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED,
                CONTROL_EFFECT_TICKS, 1, false, false, false));
    }

    private static void declareVictory(ServerLevel level, Session session) {
        if (session.victoryDeclared) {
            return;
        }
        session.victoryDeclared = true;
        RoleUtils.customWinnerWin(level, GameUtils.WinStatus.CUSTOM, WINNER_ID,
                java.util.OptionalInt.of(ModRoles.FAKE_STEVE.color()));
    }

    private record PendingInfection(ServerPlayer player, Vec3 position, long dueTick) {
    }
    private static final class Session {
        private final int startingPlayers;
        private final ServerLevel level;
        private final Map<UUID, FakeSteveManualControl> manualControls = new HashMap<>();
        private final Map<UUID, PendingInfection> pendingInfections = new HashMap<>();
        private final Map<UUID, FakeSteveAgentState> agents = new HashMap<>();
        /** 只属于当前 Session，局末/重开丢弃，不能跨局执行。 */
        private final Map<UUID, Long> pendingDeathRevives = new HashMap<>();
        private final Set<UUID> deathReviveAttempted = new HashSet<>();
        private boolean active;
        private boolean huntPhase;
        private boolean victoryDeclared;
        private long nextHuntRecallTick;
        private int pendingEvents;
        private ActivationSource activationSource;

        private Session(ServerLevel level, int startingPlayers) {
            this.level = level;
            this.startingPlayers = Math.max(0, startingPlayers);
        }
    }
}
