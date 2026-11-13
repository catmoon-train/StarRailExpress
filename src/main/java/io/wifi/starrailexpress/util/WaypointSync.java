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

package io.wifi.starrailexpress.util;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.game.data.WaypointManager;
import io.wifi.starrailexpress.game.data.WaypointVisibilityManager;
import io.wifi.starrailexpress.network.PacketTracker;
import io.wifi.starrailexpress.network.packet.SyncWaypointVisibilityPacket;
import io.wifi.starrailexpress.network.packet.SyncWaypointsPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * 路径点全量同步的统一入口。
 *
 * <p>所有对 {@link WaypointManager} 的增删都应在落库后调用 {@link #syncToAll(MinecraftServer)}，
 * 由服务端把权威全量数据重广播给所有客户端，客户端清空重建（见
 * {@link SyncWaypointsPacket#handle}）。玩家加入时调用 {@link #syncTo(ServerPlayer)} 补一次全量。</p>
 */
public final class WaypointSync {

    private WaypointSync() {
    }

    /** 把当前权威的全量路径点广播给所有在线玩家。 */
    public static void syncToAll(MinecraftServer server) {
        if (server == null) {
            return;
        }
        WaypointManager manager = WaypointManager.get(server);
        SRE.NETWORKING.sendToAllPlayers(new SyncWaypointsPacket(manager.getAllWaypointsMap()));
    }

    /** 把当前权威的全量路径点发送给单个玩家（用于加入时补同步）。 */
    public static void syncTo(ServerPlayer player) {
        if (player == null || player.getServer() == null) {
            return;
        }
        WaypointManager manager = WaypointManager.get(player.getServer());
        PacketTracker.sendToClient(player, new SyncWaypointsPacket(manager.getAllWaypointsMap()));
    }

    /**
     * 改动全局可见性的唯一权威入口：先落服务端 SavedData，再广播给全服在线玩家。
     *
     * <p>此前各调用点只改 SavedData 或用 {@code serverLevel().players()} 发包，前者会让客户端
     * 残留上一局状态，后者收不到其它维度玩家的包。所有开关（冒险家技能、OP 命令、开局/局末
     * 复位）都必须走这里，保证两端状态一致。</p>
     */
    public static void setVisibility(MinecraftServer server, boolean visible) {
        if (server == null) {
            return;
        }
        WaypointVisibilityManager.get(server).setWaypointsVisibility(visible);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PacketTracker.sendToClient(player, new SyncWaypointVisibilityPacket(visible));
        }
    }

    /** 把服务端当前权威的可见性状态补发给单个玩家（加入 / 换服时对齐客户端默认值）。 */
    public static void syncVisibilityTo(ServerPlayer player) {
        if (player == null || player.getServer() == null) {
            return;
        }
        boolean visible = WaypointVisibilityManager.get(player.getServer()).getWaypointsVisibility();
        PacketTracker.sendToClient(player, new SyncWaypointVisibilityPacket(visible));
    }

    /**
     * 清除本局所有冒险家临时标点并让客户端立刻对账。
     *
     * <p>技能到时、冒险家死亡 / 换职业、开局、局末都走这里。只清临时标点，
     * 不动 OP 建立的永久地图标点，也不改全局可见性开关。</p>
     */
    public static void clearAdventurerWaypoints(MinecraftServer server) {
        if (server == null) {
            return;
        }
        WaypointManager.get(server).clearAdventurerWaypoints();
        syncToAll(server);
    }
}
