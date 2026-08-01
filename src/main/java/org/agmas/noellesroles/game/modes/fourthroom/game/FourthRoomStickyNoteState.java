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

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;

import java.util.UUID;

public final class FourthRoomStickyNoteState {
    public UUID noteId = UUID.randomUUID();
    public UUID ownerId;
    public int roomId;
    public BlockPos pos = BlockPos.ZERO;
    public Direction face = Direction.NORTH;
    public String text = "";
    public boolean found;

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("NoteId", noteId);
        if (ownerId != null) {
            tag.putUUID("OwnerId", ownerId);
        }
        tag.putInt("RoomId", roomId);
        tag.put("Pos", NbtUtils.writeBlockPos(pos));
        tag.putString("Face", face.getName());
        tag.putString("Text", text);
        tag.putBoolean("Found", found);
        return tag;
    }

    public static FourthRoomStickyNoteState load(CompoundTag tag) {
        FourthRoomStickyNoteState state = new FourthRoomStickyNoteState();
        state.noteId = tag.getUUID("NoteId");
        if (tag.hasUUID("OwnerId")) {
            state.ownerId = tag.getUUID("OwnerId");
        }
        state.roomId = tag.getInt("RoomId");
        NbtUtils.readBlockPos(tag, "Pos").ifPresent(pos -> state.pos = pos);
        state.face = Direction.byName(tag.getString("Face"));
        if (state.face == null) {
            state.face = Direction.NORTH;
        }
        state.text = tag.getString("Text");
        state.found = tag.getBoolean("Found");
        return state;
    }
}