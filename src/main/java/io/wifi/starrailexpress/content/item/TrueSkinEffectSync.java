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

package io.wifi.starrailexpress.content.item;

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


public final class TrueSkinEffectSync {

    private TrueSkinEffectSync() {
    }

    /** 刷新间隔（tick）。小于客户端效果时长，保证不会在两次刷新之间过期。 */
    private static final int REFRESH_INTERVAL = 20;

    /** 下发到客户端的效果时长，需明显大于刷新间隔以避免闪断。 */
    private static final int SYNC_DURATION = 60;

    /** 记录上一次已广播伪装效果的玩家，便于在效果消失时下发移除包。 */
    public static final Map<UUID, Boolean> HAD_TRUE_SKIN = new HashMap<>();

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(TrueSkinEffectSync::onServerTick);
    }

    private static void onServerTick(MinecraftServer server) {
        if (server.overworld().getGameTime() % REFRESH_INTERVAL != 13) {
            return;
        }

        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            if (!HAD_TRUE_SKIN.isEmpty())
                HAD_TRUE_SKIN.clear();
            return;
        }

        for (ServerPlayer player : players) {
            MobEffectInstance instance = player.getEffect(ModEffects.TRUE_SKIN);
            boolean had = HAD_TRUE_SKIN.getOrDefault(player.getUUID(), false);

            if (instance != null) {
                HAD_TRUE_SKIN.put(player.getUUID(), true);
                // 隐藏粒子/图标，仅作为信息载体广播给其他客户端；保留 amplifier 以选中正确的皮肤变体。
                MobEffectInstance hidden = new MobEffectInstance(
                        ModEffects.TRUE_SKIN, SYNC_DURATION, instance.getAmplifier(), false, false, false);
                ClientboundUpdateMobEffectPacket update = new ClientboundUpdateMobEffectPacket(player.getId(), hidden,
                        false);
                broadcastExcept(players, player, update);
            } else if (had) {
                // 效果刚消失：下发移除包，让其它客户端清掉。
                HAD_TRUE_SKIN.remove(player.getUUID());
                ClientboundRemoveMobEffectPacket remove = new ClientboundRemoveMobEffectPacket(player.getId(),
                        ModEffects.TRUE_SKIN);
                broadcastExcept(players, player, remove);
            }
        }
    }

    private static void broadcastExcept(List<ServerPlayer> players, ServerPlayer except,
            net.minecraft.network.protocol.Packet<?> packet) {
        for (ServerPlayer receiver : players) {
            if (receiver == except)
                continue;
            receiver.connection.send(packet);
        }
    }
}
