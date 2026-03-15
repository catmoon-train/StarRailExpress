package org.agmas.noellesroles.modifier.introverted;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.noellesroles.modifier.NRModifiers;

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
    private static final float ALONE_DRAIN_MULTIPLIER = 0.0f;

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            var overworld = server.overworld();
            if (overworld == null) {
                return;
            }

            var gameWorld = SREGameWorldComponent.KEY.get(overworld);
            if (!gameWorld.isRunning()) {
                return;
            }

            var worldModifierComponent = WorldModifierComponent.KEY.get(overworld);

            for (ServerPlayer player : overworld.players()) {
                if (!GameUtils.isPlayerAliveAndSurvival(player)) {
                    continue;
                }

                if (!worldModifierComponent.isModifier(player, NRModifiers.INTROVERTED)) {
                    continue;
                }

                // 只影响受情绪系统影响的玩家
                SRERole role = gameWorld.getRole(player);
                if (role != null && role.canUseKiller()) {
                    continue;
                }

                SREPlayerMoodComponent mood = SREPlayerMoodComponent.KEY.get(player);
                int nearbyPlayers = countNearbyAliveSurvivalPlayers(overworld, player);

                // 如果超过人群阈值，情绪值下降更快
                if (nearbyPlayers >= CROWD_COUNT_THRESHOLD) {
                    mood.setMood(mood.getMood() - GameConstants.MOOD_DRAIN * CROWD_DRAIN_MULTIPLIER);
                }
                else {
                    mood.setMood(mood.getMood() + GameConstants.MOOD_DRAIN);
                }
            }
        });
    }

    private static int countNearbyAliveSurvivalPlayers(net.minecraft.server.level.ServerLevel world, ServerPlayer self) {
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
