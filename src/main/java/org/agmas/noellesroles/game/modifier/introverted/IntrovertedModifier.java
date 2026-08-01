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

package org.agmas.noellesroles.game.modifier.introverted;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.SRERole.MoodType;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.server.level.ServerPlayer;

/**
 * 内向修饰符处理器
 * 效果：
 * - 当周围有2个或更多玩家时，情绪值下降更快
 * - 独处时(0-1名玩家)，情绪值不下降
 */
public final class IntrovertedModifier {

    private IntrovertedModifier() {
    }

    private static final int CROWD_COUNT_THRESHOLD = 2;
    private static final float CROWD_RANGE = 5.0f;
    private static final float CROWD_DRAIN_MULTIPLIER = 2.0f;
    // 独处时不下降情绪值，设置为0

    public static void serverTick(ServerPlayer player) {
        var gameComponent = SREGameWorldComponent.KEY.get(player.level());
        // 只影响受情绪系统影响的玩家
        SRERole role = gameComponent.getRole(player);
        if (!role.getMoodType().equals(MoodType.REAL))
            return;

        SREPlayerMoodComponent mood = SREPlayerMoodComponent.KEY.get(player);
        int nearbyPlayers = countNearbyAliveSurvivalPlayers(player.serverLevel(), player);

        // 如果超过人群阈值，情绪值下降更快
        if (nearbyPlayers >= CROWD_COUNT_THRESHOLD) {
            mood.addMood(-GameConstants.MOOD_DRAIN * CROWD_DRAIN_MULTIPLIER);
        }else{
            mood.addMood(GameConstants.MOOD_DRAIN);
        }
    }

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
