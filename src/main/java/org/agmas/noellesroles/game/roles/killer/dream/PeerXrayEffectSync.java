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

package org.agmas.noellesroles.game.roles.killer.dream;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.protocol.Packet;
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
 * 把「同级透视」效果<b>等级</b>广播给所有其它客户端。
 *
 * <p>为什么需要它：原版只会把玩家自身的 {@link net.minecraft.world.effect.MobEffect} 下发给他自己，
 * 所以在客户端读其它玩家的 {@code getEffect(PEER_XRAY)} 永远是 {@code null}
 * ——「同等级才互相透视」无从判断。与 {@code BackworldOutlineEffectSync} 同理，
 * 这里把该效果以<b>隐藏粒子与图标</b>的形式广播给其它客户端，仅作信息载体。</p>
 *
 * <p>广播出去的是 {@code ambient=false / showParticles=false / showIcon=false} 的实例，
 * 因此不会在别人的客户端上显示药水图标或粒子；客户端
 * {@code RoleInstinctRegister#peerXrayHighlight} 只读取它的 {@code amplifier}。</p>
 */
public final class PeerXrayEffectSync {

    private PeerXrayEffectSync() {
    }

    /** 刷新间隔（tick）。 */
    private static final int REFRESH_INTERVAL = 10;

    /** 下发到客户端的效果时长，明显大于刷新间隔以避免闪断。 */
    private static final int SYNC_DURATION = 40;

    /** 记录上一次已广播的玩家，便于效果消失时下发移除包。 */
    private static final Map<UUID, Boolean> HAD_EFFECT = new HashMap<>();

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(PeerXrayEffectSync::onServerTick);
    }

    private static void onServerTick(MinecraftServer server) {
        if (server.overworld().getGameTime() % REFRESH_INTERVAL != 0) {
            return;
        }

        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            if (!HAD_EFFECT.isEmpty()) {
                HAD_EFFECT.clear();
            }
            return;
        }

        for (ServerPlayer player : players) {
            MobEffectInstance instance = player.getEffect(ModEffects.PEER_XRAY);
            boolean had = HAD_EFFECT.getOrDefault(player.getUUID(), false);

            if (instance != null) {
                HAD_EFFECT.put(player.getUUID(), true);
                MobEffectInstance hidden = new MobEffectInstance(
                        ModEffects.PEER_XRAY, SYNC_DURATION, instance.getAmplifier(), false, false, false);
                broadcastExcept(players, player, new ClientboundUpdateMobEffectPacket(player.getId(), hidden, false));
            } else if (had) {
                HAD_EFFECT.remove(player.getUUID());
                broadcastExcept(players, player,
                        new ClientboundRemoveMobEffectPacket(player.getId(), ModEffects.PEER_XRAY));
            }
        }
    }

    private static void broadcastExcept(List<ServerPlayer> players, ServerPlayer except, Packet<?> packet) {
        for (ServerPlayer receiver : players) {
            if (receiver == except) {
                continue;
            }
            receiver.connection.send(packet);
        }
    }
}