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

package org.agmas.noellesroles.game.roles.neutral.pelican;

import io.wifi.starrailexpress.event.MeetingStartEvent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

import java.util.*;

/**
 * 会议-B鹈鹕联动：会议开始时，肚子里的人自动死亡且不留下尸体。
 * 仅在会议系统启用的地图中有效。
 */
public final class PelicanMeetingHandler {
    private static boolean registered;

    public static void register() {
        if (registered) return;
        registered = true;
        MeetingStartEvent.EVENT.register((serverLevel, reporter) -> {
            for (UUID pelicanId : List.copyOf(PelicanManager.getStashedByPelican().keySet())) {
                ServerPlayer pelican = serverLevel.getServer().getPlayerList().getPlayer(pelicanId);
                if (pelican == null) continue;
                Deque<UUID> belly = PelicanManager.getStashedByPelican().get(pelicanId);
                if (belly == null) continue;
                for (UUID targetId : new ArrayList<>(belly)) {
                    ServerPlayer target = serverLevel.getServer().getPlayerList().getPlayer(targetId);
                    if (target != null) {
                        // 标记为鹈鹕吞噬死亡，Kill 但不留尸体
                        GameUtils.forceKillPlayer(target, false, pelican,
                                io.wifi.starrailexpress.game.GameConstants.DeathReasons.PELICAN_EATEN);
                        // 从肚中移除
                        PelicanManager.markStashedDead(targetId);
                        belly.remove(targetId);
                    } else {
                        PelicanManager.markStashedDead(targetId);
                        belly.remove(targetId);
                    }
                }
            }
        });
    }
}
