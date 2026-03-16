package org.agmas.noellesroles.roles.swast;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.SRERole.MoodType;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.server.level.ServerPlayer;

/**
 * 特警角色Tick处理器
 * 效果：
 * - 当特警独处时（周围没有其他玩家），san值不会下降
 */
public final class SwastTickHandler {

    private static final float CROWD_RANGE = 5.0f;

    private SwastTickHandler() {
    }

    /**
     * 特警角色的每tick处理逻辑
     * 在独处时主动恢复san值以抵消下降
     */
    public static void serverTick(ServerPlayer player, SREGameWorldComponent gameComponent) {
        // 检查角色
        SRERole role = gameComponent.getRole(player);
        if (role == null || !role.getMoodType().equals(MoodType.REAL)) {
            return;
        }

        // 检查是否独处（周围没有其他存活玩家）
        int nearbyPlayers = countNearbyAliveSurvivalPlayers(player.serverLevel(), player);
        if (nearbyPlayers == 0) {
            // 特警独处时，主动恢复san值以抵消下降，保持san值不会下降到下界
            SREPlayerMoodComponent mood = SREPlayerMoodComponent.KEY.get(player);
            mood.setMood(mood.getMood() + GameConstants.MOOD_DRAIN);
        }
    }

    /**
     * 计算周围存活的生存模式玩家数量（不包括自己）
     * 参考IntrovertedModifier的实现
     */
    private static int countNearbyAliveSurvivalPlayers(net.minecraft.server.level.ServerLevel world,
            ServerPlayer self) {
        float rangeSq = CROWD_RANGE * CROWD_RANGE;
        int count = 0;
        for (ServerPlayer other : world.players()) {
            if (other == self) {
                continue;
            }
            if (!GameUtils.isPlayerAliveAndSurvival(other)) {
                continue;
            }
            if (other.distanceToSqr(self) <= rangeSq) {
                count++;
            }
        }
        return count;
    }
}
