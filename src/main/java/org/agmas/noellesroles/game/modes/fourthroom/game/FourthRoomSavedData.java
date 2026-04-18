package org.agmas.noellesroles.game.modes.fourthroom.game;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import org.agmas.noellesroles.game.modes.fourthroom.scene.FourthRoomSceneLayout;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class FourthRoomSavedData extends SavedData {
    private static final String DATA_NAME = "starrailexpress_fourth_room";

    public boolean active;
    public FourthRoomPhase phase = FourthRoomPhase.INACTIVE;
    public int requestedPlayerCount = 8;
    public long startedGameTick;
    public long nextRotationTick;
    public long rotationResumeTick;
    public long nextTaskTick;
    public long taskDeadlineTick;
    public int rotationCount;
    public String activeTaskId = "";
    public FourthRoomTeam winner;
    public FourthRoomSceneLayout sceneLayout = new FourthRoomSceneLayout();
    public final Map<UUID, FourthRoomPlayerState> players = new LinkedHashMap<>();
    public final Map<Integer, FourthRoomRoomState> rooms = new LinkedHashMap<>();
    public final java.util.List<FourthRoomStickyNoteState> stickyNotes = new java.util.ArrayList<>();

    public static FourthRoomSavedData get(ServerLevel level) {
        return get(level.getServer());
    }

    public static FourthRoomSavedData get(MinecraftServer server) {
        ServerLevel level = server.getLevel(Level.OVERWORLD);
        DimensionDataStorage storage = level.getDataStorage();
        return storage.computeIfAbsent(new SavedData.Factory<>(FourthRoomSavedData::new, FourthRoomSavedData::load, null),
                DATA_NAME);
    }

    public static FourthRoomSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        FourthRoomSavedData data = new FourthRoomSavedData();
        data.active = tag.getBoolean("Active");
        data.phase = FourthRoomPhase.valueOf(tag.getString("Phase"));
        data.requestedPlayerCount = tag.contains("RequestedPlayerCount") ? tag.getInt("RequestedPlayerCount") : 8;
        data.startedGameTick = tag.getLong("StartedGameTick");
        data.nextRotationTick = tag.getLong("NextRotationTick");
        data.rotationResumeTick = tag.getLong("RotationResumeTick");
        data.nextTaskTick = tag.getLong("NextTaskTick");
        data.taskDeadlineTick = tag.getLong("TaskDeadlineTick");
        data.rotationCount = tag.getInt("RotationCount");
        data.activeTaskId = tag.getString("ActiveTaskId");
        if (tag.contains("Winner")) {
            data.winner = FourthRoomTeam.valueOf(tag.getString("Winner"));
        }
        if (tag.contains("SceneLayout", Tag.TAG_COMPOUND)) {
            data.sceneLayout = FourthRoomSceneLayout.load(tag.getCompound("SceneLayout"));
        }
        for (Tag playerEntry : tag.getList("Players", Tag.TAG_COMPOUND)) {
            if (playerEntry instanceof CompoundTag playerTag) {
                FourthRoomPlayerState state = FourthRoomPlayerState.load(playerTag);
                data.players.put(state.playerId, state);
            }
        }
        for (Tag roomEntry : tag.getList("Rooms", Tag.TAG_COMPOUND)) {
            if (roomEntry instanceof CompoundTag roomTag) {
                FourthRoomRoomState state = FourthRoomRoomState.load(roomTag);
                data.rooms.put(state.roomId, state);
            }
        }
        for (Tag noteEntry : tag.getList("StickyNotes", Tag.TAG_COMPOUND)) {
            if (noteEntry instanceof CompoundTag noteTag) {
                data.stickyNotes.add(FourthRoomStickyNoteState.load(noteTag));
            }
        }
        return data;
    }

    public void resetMatchState() {
        active = false;
        phase = FourthRoomPhase.INACTIVE;
        startedGameTick = 0L;
        nextRotationTick = 0L;
        rotationResumeTick = 0L;
        nextTaskTick = 0L;
        taskDeadlineTick = 0L;
        rotationCount = 0;
        activeTaskId = "";
        winner = null;
        players.clear();
        rooms.clear();
        stickyNotes.clear();
        setDirty(true);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putBoolean("Active", active);
        tag.putString("Phase", phase.name());
        tag.putInt("RequestedPlayerCount", requestedPlayerCount);
        tag.putLong("StartedGameTick", startedGameTick);
        tag.putLong("NextRotationTick", nextRotationTick);
        tag.putLong("RotationResumeTick", rotationResumeTick);
        tag.putLong("NextTaskTick", nextTaskTick);
        tag.putLong("TaskDeadlineTick", taskDeadlineTick);
        tag.putInt("RotationCount", rotationCount);
        tag.putString("ActiveTaskId", activeTaskId == null ? "" : activeTaskId);
        if (winner != null) {
            tag.putString("Winner", winner.name());
        }
        tag.put("SceneLayout", sceneLayout.save());
        ListTag playerList = new ListTag();
        for (FourthRoomPlayerState playerState : players.values()) {
            playerList.add(playerState.save());
        }
        tag.put("Players", playerList);
        ListTag roomList = new ListTag();
        for (FourthRoomRoomState roomState : rooms.values()) {
            roomList.add(roomState.save());
        }
        tag.put("Rooms", roomList);
        ListTag noteList = new ListTag();
        for (FourthRoomStickyNoteState stickyNote : stickyNotes) {
            noteList.add(stickyNote.save());
        }
        tag.put("StickyNotes", noteList);
        return tag;
    }
}