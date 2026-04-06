package io.wifi.starrailexpress.fourthroom.room;

import net.minecraft.core.BlockPos;

public record RoomDefinition(int roomId, BlockPos center, BlockPos seatA, BlockPos seatB) {
}