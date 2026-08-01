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