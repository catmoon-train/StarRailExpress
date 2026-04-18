package org.agmas.noellesroles.game.modes.fourthroom.room;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import org.agmas.noellesroles.game.modes.fourthroom.config.FourthRoomConfig;
import org.agmas.noellesroles.game.modes.fourthroom.game.FourthRoomPlayerState;
import org.agmas.noellesroles.game.modes.fourthroom.game.FourthRoomRoomState;
import org.agmas.noellesroles.game.modes.fourthroom.game.FourthRoomSavedData;
import org.agmas.noellesroles.game.modes.fourthroom.game.FourthRoomTeam;

import java.util.*;

public final class RoomManager {
    private final ServerLevel level;
    private final FourthRoomSavedData data;
    private final FourthRoomConfig config;

    public RoomManager(ServerLevel level, FourthRoomSavedData data, FourthRoomConfig config) {
        this.level = level;
        this.data = data;
        this.config = config;
    }

    public List<RoomDefinition> buildRoomDefinitions() {
        if (data.sceneLayout.hasRooms()) {
            return new ArrayList<>(data.sceneLayout.rooms);
        }
        BlockPos lobby = config.resolveLobby(level.getSharedSpawnPos());
        List<RoomDefinition> rooms = new ArrayList<>();
        int columns = Math.max(2, (int) Math.ceil(Math.sqrt(config.roomCount)));
        int rows = Math.max(1, (int) Math.ceil(config.roomCount / (double) columns));
        int index = 0;
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns && index < config.roomCount; column++) {
                int xOffset = (int) Math.round((column - (columns - 1) / 2.0D) * config.roomSpacing);
                int zOffset = (int) Math.round((row - (rows - 1) / 2.0D) * config.roomSpacing);
                BlockPos center = lobby.offset(xOffset, 0, zOffset);
                Direction facing = doorFacing(center, lobby);
                rooms.add(new RoomDefinition(index, center, seatPos(center, facing, true), seatPos(center, facing, false)));
                index++;
            }
        }
        return rooms;
    }

    public void assignRooms(List<FourthRoomPlayerState> playerStates) {
        data.rooms.clear();
        List<FourthRoomPlayerState> red = new ArrayList<>();
        List<FourthRoomPlayerState> blue = new ArrayList<>();
        for (FourthRoomPlayerState state : playerStates) {
            if (!state.alive) {
                continue;
            }
            state.roomId = -1;
            if (state.team == FourthRoomTeam.RED) {
                red.add(state);
            } else {
                blue.add(state);
            }
        }
        Collections.shuffle(red);
        Collections.shuffle(blue);
        List<FourthRoomPlayerState> leftovers = new ArrayList<>();
        int roomId = 0;
        while (!red.isEmpty() || !blue.isEmpty()) {
            FourthRoomRoomState room = new FourthRoomRoomState(roomId);
            if (!red.isEmpty()) {
                addOccupant(room, red.removeFirst());
            }
            if (!blue.isEmpty()) {
                addOccupant(room, blue.removeFirst());
            }
            while (room.occupants.size() < config.playersPerRoom && !red.isEmpty()) {
                leftovers.add(red.removeFirst());
            }
            while (room.occupants.size() < config.playersPerRoom && !blue.isEmpty()) {
                leftovers.add(blue.removeFirst());
            }
            data.rooms.put(room.roomId, room);
            roomId++;
        }
        if (!leftovers.isEmpty()) {
            leftovers.sort(Comparator.comparing(player -> player.playerId));
            for (FourthRoomPlayerState leftover : leftovers) {
                FourthRoomRoomState targetRoom = data.rooms.values().stream()
                        .filter(room -> room.occupants.size() < config.playersPerRoom)
                        .findFirst()
                        .orElseGet(() -> {
                            FourthRoomRoomState created = new FourthRoomRoomState(data.rooms.size());
                            data.rooms.put(created.roomId, created);
                            return created;
                        });
                addOccupant(targetRoom, leftover);
            }
        }
        for (FourthRoomRoomState roomState : data.rooms.values()) {
            roomState.activePlayerId = roomState.occupants.isEmpty() ? null : roomState.occupants.getFirst();
            refreshRoomTurnState(roomState.roomId);
        }
        data.setDirty(true);
    }

    public void teleportPlayersToRooms() {
        List<RoomDefinition> definitions = buildRoomDefinitions();
        for (FourthRoomRoomState roomState : data.rooms.values()) {
            if (roomState.roomId < 0 || roomState.roomId >= definitions.size()) {
                continue;
            }
            RoomDefinition definition = definitions.get(roomState.roomId);
            for (int index = 0; index < roomState.occupants.size(); index++) {
                ServerPlayer player = level.getServer().getPlayerList().getPlayer(roomState.occupants.get(index));
                if (player == null) {
                    continue;
                }
                BlockPos seatPos = index == 0 ? definition.seatA() : definition.seatB();
                float yRot = lookYaw(seatPos, definition.center());
                player.teleportTo(level, seatPos.getX() + 0.5D, seatPos.getY() + 0.1D, seatPos.getZ() + 0.5D,
                    yRot, 12.0F);
            }
        }
    }

    public void teleportAlivePlayersToLobby() {
        BlockPos lobby = data.sceneLayout.generated ? data.sceneLayout.lobbyPos : config.resolveLobby(level.getSharedSpawnPos());
        for (FourthRoomPlayerState playerState : data.players.values()) {
            if (!playerState.alive) {
                continue;
            }
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerState.playerId);
            if (player == null) {
                continue;
            }
            player.teleportTo(level, lobby.getX() + 0.5D, lobby.getY(), lobby.getZ() + 0.5D, player.getYRot(),
                    player.getXRot());
        }
    }

    public UUID getOpponent(UUID playerId) {
        FourthRoomPlayerState playerState = data.players.get(playerId);
        if (playerState == null) {
            return null;
        }
        FourthRoomRoomState roomState = data.rooms.get(playerState.roomId);
        if (roomState == null) {
            return null;
        }
        for (UUID occupant : roomState.occupants) {
            if (!occupant.equals(playerId)) {
                FourthRoomPlayerState opponent = data.players.get(occupant);
                if (opponent != null && opponent.alive) {
                    return occupant;
                }
            }
        }
        return null;
    }

    public void advanceTurn(int roomId) {
        FourthRoomRoomState roomState = data.rooms.get(roomId);
        if (roomState == null || roomState.occupants.isEmpty()) {
            return;
        }
        if (countLivingPlayers(roomId) <= 1) {
            roomState.activePlayerId = null;
            data.setDirty(true);
            return;
        }
        int activeIndex = roomState.activePlayerId == null ? -1 : roomState.occupants.indexOf(roomState.activePlayerId);
        if (activeIndex < 0) {
            activeIndex = -1;
        }
        for (int attempts = 0; attempts < roomState.occupants.size(); attempts++) {
            activeIndex = (activeIndex + 1) % roomState.occupants.size();
            UUID candidateId = roomState.occupants.get(activeIndex);
            FourthRoomPlayerState candidate = data.players.get(candidateId);
            if (candidate != null && candidate.alive) {
                roomState.activePlayerId = candidateId;
                roomState.turnNumber++;
                data.setDirty(true);
                return;
            }
        }
        roomState.activePlayerId = null;
        data.setDirty(true);
    }

    public int countLivingPlayers(int roomId) {
        FourthRoomRoomState roomState = data.rooms.get(roomId);
        if (roomState == null) {
            return 0;
        }
        int living = 0;
        for (UUID occupantId : roomState.occupants) {
            FourthRoomPlayerState state = data.players.get(occupantId);
            if (state != null && state.alive) {
                living++;
            }
        }
        return living;
    }

    public boolean hasLivingOpponent(UUID playerId) {
        return getOpponent(playerId) != null;
    }

    public void refreshRoomTurnState(int roomId) {
        FourthRoomRoomState roomState = data.rooms.get(roomId);
        if (roomState == null) {
            return;
        }
        if (countLivingPlayers(roomId) <= 1) {
            roomState.activePlayerId = null;
            data.setDirty(true);
            return;
        }
        if (roomState.activePlayerId != null) {
            FourthRoomPlayerState current = data.players.get(roomState.activePlayerId);
            if (current != null && current.alive) {
                return;
            }
        }
        for (UUID occupantId : roomState.occupants) {
            FourthRoomPlayerState candidate = data.players.get(occupantId);
            if (candidate != null && candidate.alive) {
                roomState.activePlayerId = occupantId;
                data.setDirty(true);
                return;
            }
        }
        roomState.activePlayerId = null;
        data.setDirty(true);
    }

    private void addOccupant(FourthRoomRoomState room, FourthRoomPlayerState state) {
        room.occupants.add(state.playerId);
        state.roomId = room.roomId;
    }

    private Direction doorFacing(BlockPos roomCenter, BlockPos lobbyCenter) {
        int dx = lobbyCenter.getX() - roomCenter.getX();
        int dz = lobbyCenter.getZ() - roomCenter.getZ();
        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx >= 0 ? Direction.EAST : Direction.WEST;
        }
        return dz >= 0 ? Direction.SOUTH : Direction.NORTH;
    }

    private BlockPos seatPos(BlockPos center, Direction facing, boolean firstSeat) {
        int localX = firstSeat ? -2 : 2;
        return switch (facing) {
            case SOUTH -> center.offset(-localX, 0, 0);
            case EAST -> center.offset(0, 0, localX);
            case WEST -> center.offset(0, 0, -localX);
            case NORTH -> center.offset(localX, 0, 0);
            default -> center.offset(localX, 0, 0);
        };
    }

    private float lookYaw(BlockPos from, BlockPos to) {
        double dx = (to.getX() + 0.5D) - (from.getX() + 0.5D);
        double dz = (to.getZ() + 0.5D) - (from.getZ() + 0.5D);
        return (float) (Mth.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F;
    }
}