package org.agmas.noellesroles.game.modes.fourthroom.task;

import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.game.modes.fourthroom.config.FourthRoomConfig;
import org.agmas.noellesroles.game.modes.fourthroom.game.FourthRoomGameManager;
import org.agmas.noellesroles.game.modes.fourthroom.game.FourthRoomPlayerState;
import org.agmas.noellesroles.game.modes.fourthroom.game.FourthRoomSavedData;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class FourthRoomTaskScheduler {
    private final FourthRoomGameManager manager;
    private final FourthRoomSavedData data;
    private final FourthRoomConfig config;

    public FourthRoomTaskScheduler(FourthRoomGameManager manager, FourthRoomSavedData data, FourthRoomConfig config) {
        this.manager = manager;
        this.data = data;
        this.config = config;
    }

    public void tick() {
        long currentTick = manager.currentTick();
        if (hasActiveTask() && currentTick >= data.taskDeadlineTick) {
            clearActiveTask();
            manager.broadcast("Fourth Room task window expired.");
        }
        if (!hasActiveTask() && currentTick >= data.nextTaskTick) {
            startRandomTask();
        }
    }

    public void scheduleNextTask(long currentTick) {
        data.nextTaskTick = currentTick + randomIntervalTicks();
        data.setDirty(true);
    }

    public boolean completeTask(java.util.UUID playerId) {
        FourthRoomPlayerState state = data.players.get(playerId);
        if (state == null || !state.alive || !hasActiveTask() || state.taskCompleted) {
            return false;
        }
        FourthRoomTaskType taskType = FourthRoomTaskType.byId(data.activeTaskId);
        if (taskType == null) {
            return false;
        }
        state.taskCompleted = true;
        int reward = ThreadLocalRandom.current().nextInt(taskType.minReward(), taskType.maxReward() + 1);
        manager.grantCoins(playerId, reward, "task_complete");
        manager.sendPrivate(playerId, Component.translatable("task.fourth_room.completed", Component.translatable(taskType.descriptionKey()), reward));
        data.setDirty(true);
        manager.syncMatchState();
        return true;
    }

    public boolean hasActiveTask() {
        return data.activeTaskId != null && !data.activeTaskId.isBlank() && manager.currentTick() < data.taskDeadlineTick;
    }

    public void clearActiveTask() {
        data.activeTaskId = "";
        data.taskDeadlineTick = 0L;
        for (FourthRoomPlayerState playerState : data.players.values()) {
            playerState.taskCompleted = false;
        }
        scheduleNextTask(manager.currentTick());
        data.setDirty(true);
    }

    public void startRandomTask() {
        FourthRoomTaskType[] values = FourthRoomTaskType.values();
        FourthRoomTaskType selected = values[ThreadLocalRandom.current().nextInt(values.length)];
        data.activeTaskId = selected.id();
        data.taskDeadlineTick = manager.currentTick() + config.taskDurationSeconds * 20L;
        for (FourthRoomPlayerState playerState : data.players.values()) {
            playerState.taskCompleted = false;
        }
        data.setDirty(true);
        manager.broadcast("Task started: " + selected.descriptionKey() + " (" + config.taskDurationSeconds + "s)");
        manager.syncMatchState();
    }

    public List<String> activeTaskDescriptions() {
        List<String> descriptions = new ArrayList<>();
        FourthRoomTaskType current = FourthRoomTaskType.byId(data.activeTaskId);
        if (current != null) {
            descriptions.add(current.descriptionKey());
        }
        return descriptions;
    }

    private long randomIntervalTicks() {
        int min = Math.min(config.taskMinIntervalSeconds, config.taskMaxIntervalSeconds);
        int max = Math.max(config.taskMinIntervalSeconds, config.taskMaxIntervalSeconds);
        return ThreadLocalRandom.current().nextLong(min, max + 1L) * 20L;
    }
}