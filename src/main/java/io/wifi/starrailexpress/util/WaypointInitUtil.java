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

import io.wifi.starrailexpress.game.data.WaypointManager;
import io.wifi.starrailexpress.game.data.WaypointVisibilityManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;

public class WaypointInitUtil {
    public static void initialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(WaypointInitUtil::onServerStarted);
    }

    private static void onServerStarted(MinecraftServer server) {
        // 初始化路径点管理器并加载数据
        WaypointManager manager = WaypointManager.get(server);
        manager.loadFromFile();
        
        // 初始化路径点可见性管理器。可见性是"本局临时状态"，不能跨服务器重启恢复：
        // 崩溃 / 强停会绕开 OnGameEnd，若把 true 恢复回来，下一局一开始就会显示上一局的标点。
        WaypointVisibilityManager.get(server).setWaypointsVisibility(false);
    }
}