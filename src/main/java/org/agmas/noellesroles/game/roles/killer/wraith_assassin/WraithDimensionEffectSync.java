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

package org.agmas.noellesroles.game.roles.killer.wraith_assassin;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundRemoveMobEffectPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import org.agmas.noellesroles.init.ModEffects;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class WraithDimensionEffectSync {
    private static final int REFRESH_INTERVAL = 10;
    private static final int SYNC_DURATION = 40;
    private static final Map<String, Boolean> HAD_EFFECT = new HashMap<>();

    private WraithDimensionEffectSync() {
    }

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(WraithDimensionEffectSync::onServerTick);
    }

    private static void onServerTick(MinecraftServer server) {
        if (server.overworld().getGameTime() % REFRESH_INTERVAL != 0) {
            return;
        }
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        syncEffect(players, ModEffects.WRAITH_DIMENSION);
        syncEffect(players, ModEffects.WRAITH_MANIFEST);
    }

    private static void syncEffect(List<ServerPlayer> players, Holder<MobEffect> effect) {
        for (ServerPlayer player : players) {
            MobEffectInstance instance = player.getEffect(effect);
            String key = player.getUUID() + ":" + effect.unwrapKey().map(k -> k.location().toString()).orElse("");
            boolean had = HAD_EFFECT.getOrDefault(key, false);

            if (instance != null) {
                HAD_EFFECT.put(key, true);
                MobEffectInstance hidden = new MobEffectInstance(effect, SYNC_DURATION, instance.getAmplifier(), false,
                        false, false);
                broadcastExcept(players, player, new ClientboundUpdateMobEffectPacket(player.getId(), hidden, false));
            } else if (had) {
                HAD_EFFECT.remove(key);
                broadcastExcept(players, player, new ClientboundRemoveMobEffectPacket(player.getId(), effect));
            }
        }
    }

    private static void broadcastExcept(List<ServerPlayer> players, ServerPlayer except, Packet<?> packet) {
        for (ServerPlayer receiver : players) {
            if (receiver != except) {
                receiver.connection.send(packet);
            }
        }
    }
}
