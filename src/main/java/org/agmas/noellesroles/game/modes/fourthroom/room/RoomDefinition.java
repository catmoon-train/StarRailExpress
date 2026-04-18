package org.agmas.noellesroles.game.modes.fourthroom.room;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;

public record RoomDefinition(int roomId, BlockPos center, BlockPos seatA, BlockPos seatB) {
	public CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		tag.putInt("RoomId", roomId);
		tag.put("Center", NbtUtils.writeBlockPos(center));
		tag.put("SeatA", NbtUtils.writeBlockPos(seatA));
		tag.put("SeatB", NbtUtils.writeBlockPos(seatB));
		return tag;
	}

	public static RoomDefinition load(CompoundTag tag) {
		BlockPos center = NbtUtils.readBlockPos(tag, "Center").orElse(BlockPos.ZERO);
		BlockPos seatA = NbtUtils.readBlockPos(tag, "SeatA").orElse(center.west());
		BlockPos seatB = NbtUtils.readBlockPos(tag, "SeatB").orElse(center.east());
		return new RoomDefinition(tag.getInt("RoomId"), center, seatA, seatB);
	}
}