package org.agmas.noellesroles.game.modes.fourthroom.scene;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import org.agmas.noellesroles.game.modes.fourthroom.room.RoomDefinition;

import java.util.ArrayList;
import java.util.List;

public final class FourthRoomSceneLayout {
    public boolean generated;
    public BlockPos origin = BlockPos.ZERO;
    public BlockPos lobbyPos = BlockPos.ZERO;
    public BlockPos duelArenaPos = BlockPos.ZERO;
    public final List<RoomDefinition> rooms = new ArrayList<>();

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("Generated", generated);
        tag.put("Origin", NbtUtils.writeBlockPos(origin));
        tag.put("LobbyPos", NbtUtils.writeBlockPos(lobbyPos));
        tag.put("DuelArenaPos", NbtUtils.writeBlockPos(duelArenaPos));
        ListTag roomList = new ListTag();
        for (RoomDefinition room : rooms) {
            roomList.add(room.save());
        }
        tag.put("Rooms", roomList);
        return tag;
    }

    public static FourthRoomSceneLayout load(CompoundTag tag) {
        FourthRoomSceneLayout layout = new FourthRoomSceneLayout();
        layout.generated = tag.getBoolean("Generated");
        layout.origin = NbtUtils.readBlockPos(tag, "Origin").orElse(BlockPos.ZERO);
        layout.lobbyPos = NbtUtils.readBlockPos(tag, "LobbyPos").orElse(BlockPos.ZERO);
        layout.duelArenaPos = NbtUtils.readBlockPos(tag, "DuelArenaPos").orElse(BlockPos.ZERO);
        for (Tag roomEntry : tag.getList("Rooms", Tag.TAG_COMPOUND)) {
            if (roomEntry instanceof CompoundTag roomTag) {
                layout.rooms.add(RoomDefinition.load(roomTag));
            }
        }
        return layout;
    }

    public boolean hasRooms() {
        return generated && !rooms.isEmpty();
    }
}