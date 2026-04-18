package org.agmas.noellesroles.game.modes.fourthroom.game;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class FourthRoomRoomState {
    public int roomId;
    public final List<UUID> occupants = new ArrayList<>();
    public UUID activePlayerId;
    public int turnNumber = 1;
    public int nextPublicActionSequence = 1;
    public final List<FourthRoomPublicAction> publicActions = new ArrayList<>();

    public FourthRoomRoomState() {
    }

    public FourthRoomRoomState(int roomId) {
        this.roomId = roomId;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("RoomId", roomId);
        ListTag occupantsTag = new ListTag();
        for (UUID occupant : occupants) {
            occupantsTag.add(NbtUtils.createUUID(occupant));
        }
        tag.put("Occupants", occupantsTag);
        if (activePlayerId != null) {
            tag.putUUID("ActivePlayerId", activePlayerId);
        }
        tag.putInt("TurnNumber", turnNumber);
        tag.putInt("NextPublicActionSequence", nextPublicActionSequence);
        ListTag actionsTag = new ListTag();
        for (FourthRoomPublicAction publicAction : publicActions) {
            actionsTag.add(publicAction.save());
        }
        tag.put("PublicActions", actionsTag);
        return tag;
    }

    public static FourthRoomRoomState load(CompoundTag tag) {
        FourthRoomRoomState roomState = new FourthRoomRoomState(tag.getInt("RoomId"));
        for (Tag occupantTag : tag.getList("Occupants", Tag.TAG_INT_ARRAY)) {
            roomState.occupants.add(NbtUtils.loadUUID(occupantTag));
        }
        if (tag.hasUUID("ActivePlayerId")) {
            roomState.activePlayerId = tag.getUUID("ActivePlayerId");
        }
        roomState.turnNumber = Math.max(1, tag.getInt("TurnNumber"));
        roomState.nextPublicActionSequence = Math.max(1, tag.getInt("NextPublicActionSequence"));
        for (Tag actionTag : tag.getList("PublicActions", Tag.TAG_COMPOUND)) {
            if (actionTag instanceof CompoundTag compoundTag) {
                roomState.publicActions.add(FourthRoomPublicAction.load(compoundTag));
            }
        }
        if (roomState.publicActions.isEmpty()) {
            roomState.nextPublicActionSequence = Math.max(1, roomState.nextPublicActionSequence);
        } else {
            int maxSequence = roomState.publicActions.stream().mapToInt(action -> action.sequence).max().orElse(0);
            roomState.nextPublicActionSequence = Math.max(roomState.nextPublicActionSequence, maxSequence + 1);
        }
        return roomState;
    }
}