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

package org.agmas.noellesroles.game.modes.fourthroom.duel;

import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import org.agmas.noellesroles.game.modes.fourthroom.config.FourthRoomConfig;
import org.agmas.noellesroles.game.modes.fourthroom.game.*;

public final class FourthRoomDuelManager {
    private final FourthRoomGameManager manager;
    private final FourthRoomSavedData data;
    private final FourthRoomConfig config;

    public FourthRoomDuelManager(FourthRoomGameManager manager, FourthRoomSavedData data, FourthRoomConfig config) {
        this.manager = manager;
        this.data = data;
        this.config = config;
    }

    public void tick() {
        FourthRoomTeam winner = findLivingTeam();
        if (winner != null && data.phase == FourthRoomPhase.DUEL) {
            finishMatch(winner, "duel_victory");
        }
    }

    public void maybeResolveWinCondition() {
        int redAlive = 0;
        int blueAlive = 0;
        for (FourthRoomPlayerState playerState : data.players.values()) {
            if (!playerState.alive) {
                continue;
            }
            if (playerState.team == FourthRoomTeam.RED) {
                redAlive++;
            } else {
                blueAlive++;
            }
        }
        if (redAlive == 0 && blueAlive > 0) {
            finishMatch(FourthRoomTeam.BLUE, "team_eliminated");
            return;
        }
        if (blueAlive == 0 && redAlive > 0) {
            finishMatch(FourthRoomTeam.RED, "team_eliminated");
            return;
        }
        if (redAlive > 0 && blueAlive > 0 && data.rotationCount >= config.maxRotations && data.phase == FourthRoomPhase.CARD_BATTLE) {
            startFinalDuel();
        }
    }

    public void startFinalDuel() {
        data.phase = FourthRoomPhase.DUEL;
        BlockPos duelArena = data.sceneLayout.generated
                ? data.sceneLayout.duelArenaPos
                : config.resolveDuelArena(manager.level().getSharedSpawnPos());
        for (FourthRoomPlayerState playerState : data.players.values()) {
            if (!playerState.alive) {
                continue;
            }
            ServerPlayer player = manager.level().getServer().getPlayerList().getPlayer(playerState.playerId);
            if (player == null) {
                continue;
            }
            player.stopRiding();
            player.setGameMode(GameType.SURVIVAL);
            player.setHealth(Math.min(player.getMaxHealth(), 10.0F));
            player.teleportTo(manager.level(), duelArena.getX() + 0.5D, duelArena.getY() + 0.1D,
                    duelArena.getZ() + 0.5D, player.getYRot(), player.getXRot());
        }
        data.setDirty(true);
        manager.broadcast("Final duel started.");
        manager.syncMatchState();
    }

    public void finishMatch(FourthRoomTeam winner, String reason) {
        data.winner = winner;
        data.phase = FourthRoomPhase.FINISHED;
        data.active = false;
        data.setDirty(true);
        manager.broadcast("Fourth Room winner: " + winner.name() + " (" + reason + ")");
        manager.syncMatchState();
        GameUtils.stopGame(manager.level());
    }

    private FourthRoomTeam findLivingTeam() {
        boolean redAlive = false;
        boolean blueAlive = false;
        for (FourthRoomPlayerState playerState : data.players.values()) {
            if (!playerState.alive) {
                continue;
            }
            if (playerState.team == FourthRoomTeam.RED) {
                redAlive = true;
            } else {
                blueAlive = true;
            }
        }
        if (redAlive == blueAlive) {
            return null;
        }
        return redAlive ? FourthRoomTeam.RED : FourthRoomTeam.BLUE;
    }
}