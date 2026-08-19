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

package org.agmas.noellesroles.game.modes.fourthroom.game;

import io.wifi.starrailexpress.api.GameMode;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameRoundEndComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

public final class FourthRoomGameMode extends GameMode {

    public FourthRoomGameMode(ResourceLocation identifier) {
        super(identifier, 45, 2);
    }

    @Override
    public boolean requiresAssignedRole() {
        return false;
    }

    @Override
    public boolean enablePlayAreaDetections() {
        return false;
    }

    @Override
    public void tickServerGameLoop(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent) {
        FourthRoomGameManager.of(serverWorld).tickServer();
    }

    @Override
    public void initializeGame(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent,
            List<ServerPlayer> players) {
        FourthRoomGameManager.of(serverWorld).initializeMatch(players);
    }

    @Override
    public void finalizeGame(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent) {
        FourthRoomGameManager.of(serverWorld).shutdownMatch();
    }

    public boolean hasMood() {
        return false;
    }

    @Override
    public boolean canHaveMeeting() {
        return false;
    }

    @Override
    public boolean isPlayerWinning(ServerLevel world, ServerPlayer player, SRERole playerRole,
            SREGameRoundEndComponent roundEnd, SREGameWorldComponent gameComponent) {
        return false;
    }
}