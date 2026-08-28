package org.agmas.noellesroles.game.fake_steve;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.AllowGameEnd;
import io.wifi.starrailexpress.event.AllowPlayerWin;
import io.wifi.starrailexpress.event.OnGameEnd;
import io.wifi.starrailexpress.event.OnGameTrueStarted;
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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.agmas.noellesroles.game.modifier.NRModifiers;
import org.agmas.noellesroles.init.ModEffects;
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

        OnGameTrueStarted.EVENT.register((level) -> {
            if (!(SREGameWorldComponent.KEY.get(level).getGameMode() instanceof SREMurderGameMode)) {
                return;
            }
            int startingPlayers = (int) level.getPlayers((p) -> GameUtils.isPlayerAliveAndSurvival(p)).stream().count();
            Session session = new Session(startingPlayers);
            SESSIONS.put(level.dimension().location(), session);
            if (canGenerate(level)
                    && level.getRandom().nextInt(10000) <= NoellesRolesConfig.instance().fakeSteveEnableChance) {
                SRE.LOGGER.info("[Fake Steve] Event is enabled!");
                session.active = true;
                session.pendingEvents = 1;
                session.activationSource = ActivationSource.NATURAL_ROLL;
            }
        });

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

        AllowGameEnd.EVENT.register((level, proposed, looseEnds) -> {
            Session session = session(level);
            if (session != null && session.active && !session.victoryDeclared && hasWon(level, session)) {
                declareVictory(level, session);
                return GameUtils.WinStatus.CUSTOM;
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
        session.agents.put(player.getUUID(), new FakeSteveAgentState(player.getUUID(), cause));
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
        for (UUID id : Set.copyOf(session.agents.keySet())) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
            if (player == null || player.serverLevel() != level) {
                continue;
            }
            if (GameUtils.isPlayerAliveAndSurvival(player)) {
                applyControl(player);
                FakeSteveAi.tick(level, player, session.agents.get(id));
            } else {
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
        if (removed != null) {
            for (UUID id : removed.agents.keySet()) {
                ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
                if (player != null) {
                    removeControl(player);
                }
            }
        }
    }

    private static boolean isValidTarget(ServerPlayer target) {
        return GameUtils.isPlayerAliveAndSurvival(target) && !isReplaced(target)
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

    private static boolean hasWon(ServerLevel level, Session session) {
        return FakeSteveRules.hasWon(livingFakeCount(level), session.startingPlayers);
    }

    private static void checkVictory(ServerLevel level, Session session) {
        if (!session.victoryDeclared && hasWon(level, session)) {
            declareVictory(level, session);
        }
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
        private boolean victoryDeclared;
        private int pendingEvents;
        private ActivationSource activationSource;

        private Session(int startingPlayers) {
            this.startingPlayers = Math.max(0, startingPlayers);
        }
    }
}
