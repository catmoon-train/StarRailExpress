package io.wifi.starrailexpress.api.replay;

// import java.util.*;
// import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
// import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import io.wifi.starrailexpress.SRE;

/**
 * 没任何用处的发包
 */
public record ReplayPayload(GameReplay replay) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ReplayPayload> ID = new CustomPacketPayload.Type<>(SRE.id("replay"));
    public static final StreamCodec<FriendlyByteBuf, ReplayPayload> CODEC = StreamCodec.ofMember(ReplayPayload::write,
            ReplayPayload::new);

    private ReplayPayload(FriendlyByteBuf buf) {
        this(readReplay(buf));
    }

    private void write(FriendlyByteBuf buf) {
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    private static GameReplay readReplay(FriendlyByteBuf buf) {
        return null;
        // int playerCount = buf.readInt();
        // GameUtils.WinStatus winningTeam =
        // buf.readEnum(GameUtils.WinStatus.class);

        // int numPlayers = buf.readInt();
        // List<GameReplay.ReplayPlayerInfo> players = new ArrayList<>();
        // for (int i = 0; i < numPlayers; i++) {
        // UUID uuid = buf.readUUID();
        // String name = buf.readUtf();
        // ResourceLocation roleId = buf.readResourceLocation();

        // Role role = TMMRoles.ROLES.values().stream()
        // .filter(r -> r.identifier().equals(roleId))
        // .findFirst()
        // .orElse(TMMRoles.CIVILIAN);
        // players.add(new GameReplay.ReplayPlayerInfo(uuid, name, role));
        // }

        // int numEvents = buf.readInt();
        // List<ReplayEvent> timelineEvents = new ArrayList<>();
        // for (int i = 0; i < numEvents; i++) {
        // ReplayEventTypes.EventType eventType =
        // buf.readEnum(ReplayEventTypes.EventType.class);
        // long timestamp = buf.readInt();
        // ReplayEventTypes.EventDetails details = null;

        // switch (eventType) {
        // case PLAYER_KILL: {
        // int killerIndex = buf.readVarInt();
        // UUID killerUuid = players.get(killerIndex).uuid();
        // int victimIndex = buf.readVarInt();
        // UUID victimUuid = players.get(victimIndex).uuid();
        // ResourceLocation deathReason = buf.readResourceLocation();
        // details = new ReplayEventTypes.PlayerKillDetails(killerUuid, victimUuid,
        // deathReason);
        // break;
        // }
        // case PLAYER_POISONED: {
        // int poisonerIndex = buf.readVarInt();
        // UUID poisonerUuid = players.get(poisonerIndex).uuid();
        // int victimIndex = buf.readVarInt();
        // UUID poisonedVictimUuid = players.get(victimIndex).uuid();
        // details = new ReplayEventTypes.PlayerPoisonedDetails(poisonerUuid,
        // poisonedVictimUuid);
        // break;
        // }
        // case GRENADE_THROWN: {
        // int throwerIndex = buf.readVarInt();
        // UUID throwerUuid = players.get(throwerIndex).uuid();
        // BlockPos pos = buf.readBlockPos();
        // details = new ReplayEventTypes.GrenadeThrownDetails(throwerUuid, pos);
        // break;
        // }
        // case ITEM_USED: {
        // int userIndex = buf.readVarInt();
        // UUID userUuid = players.get(userIndex).uuid();
        // ResourceLocation itemId = buf.readResourceLocation();
        // details = new ReplayEventTypes.ItemUsedDetails(userUuid, itemId);
        // break;
        // }
        // case TASK_COMPLETE: {
        // int playerIndex = buf.readVarInt();
        // UUID playerUuid = players.get(playerIndex).uuid();
        // ResourceLocation taskId = buf.readResourceLocation();
        // details = new ReplayEventTypes.TaskCompleteDetails(playerUuid, taskId);
        // break;
        // }
        // case STORE_BUY: {
        // int buyerIndex = buf.readVarInt();
        // UUID buyerUuid = players.get(buyerIndex).uuid();
        // ResourceLocation itemId = buf.readResourceLocation();
        // int cost = buf.readInt();
        // details = new ReplayEventTypes.StoreBuyDetails(buyerUuid, itemId, cost);
        // break;
        // }
        // case DOOR_OPEN:
        // case DOOR_CLOSE: {
        // int playerIndex = buf.readVarInt();
        // UUID playerUuid = players.get(playerIndex).uuid();
        // BlockPos doorPos = buf.readBlockPos();
        // boolean success = buf.readBoolean();
        // details = new ReplayEventTypes.DoorActionDetails(playerUuid, doorPos,
        // success);
        // break;
        // }
        // case LOCKPICK_ATTEMPT: {
        // int playerIndex = buf.readVarInt();
        // UUID playerUuid = players.get(playerIndex).uuid();
        // BlockPos doorPos = buf.readBlockPos();
        // boolean success = buf.readBoolean();
        // details = new ReplayEventTypes.LockpickAttemptDetails(playerUuid, doorPos,
        // success);
        // break;
        // }
        // case MOOD_CHANGE: {
        // int playerIndex = buf.readVarInt();
        // UUID playerUuid = players.get(playerIndex).uuid();
        // int oldMood = buf.readInt();
        // int newMood = buf.readInt();
        // details = new ReplayEventTypes.MoodChangeDetails(playerUuid, oldMood,
        // newMood);
        // break;
        // }
        // case ARMOR_BREAK: {
        // int playerIndex = buf.readVarInt();
        // UUID playerUuid = players.get(playerIndex).uuid();
        // details = new ReplayEventTypes.ArmorBreakDetails(playerUuid);
        // break;
        // }
        // case PLAYER_REVIVAL: {
        // int playerIndex = buf.readVarInt();
        // UUID playerUuid = players.get(playerIndex).uuid();
        // String job = buf.readUtf();
        // details = new ReplayEventTypes.PlayerRevivalDetails(playerUuid, job);
        // break;
        // }
        // case CHANGE_ROLE: {
        // int playerIndex = buf.readVarInt();
        // UUID playerUuid = players.get(playerIndex).uuid();
        // String oldJob = buf.readUtf();
        // String newJob = buf.readUtf();
        // details = new ReplayEventTypes.ChangeRoleDetails(playerUuid, oldJob, newJob);
        // break;
        // }
        // // Add more cases for other event types if needed
        // default:
        // break;
        // }
        // timelineEvents.add(new ReplayEvent(eventType, timestamp, details));
        // }
        // return new GameReplay(playerCount, winningTeam, players, timelineEvents);
    }

    // private static void writeReplay(FriendlyByteBuf buf, GameReplay replay) {
    // buf.writeInt(replay.playerCount());
    // buf.writeEnum(replay.winningTeam());

    // buf.writeInt(replay.players().size());
    // for (GameReplay.ReplayPlayerInfo playerInfo : replay.players()) {
    // buf.writeUUID(playerInfo.uuid());
    // buf.writeUtf(playerInfo.name());
    // buf.writeResourceLocation(playerInfo.finalRole().identifier());
    // }

    // Map<UUID, Integer> playerUuidToIndex = new HashMap<>();
    // for (int i = 0; i < replay.players().size(); i++) {
    // playerUuidToIndex.put(replay.players().get(i).uuid(), i);
    // }

    // buf.writeInt(replay.timelineEvents().size());
    // for (ReplayEvent event : replay.timelineEvents()) {
    // buf.writeEnum(event.eventType());
    // buf.writeInt((int) event.timestamp());

    // switch (event.eventType()) {
    // case PLAYER_KILL:
    // ReplayEventTypes.PlayerKillDetails killDetails =
    // (ReplayEventTypes.PlayerKillDetails) event
    // .details();
    // buf.writeVarInt(playerUuidToIndex.get(killDetails.killerUuid()));
    // buf.writeVarInt(playerUuidToIndex.get(killDetails.victimUuid()));
    // buf.writeResourceLocation(killDetails.deathReason());
    // break;
    // case PLAYER_POISONED:
    // ReplayEventTypes.PlayerPoisonedDetails poisonedDetails =
    // (ReplayEventTypes.PlayerPoisonedDetails) event
    // .details();
    // buf.writeVarInt(playerUuidToIndex.get(poisonedDetails.poisonerUuid()));
    // buf.writeVarInt(playerUuidToIndex.get(poisonedDetails.victimUuid()));
    // break;
    // case GRENADE_THROWN:
    // ReplayEventTypes.GrenadeThrownDetails grenadeDetails =
    // (ReplayEventTypes.GrenadeThrownDetails) event
    // .details();
    // buf.writeVarInt(playerUuidToIndex.get(grenadeDetails.playerUuid()));
    // buf.writeBlockPos(grenadeDetails.position());
    // break;
    // case ITEM_USED:
    // ReplayEventTypes.ItemUsedDetails itemDetails =
    // (ReplayEventTypes.ItemUsedDetails) event.details();
    // buf.writeVarInt(playerUuidToIndex.get(itemDetails.playerUuid()));
    // buf.writeResourceLocation(itemDetails.itemId());
    // break;
    // case TASK_COMPLETE:
    // ReplayEventTypes.TaskCompleteDetails taskDetails =
    // (ReplayEventTypes.TaskCompleteDetails) event
    // .details();
    // buf.writeVarInt(playerUuidToIndex.get(taskDetails.playerUuid()));
    // buf.writeResourceLocation(taskDetails.taskId());
    // break;
    // case STORE_BUY:
    // ReplayEventTypes.StoreBuyDetails storeDetails =
    // (ReplayEventTypes.StoreBuyDetails) event.details();
    // buf.writeVarInt(playerUuidToIndex.get(storeDetails.playerUuid()));
    // buf.writeResourceLocation(storeDetails.itemId());
    // buf.writeInt(storeDetails.cost());
    // break;
    // case DOOR_OPEN:
    // case DOOR_CLOSE:
    // ReplayEventTypes.DoorActionDetails doorDetails =
    // (ReplayEventTypes.DoorActionDetails) event
    // .details();
    // buf.writeVarInt(playerUuidToIndex.get(doorDetails.playerUuid()));
    // buf.writeBlockPos(doorDetails.doorPos());
    // buf.writeBoolean(doorDetails.success());
    // break;
    // case LOCKPICK_ATTEMPT:
    // ReplayEventTypes.LockpickAttemptDetails lockpickDetails =
    // (ReplayEventTypes.LockpickAttemptDetails) event
    // .details();
    // buf.writeVarInt(playerUuidToIndex.get(lockpickDetails.playerUuid()));
    // buf.writeBlockPos(lockpickDetails.doorPos());
    // buf.writeBoolean(lockpickDetails.success());
    // break;
    // case MOOD_CHANGE:
    // ReplayEventTypes.MoodChangeDetails moodDetails =
    // (ReplayEventTypes.MoodChangeDetails) event
    // .details();
    // buf.writeVarInt(playerUuidToIndex.get(moodDetails.playerUuid()));
    // buf.writeInt(moodDetails.oldMood());
    // buf.writeInt(moodDetails.newMood());
    // break;
    // // Add more cases for other event types if needed
    // case BLACKOUT_END:
    // break;
    // case BLACKOUT_START:
    // break;
    // case PLAYER_REVIVAL:
    // ReplayEventTypes.PlayerRevivalDetails revivalDetails =
    // (ReplayEventTypes.PlayerRevivalDetails) event
    // .details();
    // buf.writeVarInt(playerUuidToIndex.get(revivalDetails.player()));
    // buf.writeUtf(revivalDetails.Role());
    // break;
    // case CHANGE_ROLE:
    // ReplayEventTypes.ChangeRoleDetails roleDetails =
    // (ReplayEventTypes.ChangeRoleDetails) event
    // .details();
    // buf.writeVarInt(playerUuidToIndex.get(roleDetails.player()));
    // buf.writeUtf(roleDetails.oldRole());
    // buf.writeUtf(roleDetails.newRole());
    // break;
    // case CUSTOM_EVENT:
    // break;
    // case DOOR_LOCK:
    // break;
    // case DOOR_UNLOCK:
    // break;
    // case GAME_END:
    // break;
    // case GAME_START:
    // break;
    // case PLAYER_JOIN:
    // break;
    // case PLAYER_LEAVE:
    // break;
    // case PSYCHO_STATE_CHANGE:
    // break;
    // default:
    // break;
    // }
    // }
    // }
}