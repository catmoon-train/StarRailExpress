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

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import org.agmas.noellesroles.init.ModEffects;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Temporary Fake Steve locomotion for 失心症.
 * Does not apply the impostor modifier, apparitions, staring, or hunt.
 */
public final class AphreniaFakeSteveControl {
    private static final int CONTROL_EFFECT_TICKS = 30;
    private static final Map<UUID, FakeSteveAgentState> AGENTS = new ConcurrentHashMap<>();

    private AphreniaFakeSteveControl() {
    }

    public static boolean isControlling(UUID playerId) {
        return AGENTS.containsKey(playerId);
    }

    public static void start(ServerPlayer player) {
        if (player == null) {
            return;
        }
        FakeSteveAgentState existing = AGENTS.get(player.getUUID());
        if (existing != null) {
            applyControl(player);
            return;
        }
        FakeSteveAgentState state = new FakeSteveAgentState(player.getUUID(), ReplacementCause.COMMAND);
        state.mode = AgentMode.DISGUISE_IDLE;
        AGENTS.put(player.getUUID(), state);
        applyControl(player);
    }

    public static void tick(ServerPlayer player) {
        if (player == null) {
            return;
        }
        FakeSteveAgentState state = AGENTS.get(player.getUUID());
        if (state == null) {
            return;
        }
        applyControl(player);
        FakeSteveAi.tickWanderOnly(player.serverLevel(), player, state);
    }

    public static void stop(ServerPlayer player) {
        if (player == null) {
            return;
        }
        FakeSteveAgentState state = AGENTS.remove(player.getUUID());
        if (state != null) {
            FakeSteveMotionController.clear(player, state);
        }
        removeControl(player);
    }

    public static void clear() {
        AGENTS.clear();
    }

    private static void applyControl(ServerPlayer player) {
        addControl(player, ModEffects.MOVE_BANED);
        addControl(player, ModEffects.TURN_BANED);
        addControl(player, ModEffects.USED_BANED);
        addControl(player, ModEffects.INVENTORY_BANED);
        addControl(player, ModEffects.SKILL_BANED);
    }

    private static void addControl(ServerPlayer player,
            net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect) {
        player.addEffect(new MobEffectInstance(effect, CONTROL_EFFECT_TICKS, 0, false, false, false));
    }

    private static void removeControl(ServerPlayer player) {
        removeIfOurs(player, ModEffects.MOVE_BANED);
        removeIfOurs(player, ModEffects.TURN_BANED);
        removeIfOurs(player, ModEffects.USED_BANED);
        removeIfOurs(player, ModEffects.INVENTORY_BANED);
        removeIfOurs(player, ModEffects.SKILL_BANED);
    }

    private static void removeIfOurs(ServerPlayer player,
            net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect) {
        MobEffectInstance instance = player.getEffect(effect);
        if (instance != null && instance.getDuration() > 0
                && instance.getDuration() <= CONTROL_EFFECT_TICKS) {
            player.removeEffect(effect);
        }
    }
}
