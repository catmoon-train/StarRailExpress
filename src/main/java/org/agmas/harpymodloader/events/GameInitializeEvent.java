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

package org.agmas.harpymodloader.events;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

import static net.fabricmc.fabric.api.event.EventFactory.createArrayBacked;

public interface GameInitializeEvent {

    Event<GameInitializeEvent> EVENT = createArrayBacked(GameInitializeEvent.class,
            listeners -> (serverWorld, gameWorldComponent, players) -> {
                for (GameInitializeEvent listener : listeners) {
                    listener.initializeGame(serverWorld, gameWorldComponent, players);
                }
            });

    void initializeGame(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent,
            List<ServerPlayer> players);
}