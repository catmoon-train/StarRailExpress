package org.agmas.noellesroles.game.modes.fourthroom.scene;

import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.index.TMMBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.game.modes.fourthroom.block.FourthRoomTableBlock;
import org.agmas.noellesroles.game.modes.fourthroom.config.FourthRoomConfig;
import org.agmas.noellesroles.game.modes.fourthroom.game.FourthRoomSavedData;
import org.agmas.noellesroles.game.modes.fourthroom.room.RoomDefinition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class FourthRoomSceneGenerator {
    private static final int ROOM_HALF = 5;
    private static final int ROOM_HEIGHT = 4;
    private static final int LOBBY_HALF = 5;
    private static final int DUEL_HALF = 9;

    private final ServerLevel level;
    private final FourthRoomConfig config;

    public FourthRoomSceneGenerator(ServerLevel level) {
        this.level = level;
        this.config = FourthRoomConfig.get();
    }

    public FourthRoomSceneLayout generate(BlockPos lobbyFloorCenter) {
        FourthRoomSceneLayout layout = new FourthRoomSceneLayout();
        layout.generated = true;
        layout.origin = lobbyFloorCenter;
        layout.lobbyPos = lobbyFloorCenter.above();
        layout.rooms.addAll(createRoomLayout(lobbyFloorCenter));
        layout.duelArenaPos = computeDuelArena(layout.rooms, lobbyFloorCenter);

        Bounds bounds = computeBounds(layout, lobbyFloorCenter);
        clearBounds(bounds);
        buildLobby(lobbyFloorCenter);
        for (RoomDefinition room : layout.rooms) {
            buildRoom(room, lobbyFloorCenter);
            buildCorridor(lobbyFloorCenter, room);
        }
        buildDuelArena(layout.duelArenaPos);
        applyAreas(layout, bounds);
        FourthRoomSavedData data = FourthRoomSavedData.get(level);
        data.sceneLayout = layout;
        data.setDirty(true);
        return layout;
    }

    private List<RoomDefinition> createRoomLayout(BlockPos lobbyFloorCenter) {
        int roomCount = Math.max(1, config.roomCount);
        int spacing = Math.max(config.roomSpacing + 8, 20);
        int columns = Math.max(2, (int) Math.ceil(Math.sqrt(roomCount)));
        int rows = Math.max(1, (int) Math.ceil(roomCount / (double) columns));
        List<RoomDefinition> rooms = new ArrayList<>();
        int roomId = 0;
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns && roomId < roomCount; column++) {
                double centeredColumn = column - (columns - 1) / 2.0D;
                double centeredRow = row - (rows - 1) / 2.0D;
                int x = lobbyFloorCenter.getX() + (int) Math.round(centeredColumn * spacing);
                int z = lobbyFloorCenter.getZ() + (int) Math.round(centeredRow * spacing);
                BlockPos center = new BlockPos(x, lobbyFloorCenter.getY(), z);
                Direction facing = doorFacing(center, lobbyFloorCenter);
                rooms.add(new RoomDefinition(roomId, center, seatPos(center, facing, true), seatPos(center, facing, false)));
                roomId++;
            }
        }
        return rooms;
    }

    private BlockPos computeDuelArena(List<RoomDefinition> rooms, BlockPos lobbyFloorCenter) {
        int maxZ = lobbyFloorCenter.getZ();
        for (RoomDefinition room : rooms) {
            maxZ = Math.max(maxZ, room.center().getZ());
        }
        return new BlockPos(lobbyFloorCenter.getX(), lobbyFloorCenter.getY(), maxZ + Math.max(config.roomSpacing + 16, 28));
    }

    private Bounds computeBounds(FourthRoomSceneLayout layout, BlockPos lobbyFloorCenter) {
        int minX = lobbyFloorCenter.getX() - LOBBY_HALF - 4;
        int maxX = lobbyFloorCenter.getX() + LOBBY_HALF + 4;
        int minZ = lobbyFloorCenter.getZ() - LOBBY_HALF - 4;
        int maxZ = lobbyFloorCenter.getZ() + LOBBY_HALF + 4;
        for (RoomDefinition room : layout.rooms) {
            minX = Math.min(minX, room.center().getX() - ROOM_HALF - 4);
            maxX = Math.max(maxX, room.center().getX() + ROOM_HALF + 4);
            minZ = Math.min(minZ, room.center().getZ() - ROOM_HALF - 4);
            maxZ = Math.max(maxZ, room.center().getZ() + ROOM_HALF + 4);
        }
        minX = Math.min(minX, layout.duelArenaPos.getX() - DUEL_HALF - 4);
        maxX = Math.max(maxX, layout.duelArenaPos.getX() + DUEL_HALF + 4);
        minZ = Math.min(minZ, layout.duelArenaPos.getZ() - DUEL_HALF - 4);
        maxZ = Math.max(maxZ, layout.duelArenaPos.getZ() + DUEL_HALF + 4);
        return new Bounds(minX, lobbyFloorCenter.getY(), minZ, maxX, lobbyFloorCenter.getY() + 8, maxZ);
    }

    private void clearBounds(Bounds bounds) {
        fill(bounds.min(), bounds.max(), Blocks.AIR.defaultBlockState());
    }

    private void buildLobby(BlockPos center) {
        BlockState floor = TMMBlocks.WHITE_HULL.defaultBlockState();
        BlockState trim = TMMBlocks.STAINLESS_STEEL.defaultBlockState();
        fill(center.offset(-LOBBY_HALF, 0, -LOBBY_HALF), center.offset(LOBBY_HALF, 0, LOBBY_HALF), floor);
        fill(center.offset(-LOBBY_HALF - 1, -1, -LOBBY_HALF - 1), center.offset(LOBBY_HALF + 1, -1, LOBBY_HALF + 1), trim);
        placeLight(center.offset(-3, 0, -3));
        placeLight(center.offset(3, 0, -3));
        placeLight(center.offset(-3, 0, 3));
        placeLight(center.offset(3, 0, 3));
        setBlock(center, TMMBlocks.BAR_TABLE.defaultBlockState());
    }

    private void buildRoom(RoomDefinition room, BlockPos lobbyFloorCenter) {
        BlockPos center = room.center();
        BlockState floor = TMMBlocks.WHITE_HULL.defaultBlockState();
        BlockState wall = TMMBlocks.BLACK_HULL.defaultBlockState();
        BlockState roof = TMMBlocks.STAINLESS_STEEL.defaultBlockState();
        BlockState window = Blocks.TINTED_GLASS.defaultBlockState();
        fill(center.offset(-ROOM_HALF, 0, -ROOM_HALF), center.offset(ROOM_HALF, 0, ROOM_HALF), floor);
        fill(center.offset(-ROOM_HALF, ROOM_HEIGHT, -ROOM_HALF), center.offset(ROOM_HALF, ROOM_HEIGHT, ROOM_HALF), roof);
        fill(center.offset(-ROOM_HALF, 1, -ROOM_HALF), center.offset(ROOM_HALF, ROOM_HEIGHT - 1, ROOM_HALF), Blocks.AIR.defaultBlockState());
        for (int y = 1; y < ROOM_HEIGHT; y++) {
            for (int x = -ROOM_HALF; x <= ROOM_HALF; x++) {
                setBlock(center.offset(x, y, -ROOM_HALF), wall);
                setBlock(center.offset(x, y, ROOM_HALF), wall);
            }
            for (int z = -ROOM_HALF; z <= ROOM_HALF; z++) {
                setBlock(center.offset(-ROOM_HALF, y, z), wall);
                setBlock(center.offset(ROOM_HALF, y, z), wall);
            }
        }
        carveDoor(roomDoorCenter(room, lobbyFloorCenter), doorFacing(room, lobbyFloorCenter));
        placeWindows(center, lobbyFloorCenter, window);
        Direction tableFacing = doorFacing(room, lobbyFloorCenter);
        setBlock(center, TMMBlocks.FOURTH_ROOM_TABLE.defaultBlockState().setValue(FourthRoomTableBlock.FACING, tableFacing));
        FourthRoomTableBlock.placeStructure(level, center, tableFacing);
        setBlock(room.seatA(), TMMBlocks.BAR_STOOL.defaultBlockState());
        setBlock(room.seatB(), TMMBlocks.BAR_STOOL.defaultBlockState());
        placeLight(center.offset(-3, 0, -3));
        placeLight(center.offset(3, 0, -3));
    }

    private void placeWindows(BlockPos center, BlockPos lobbyFloorCenter, BlockState window) {
        Direction doorFacing = doorFacing(center, lobbyFloorCenter);
        if (doorFacing != Direction.NORTH) {
            for (int x = -2; x <= 2; x++) {
                setBlock(center.offset(x, 2, -ROOM_HALF), window);
            }
        }
        if (doorFacing != Direction.SOUTH) {
            for (int x = -2; x <= 2; x++) {
                setBlock(center.offset(x, 2, ROOM_HALF), window);
            }
        }
        if (doorFacing != Direction.WEST) {
            for (int z = -2; z <= 2; z++) {
                setBlock(center.offset(-ROOM_HALF, 2, z), window);
            }
        }
        if (doorFacing != Direction.EAST) {
            for (int z = -2; z <= 2; z++) {
                setBlock(center.offset(ROOM_HALF, 2, z), window);
            }
        }
    }

    private void buildCorridor(BlockPos lobbyFloorCenter, RoomDefinition room) {
        BlockPos doorCenter = roomDoorCenter(room, lobbyFloorCenter);
        Direction doorFacing = doorFacing(room, lobbyFloorCenter);
        if (doorFacing == Direction.WEST || doorFacing == Direction.EAST) {
            BlockPos corner = new BlockPos(doorCenter.getX(), lobbyFloorCenter.getY(), lobbyFloorCenter.getZ());
            buildStraightCorridor(lobbyFloorCenter, corner);
            buildStraightCorridor(corner, doorCenter);
        } else {
            BlockPos corner = new BlockPos(lobbyFloorCenter.getX(), lobbyFloorCenter.getY(), doorCenter.getZ());
            buildStraightCorridor(lobbyFloorCenter, corner);
            buildStraightCorridor(corner, doorCenter);
        }
    }

    private void buildStraightCorridor(BlockPos start, BlockPos end) {
        if (start.equals(end)) {
            return;
        }
        BlockState floor = TMMBlocks.WHITE_HULL.defaultBlockState();
        BlockState wall = TMMBlocks.BLACK_HULL.defaultBlockState();
        BlockState roof = TMMBlocks.STAINLESS_STEEL.defaultBlockState();
        int y = start.getY();
        if (start.getX() == end.getX()) {
            int minZ = Math.min(start.getZ(), end.getZ());
            int maxZ = Math.max(start.getZ(), end.getZ());
            fill(new BlockPos(start.getX() - 2, y, minZ), new BlockPos(start.getX() + 2, y, maxZ), floor);
            fill(new BlockPos(start.getX() - 2, y + ROOM_HEIGHT, minZ), new BlockPos(start.getX() + 2, y + ROOM_HEIGHT, maxZ), roof);
            fill(new BlockPos(start.getX() - 1, y + 1, minZ), new BlockPos(start.getX() + 1, y + ROOM_HEIGHT - 1, maxZ), Blocks.AIR.defaultBlockState());
            fill(new BlockPos(start.getX() - 2, y + 1, minZ), new BlockPos(start.getX() - 2, y + ROOM_HEIGHT - 1, maxZ), wall);
            fill(new BlockPos(start.getX() + 2, y + 1, minZ), new BlockPos(start.getX() + 2, y + ROOM_HEIGHT - 1, maxZ), wall);
        } else if (start.getZ() == end.getZ()) {
            int minX = Math.min(start.getX(), end.getX());
            int maxX = Math.max(start.getX(), end.getX());
            fill(new BlockPos(minX, y, start.getZ() - 2), new BlockPos(maxX, y, start.getZ() + 2), floor);
            fill(new BlockPos(minX, y + ROOM_HEIGHT, start.getZ() - 2), new BlockPos(maxX, y + ROOM_HEIGHT, start.getZ() + 2), roof);
            fill(new BlockPos(minX, y + 1, start.getZ() - 1), new BlockPos(maxX, y + ROOM_HEIGHT - 1, start.getZ() + 1), Blocks.AIR.defaultBlockState());
            fill(new BlockPos(minX, y + 1, start.getZ() - 2), new BlockPos(maxX, y + ROOM_HEIGHT - 1, start.getZ() - 2), wall);
            fill(new BlockPos(minX, y + 1, start.getZ() + 2), new BlockPos(maxX, y + ROOM_HEIGHT - 1, start.getZ() + 2), wall);
        }
    }

    private void buildDuelArena(BlockPos center) {
        BlockState floor = Blocks.SMOOTH_STONE.defaultBlockState();
        BlockState wall = TMMBlocks.DARK_STEEL.defaultBlockState();
        fill(center.offset(-DUEL_HALF, 0, -DUEL_HALF), center.offset(DUEL_HALF, 0, DUEL_HALF), floor);
        fill(center.offset(-DUEL_HALF, 1, -DUEL_HALF), center.offset(DUEL_HALF, 2, -DUEL_HALF), wall);
        fill(center.offset(-DUEL_HALF, 1, DUEL_HALF), center.offset(DUEL_HALF, 2, DUEL_HALF), wall);
        fill(center.offset(-DUEL_HALF, 1, -DUEL_HALF), center.offset(-DUEL_HALF, 2, DUEL_HALF), wall);
        fill(center.offset(DUEL_HALF, 1, -DUEL_HALF), center.offset(DUEL_HALF, 2, DUEL_HALF), wall);
        setBlock(center.offset(-4, 1, -4), TMMBlocks.CARGO_BOX.defaultBlockState());
        setBlock(center.offset(4, 1, -4), TMMBlocks.CARGO_BOX.defaultBlockState());
        setBlock(center.offset(-4, 1, 4), TMMBlocks.CARGO_BOX.defaultBlockState());
        setBlock(center.offset(4, 1, 4), TMMBlocks.CARGO_BOX.defaultBlockState());
        placeLight(center.offset(-7, 0, -7));
        placeLight(center.offset(7, 0, -7));
        placeLight(center.offset(-7, 0, 7));
        placeLight(center.offset(7, 0, 7));
    }

    private void applyAreas(FourthRoomSceneLayout layout, Bounds bounds) {
        AreasWorldComponent areas = AreasWorldComponent.KEY.get(level);
        areas.setReadyArea(new AABB(
                layout.lobbyPos.getX() - (LOBBY_HALF + 1),
                layout.lobbyPos.getY() - 1,
                layout.lobbyPos.getZ() - (LOBBY_HALF + 1),
                layout.lobbyPos.getX() + (LOBBY_HALF + 2),
                layout.lobbyPos.getY() + 4,
                layout.lobbyPos.getZ() + (LOBBY_HALF + 2)));
        AABB sceneArea = new AABB(bounds.minX, bounds.minY, bounds.minZ, bounds.maxX + 1, bounds.maxY + 1, bounds.maxZ + 1);
        areas.setSceneArea(sceneArea);
        areas.setPlayArea(sceneArea);
        areas.setRoomCount(layout.rooms.size());
        Map<Integer, Vec3> roomPositions = new HashMap<>();
        for (RoomDefinition room : layout.rooms) {
            roomPositions.put(room.roomId() + 1, Vec3.atCenterOf(room.center()));
        }
        areas.setRoomPositions(roomPositions);
        areas.sync();
    }

    private BlockPos roomDoorCenter(RoomDefinition room, BlockPos lobbyFloorCenter) {
        Direction facing = doorFacing(room, lobbyFloorCenter);
        return room.center().relative(facing, ROOM_HALF);
    }

    private Direction doorFacing(RoomDefinition room, BlockPos lobbyFloorCenter) {
        return doorFacing(room.center(), lobbyFloorCenter);
    }

    private Direction doorFacing(BlockPos roomCenter, BlockPos lobbyFloorCenter) {
        int dx = lobbyFloorCenter.getX() - roomCenter.getX();
        int dz = lobbyFloorCenter.getZ() - roomCenter.getZ();
        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx >= 0 ? Direction.EAST : Direction.WEST;
        }
        return dz >= 0 ? Direction.SOUTH : Direction.NORTH;
    }

    private void carveDoor(BlockPos doorCenter, Direction facing) {
        if (facing == Direction.EAST || facing == Direction.WEST) {
            fill(doorCenter.offset(0, 1, -1), doorCenter.offset(0, ROOM_HEIGHT - 1, 1), Blocks.AIR.defaultBlockState());
        } else {
            fill(doorCenter.offset(-1, 1, 0), doorCenter.offset(1, ROOM_HEIGHT - 1, 0), Blocks.AIR.defaultBlockState());
        }
    }

    private void placeLight(BlockPos floorPos) {
        setBlock(floorPos, Blocks.SEA_LANTERN.defaultBlockState());
    }

    private BlockPos seatPos(BlockPos center, Direction facing, boolean firstSeat) {
        int localX = firstSeat ? -2 : 2;
        BlockPos offset = switch (facing) {
            case SOUTH -> center.offset(-localX, 0, 0);
            case EAST -> center.offset(0, 0, localX);
            case WEST -> center.offset(0, 0, -localX);
            case NORTH -> center.offset(localX, 0, 0);
            default -> center.offset(localX, 0, 0);
        };
        return offset;
    }

    private void fill(BlockPos from, BlockPos to, BlockState state) {
        int minX = Math.min(from.getX(), to.getX());
        int maxX = Math.max(from.getX(), to.getX());
        int minY = Math.min(from.getY(), to.getY());
        int maxY = Math.max(from.getY(), to.getY());
        int minZ = Math.min(from.getZ(), to.getZ());
        int maxZ = Math.max(from.getZ(), to.getZ());
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    level.setBlock(new BlockPos(x, y, z), state, Block.UPDATE_ALL);
                }
            }
        }
    }

    private void setBlock(BlockPos pos, BlockState state) {
        level.setBlock(pos, state, Block.UPDATE_ALL);
    }

    private record Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        BlockPos min() {
            return new BlockPos(minX, minY - 1, minZ);
        }

        BlockPos max() {
            return new BlockPos(maxX, maxY, maxZ);
        }
    }
}