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

package org.agmas.noellesroles.init;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundRemoveMobEffectPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 把「只作信息载体」的 MobEffect <b>广播</b>给所有其它客户端。
 *
 * <p>为什么需要它：原版只会把玩家自身的 {@link MobEffect} 下发给他自己。于是客户端在别人身上
 * 读 {@code target.getEffect(...)} 永远是 {@code null}，任何「A 能看到 B 的某个状态」的机制都会失效。
 * 本项目已有两处踩过这个坑：{@code BackworldOutlineEffectSync}（里世界描边）与
 * {@code NostalgistBackworldEffectSync}（怀旧者里世界）。</p>
 *
 * <p>广播出去的是 {@code ambient=false / showParticles=false / showIcon=false} 的实例，
 * 因此<b>不会</b>在别人的客户端上显示药水图标或粒子，仅通过 {@code amplifier} 携带一个整数，
 * 供客户端的高亮 / 隐匿判定读取。</p>
 */
public final class EffectBroadcastSync {

    private EffectBroadcastSync() {
    }

    /** 刷新间隔（tick）。小于下发时长，保证不会在两次刷新之间过期。 */
    private static final int REFRESH_INTERVAL = 10;

    /** 下发到客户端的效果时长，需明显大于刷新间隔以避免闪断。 */
    private static final int SYNC_DURATION = 40;

    /** 需要广播的效果。 */
    private static final List<Holder<MobEffect>> SYNCED = new ArrayList<>();

    /** 记录每个玩家上一次持有哪些广播效果，便于效果消失时只对真正消失的那个下发移除包。 */
    private static final Map<UUID, Set<MobEffect>> HAD_EFFECTS = new HashMap<>();

    /**
     * 登记一个需要广播给所有其它客户端的效果。
     *
     * @param effect 要广播的效果；其 {@code amplifier} 会被原样下发到客户端
     */
    public static void register(Holder<MobEffect> effect) {
        SYNCED.add(effect);
    }

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(EffectBroadcastSync::onServerTick);
    }

    private static void onServerTick(MinecraftServer server) {
        if (SYNCED.isEmpty() || server.overworld().getGameTime() % REFRESH_INTERVAL != 0) {
            return;
        }

        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            if (!HAD_EFFECTS.isEmpty()) {
                HAD_EFFECTS.clear();
            }
            return;
        }

        for (ServerPlayer player : players) {
            // 上一次该玩家持有的广播效果集合；本轮中不再持有的会被移除。
            Set<MobEffect> had = HAD_EFFECTS.get(player.getUUID());
            if (had == null) {
                had = new HashSet<>();
            }

            for (Holder<MobEffect> effect : SYNCED) {
                boolean hadThis = had.remove(effect.value());
                MobEffectInstance instance = player.getEffect(effect);

                if (instance != null) {
                    had.add(effect.value());
                    MobEffectInstance hidden = new MobEffectInstance(
                            effect, SYNC_DURATION, instance.getAmplifier(), false, false, false);
                    broadcastExcept(players, player,
                            new ClientboundUpdateMobEffectPacket(player.getId(), hidden, false));
                } else if (hadThis) {
                    broadcastExcept(players, player,
                            new ClientboundRemoveMobEffectPacket(player.getId(), effect));
                }
            }

            if (had.isEmpty()) {
                HAD_EFFECTS.remove(player.getUUID());
            } else {
                HAD_EFFECTS.put(player.getUUID(), had);
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