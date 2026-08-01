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

package org.agmas.noellesroles.game.backworld;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.protocol.game.ClientboundRemoveMobEffectPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import org.agmas.noellesroles.init.ModEffects;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 把「里世界·同界描边」标记同步给所有其它客户端。
 *
 * <p>原版只会把玩家自身的 MobEffect 下发给他自己。而
 * {@code BackworldOutlineGlowMixin} 需要在<b>每个客户端</b>判断
 * 「目标玩家是否处于里世界」，因此必须把 {@link ModEffects#BACKWORLD_OUTLINE}
 * 广播出去（隐藏粒子与图标，仅作信息载体）。</p>
 *
 * <p>使用方：怀旧者里世界、布袋鬼里世界、归途旅人「旧日渡口 / 末班车」。</p>
 */
public final class BackworldOutlineEffectSync {

    private BackworldOutlineEffectSync() {
    }

    /** 刷新间隔（tick）。小于下发时长，保证不会在两次刷新之间过期。 */
    private static final int REFRESH_INTERVAL = 10;

    /** 下发到客户端的效果时长，需明显大于刷新间隔以避免闪断。 */
    private static final int SYNC_DURATION = 40;

    /** 记录上一次已广播描边标记的玩家，便于在效果消失时下发移除包。 */
    private static final Map<UUID, Boolean> HAD_EFFECT = new HashMap<>();

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(BackworldOutlineEffectSync::onServerTick);
    }

    private static void onServerTick(MinecraftServer server) {
        if (server.overworld().getGameTime() % REFRESH_INTERVAL != 0) {
            return;
        }

        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            if (!HAD_EFFECT.isEmpty()) HAD_EFFECT.clear();
            return;
        }

        for (ServerPlayer player : players) {
            MobEffectInstance instance = player.getEffect(ModEffects.BACKWORLD_OUTLINE);
            boolean had = HAD_EFFECT.getOrDefault(player.getUUID(), false);

            if (instance != null) {
                HAD_EFFECT.put(player.getUUID(), true);
                MobEffectInstance hidden = new MobEffectInstance(
                        ModEffects.BACKWORLD_OUTLINE, SYNC_DURATION, instance.getAmplifier(), false, false, false);
                ClientboundUpdateMobEffectPacket update =
                        new ClientboundUpdateMobEffectPacket(player.getId(), hidden, false);
                broadcastExcept(players, player, update);
            } else if (had) {
                HAD_EFFECT.remove(player.getUUID());
                ClientboundRemoveMobEffectPacket remove =
                        new ClientboundRemoveMobEffectPacket(player.getId(), ModEffects.BACKWORLD_OUTLINE);
                broadcastExcept(players, player, remove);
            }
        }
    }

    private static void broadcastExcept(List<ServerPlayer> players, ServerPlayer except,
                                        net.minecraft.network.protocol.Packet<?> packet) {
        for (ServerPlayer receiver : players) {
            if (receiver == except) continue;
            receiver.connection.send(packet);
        }
    }
}
