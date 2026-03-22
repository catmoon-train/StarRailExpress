package io.wifi.starrailexpress.api.replay;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.UUID;

public record GameReplay(int playerCount, GameUtils.WinStatus winningTeam, List<ReplayPlayerInfo> players,
                         List<ReplayEvent> timelineEvents) {

    public record ReplayPlayerInfo(UUID uuid, String name, SRERole finalRole) {
    }

    public enum EventType {
        PLAYER_KILL,
        PLAYER_POISONED,
        GUN_FIRED,
        GRENADE_THROWN,
        ITEM_USED,
        TASK_COMPLETE,
        STORE_BUY,
        DOOR_OPEN,
        DOOR_CLOSE,
        LOCKPICK_ATTEMPT,
        MOOD_CHANGE,
        NOTE_EDIT,
        GAME_START,
        GAME_END,
        PLAYER_JOIN,
        PLAYER_LEAVE,
        ROLE_ASSIGNMENT,
        CUSTOM_MESSAGE,
        DOOR_LOCK,
        DOOR_UNLOCK,
        BLACKOUT_START,
        BLACKOUT_END,
        ROUND_END,
        KEY_USED,
        SKILL_USED,
        ITEM_USE,
        PSYCHO_STATE_CHANGE
    }

    public interface EventDetails {
        // 标记接口
    }

    public record PlayerKillDetails(UUID killerUuid, UUID victimUuid, ResourceLocation deathReason) implements EventDetails {}
    public record PlayerPoisonedDetails(UUID poisonerUuid, UUID victimUuid) implements EventDetails {}
    public record GunFiredDetails(UUID playerUuid, boolean hit, UUID targetUuid) implements EventDetails {}
    public record GrenadeThrownDetails(UUID playerUuid, BlockPos position) implements EventDetails {}
    public record ItemUsedDetails(UUID playerUuid, ResourceLocation itemId) implements EventDetails {}
    public record TaskCompleteDetails(UUID playerUuid, ResourceLocation taskId) implements EventDetails {}
    public record StoreBuyDetails(UUID playerUuid, ResourceLocation itemId, int cost) implements EventDetails {}
    public record DoorActionDetails(UUID playerUuid, BlockPos doorPos, boolean success) implements EventDetails {}
    public record LockpickAttemptDetails(UUID playerUuid, BlockPos doorPos, boolean success) implements EventDetails {}
    public record MoodChangeDetails(UUID playerUuid, int oldMood, int newMood) implements EventDetails {}
    public record NoteEditDetails(UUID playerUuid, String noteContent) implements EventDetails {}
    public record PsychoStateChangeDetails(UUID playerUuid, int oldState, int newState) implements EventDetails {}
    public record KeyUsedDetails(UUID playerUuid, ResourceLocation keyItemId, BlockPos doorPos) implements EventDetails {}
    public record BlackoutEventDetails(long duration) implements EventDetails {}
    public record RoundEndDetails(GameUtils.WinStatus roundResult) implements EventDetails {}
    public record CustomEventDetails(ResourceLocation eventId, String data) implements EventDetails {}
}