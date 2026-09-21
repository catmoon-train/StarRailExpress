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
import io.wifi.starrailexpress.cca.SREPlayerPsychoComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.event.AllowGameEnd;
import io.wifi.starrailexpress.event.AllowPlayerWin;
import io.wifi.starrailexpress.event.OnGameEnd;
import io.wifi.starrailexpress.event.OnKillPlayerTriggered;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.modes.SREMurderGameMode;
import io.wifi.starrailexpress.util.SRENetworkMessageUtils;
import io.wifi.starrailexpress.util.TrueFalseResult;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
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
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.game.modifier.NRModifiers;
import org.agmas.noellesroles.init.ModEffects;
// import org.agmas.noellesroles.packet.FakeSteveHuntS2CPacket;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.utils.RoleUtils;

import java.util.HashMap;
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

        AllowGameEnd.EVENT_END.register((level, proposed, looseEnds) -> {
            Session session = session(level);
            if (session == null || !session.active || session.victoryDeclared) {
                return GameUtils.WinStatus.NOT_MODIFY;
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
        Session session = new Session(startingPlayers);
        SESSIONS.put(level.dimension().location(), session);
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
        var originalRole = SREGameWorldComponent.KEY.get(player.serverLevel()).getRole(player);
        if (originalRole != null && originalRole.canUseKiller()) {
            SREPlayerShopComponent.KEY.get(player).addToBalance(200);
        }
        FakeSteveAgentState agentState = new FakeSteveAgentState(player.getUUID(), cause);
        // 把「生前」的移动路线交给 AI：替换后空闲时优先沿它巡逻，
        // 不再依赖 A*（见 FakeSteveAi#driveTrail），也就不会再卡在原地转圈。
        FakeSteveTrailRecorder.capture(player, agentState);
        session.agents.put(player.getUUID(), agentState);
        applyControl(player);
        checkVictory(player.serverLevel(), session);
        return true;
    }

    public static boolean isReplaced(Player player) {
        return player != null && WorldModifierComponent.KEY.get(player.level())
                .isModifier(player, NRModifiers.FAKE_STEVE_REPLACED);
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

    private static void tick(ServerLevel level) {
        Session session = session(level);
        if (session == null) {
            return;
        }
        boolean generating = canGenerate(level);
        if (!generating) {
            session.pendingEvents = 0;
            FakeSteveApparitions.cancelAll(level);
        }
        if (!session.active
                || SREGameWorldComponent.KEY.get(level).getGameStatus() != SREGameWorldComponent.GameStatus.ACTIVE) {
            return;
        }

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
            if (player == null || player.serverLevel() != level) {
                continue;
            }
            if (GameUtils.isPlayerAliveAndSurvival(player)) {
                if (session.huntPhase) {
                    enforcePermanentPsycho(player);
                }
                applyControl(player);
                FakeSteveMotionController.applyServerMotion(player, session.agents.get(id));
                FakeSteveAi.tick(level, player, session.agents.get(id));
                // 最后一层保底：连续 12 秒位置都没变化超过 2 格，直接传送回可用的记录路径点。
                // 放在 tick 之后，这样无论本 tick 走的是哪条行为分支都会被覆盖。
                FakeSteveAi.rescueIfStuck(level, player, session.agents.get(id));
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
        return new Session(count);
    }

    private static Session session(ServerLevel level) {
        return level == null ? null : SESSIONS.get(level.dimension().location());
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
        if (session.victoryDeclared) {
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

    private static final class Session {
        private final int startingPlayers;
        private final Map<UUID, FakeSteveAgentState> agents = new HashMap<>();
        private boolean active;
        private boolean huntPhase;
        private boolean victoryDeclared;
        private long nextHuntRecallTick;
        private int pendingEvents;
        private ActivationSource activationSource;

        private Session(int startingPlayers) {
            this.startingPlayers = Math.max(0, startingPlayers);
        }
    }
}
