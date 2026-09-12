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

import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import org.agmas.noellesroles.init.ModEffects;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 怯懦：附近有人死后一次性接管移动，沿路径跑走。
 */
public final class CowardiceFakeSteveControl {
    private static final int CONTROL_EFFECT_TICKS = 30;
    static final int MAX_FLEE_TICKS = 160;
    static final int SPEED_AMPLIFIER = 3;

    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();
    private static final Set<UUID> USED_THIS_GAME = ConcurrentHashMap.newKeySet();

    private CowardiceFakeSteveControl() {
    }

    public static boolean isControlling(UUID playerId) {
        return SESSIONS.containsKey(playerId);
    }

    public static void tryStart(ServerPlayer player, BlockPos awayFrom) {
        if (player == null || awayFrom == null) {
            return;
        }
        UUID id = player.getUUID();
        if (USED_THIS_GAME.contains(id) || SESSIONS.containsKey(id)) {
            return;
        }
        if (AphreniaFakeSteveControl.isControlling(id)) {
            AphreniaFakeSteveControl.stop(player);
        }
        FakeSteveAgentState state = new FakeSteveAgentState(id, ReplacementCause.COMMAND);
        state.mode = AgentMode.DISGUISE_IDLE;
        SESSIONS.put(id, new Session(state, awayFrom.immutable(), player.level().getGameTime()));
        USED_THIS_GAME.add(id);
        applyBuffs(player);
        applyControl(player);
    }

    public static void tick(ServerPlayer player) {
        if (player == null) {
            return;
        }
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) {
            return;
        }
        if (!GameUtils.isPlayerAliveAndSurvival(player)) {
            stop(player);
            return;
        }
        if (AphreniaFakeSteveControl.isControlling(player.getUUID())) {
            AphreniaFakeSteveControl.stop(player);
        }
        long now = player.level().getGameTime();
        if (now - session.startedTick >= MAX_FLEE_TICKS) {
            stop(player);
            return;
        }
        applyControl(player);
        FakeSteveAi.tickFlee(player.serverLevel(), player, session.state, session.awayFrom);
        if (FakeSteveAi.hasReachedFleeGoal(player, session.state)) {
            stop(player);
        }
    }

    public static void stop(ServerPlayer player) {
        if (player == null) {
            return;
        }
        Session session = SESSIONS.remove(player.getUUID());
        if (session != null) {
            FakeSteveMotionController.clear(player, session.state);
        }
        removeControl(player);
        removeIfOurs(player, ModEffects.NO_COLLIDE, MAX_FLEE_TICKS);
        removeIfOurs(player, MobEffects.MOVEMENT_SPEED, MAX_FLEE_TICKS);
    }

    public static void clear() {
        SESSIONS.clear();
        USED_THIS_GAME.clear();
    }

    private static void applyBuffs(ServerPlayer player) {
        player.addEffect(ModEffects.of(ModEffects.NO_COLLIDE, MAX_FLEE_TICKS, 0, false, false, false));
        player.addEffect(ModEffects.of(MobEffects.MOVEMENT_SPEED, MAX_FLEE_TICKS, SPEED_AMPLIFIER, false, false, true));
    }

    private static void applyControl(ServerPlayer player) {
        addControl(player, ModEffects.MOVE_BANED);
        addControl(player, ModEffects.TURN_BANED);
        addControl(player, ModEffects.USED_BANED);
        addControl(player, ModEffects.INVENTORY_BANED);
        addControl(player, ModEffects.SKILL_BANED);
    }

    private static void addControl(ServerPlayer player, Holder<MobEffect> effect) {
        player.addEffect(new MobEffectInstance(effect, CONTROL_EFFECT_TICKS, 0, false, false, false));
    }

    private static void removeControl(ServerPlayer player) {
        removeIfOurs(player, ModEffects.MOVE_BANED, CONTROL_EFFECT_TICKS);
        removeIfOurs(player, ModEffects.TURN_BANED, CONTROL_EFFECT_TICKS);
        removeIfOurs(player, ModEffects.USED_BANED, CONTROL_EFFECT_TICKS);
        removeIfOurs(player, ModEffects.INVENTORY_BANED, CONTROL_EFFECT_TICKS);
        removeIfOurs(player, ModEffects.SKILL_BANED, CONTROL_EFFECT_TICKS);
    }

    private static void removeIfOurs(ServerPlayer player, Holder<MobEffect> effect, int maxDuration) {
        MobEffectInstance instance = player.getEffect(effect);
        if (instance != null && !instance.isInfiniteDuration()
                && instance.getDuration() > 0 && instance.getDuration() <= maxDuration) {
            player.removeEffect(effect);
        }
    }

    private record Session(FakeSteveAgentState state, BlockPos awayFrom, long startedTick) {
    }
}
