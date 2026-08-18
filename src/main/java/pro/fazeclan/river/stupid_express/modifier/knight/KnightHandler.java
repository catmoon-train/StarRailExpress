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

package pro.fazeclan.river.stupid_express.modifier.knight;

import io.wifi.starrailexpress.cca.SREGameTimeComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.LightLayer;
import pro.fazeclan.river.stupid_express.modifier.knight.cca.KnightComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class KnightHandler {
    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 20 != 0)
                return;
            // 侠客不在时停时传送
            if (SREGameTimeComponent.KEY.get(server.overworld()).isTimeFrozen()) {
                return;
            }
            List<ServerPlayer> knights = new ArrayList<>();
            List<ServerPlayer> targets = new ArrayList<>();

            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (!player.isAlive())
                    continue;

                // 只有冒险模式的玩家才能作为交换目标
                if (GameUtils.isPlayerAliveAndSurvival(player)) {
                    targets.add(player);
                }

                KnightComponent component = KnightComponent.KEY.get(player);
                if (component.isKnight()) {
                    int light = player.level().getBrightness(LightLayer.BLOCK, player.blockPosition());
                    if (light < 2) {
                        // 侠客也必须是冒险模式才能使用交换能力
                        if (GameUtils.isPlayerAliveAndSurvival(player)) {
                            knights.add(player);
                        }
                    }
                }
            }

            if (knights.isEmpty() || targets.size() < 2)
                return;

            Random random = new Random();
            for (ServerPlayer knight : knights) {

                // 10% chance per second
                if (random.nextDouble() <= 0.1) {
                    ServerPlayer target = targets.get(random.nextInt(targets.size()));
                    if (target.getUUID().equals(knight.getUUID()))
                        continue;
                    if (knight.distanceToSqr(target) >= 250 * 250)// 太远了
                        continue;
                    knight.stopRiding();

                    double tx = target.getX();
                    double ty = target.getY();
                    double tz = target.getZ();

                    knight.teleportTo(tx, ty, tz);
                }
            }
        });
    }
}