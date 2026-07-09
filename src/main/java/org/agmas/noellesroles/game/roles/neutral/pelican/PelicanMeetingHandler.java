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
                        io.wifi.starrailexpress.game.GameConstants.DeathReasons dl =
                                io.wifi.starrailexpress.game.GameConstants.DeathReasons.PELICAN_EATEN;
                        GameUtils.killPlayer(target, false, pelican, dl);
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
