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

package org.agmas.noellesroles.game.roles.neutral.panda;

import io.wifi.starrailexpress.event.AllowOtherCameraType;
import io.wifi.starrailexpress.event.client.OnGameFinishedClient;
import io.wifi.starrailexpress.event.client.OnGameStartedClient;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Panda;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PandaClientHandle {
    public static Map<UUID, Panda> pandaMap = new HashMap<>();
    public static void getOrCreatePanda(Player player, ClientLevel clientLevel) {
        UUID uuid = player.getUUID();
        if (!pandaMap.containsKey(uuid)){
            Panda value = new Panda(EntityType.PANDA, clientLevel);
            value.setPos(player.getX(),player.getY(),player.getZ());
            value.setNoAi(true);

            value.setYHeadRot(player.getYHeadRot());
            pandaMap.put(uuid, value);
            clientLevel.addEntity(pandaMap.get(uuid));
        }else {
            pandaMap.get(uuid);
        }
    }
    static {
        AllowOtherCameraType.EVENT.register((original, localplayer) -> {
            if (pandaMap.containsKey(localplayer.getUUID())){
                return AllowOtherCameraType.ReturnCameraType.THIRD_PERSON_BACK;

            }
            return AllowOtherCameraType.ReturnCameraType.NO_CHANGE;
        });
        OnGameStartedClient.EVENT.register(() -> {
            pandaMap.clear();
        });
        OnGameFinishedClient.EVENT.register(() -> {
            pandaMap.clear();
        });
//        ClientTickEvents.END_CLIENT_TICK.register(
//                client -> {
//                    ClientLevel level = client.level;
//                    if (level.getGameTime()%20==0){
//                        level.players().forEach(player -> {
//                            PandaComponent pandaComponent = PandaComponent.KEY.get(player);
//                            if (pandaComponent.isPanda){
//                                getOrCreatePanda(player, level);
//                            }else {
//                                pandaMap.remove(player.getUUID());
//                            }
//                        });
//                    }
//
//                }
//        );
    }
}
