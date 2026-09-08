/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package pro.fazeclan.river.stupid_express.modifier.twin_children;

import io.wifi.starrailexpress.api.RoleMethodDispatcher;
import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerMinigameTaskComponent;
import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import io.wifi.starrailexpress.cca.SREPlayerTaskComponent;
import io.wifi.starrailexpress.cca.SREPlayerTaskComponent.Task;
import io.wifi.starrailexpress.cca.SREPlayerTaskComponent.TrainTask;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.network.original.TaskCompletePayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role.ModifierEffects;
import org.agmas.noellesroles.role.TraitorAndModifiers;
import org.agmas.noellesroles.role_data.neutral.RavenRoleData;
import org.agmas.noellesroles.scene.SceneTaskManager;

import java.util.ArrayList;

/**
 * When one Twin Children player finishes a task, complete the partner's
 * current mood task and pending minigame task if they still have one.
 */
public final class TwinChildrenTaskShare {
    private static final ThreadLocal<Boolean> SHARING = ThreadLocal.withInitial(() -> false);

    private TwinChildrenTaskShare() {
    }

    public static void onTaskCompleted(Player player) {
        if (player.level().isClientSide || Boolean.TRUE.equals(SHARING.get())) {
            return;
        }
        if (!(player instanceof ServerPlayer completer)
                || !TwinChildrenHandler.isPairedAlivePlayer(completer)) {
            return;
        }
        ServerPlayer partner = TwinChildrenHandler.getPartner(completer);
        if (partner == null || !TwinChildrenHandler.isPairedAlivePlayer(partner)
                || !GameUtils.isPlayerAliveAndSurvivalIgnoreShitSplit(partner)) {
            return;
        }

        SHARING.set(true);
        try {
            completeMoodTaskIfPresent(partner);
            completeMinigameTaskIfPresent(partner);
        } finally {
            SHARING.set(false);
        }
    }

    private static void completeMoodTaskIfPresent(ServerPlayer partner) {
        SREPlayerTaskComponent component = SREPlayerTaskComponent.KEY.get(partner);
        if (component == null || component.tasks.isEmpty()) {
            return;
        }

        ArrayList<TrainTask> toCompleteHolder = new ArrayList<>(1);
        ArrayList<TrainTask> toDismiss = new ArrayList<>();
        TwinChildrenTaskShareLogic.splitCompletable(component.tasks.values(), task -> task.getType() == Task.MANIC,
                toCompleteHolder::add, toDismiss::add);
        if (toCompleteHolder.isEmpty()) {
            return;
        }
        TrainTask toComplete = toCompleteHolder.getFirst();

        if (component.playerMoodComponent == null) {
            component.playerMoodComponent = SREPlayerMoodComponent.KEY.get(partner);
        }
        float moodGain = GameConstants.MOOD_GAIN;
        if (component.parallelTaskGenerated) {
            moodGain += GameConstants.PARALLEL_TASK_COMPLETION_BONUS;
        }
        if (component.playerMoodComponent != null) {
            component.playerMoodComponent.addMood(moodGain);
        }
        ServerPlayNetworking.send(partner, new TaskCompletePayload());

        component.tasks.remove(toComplete.getType());
        component.parallelTaskTypes.remove(toComplete.getType());
        RoleMethodDispatcher.callOnFinishQuest(partner, toComplete.getName(), component.taskStreak, false);
        component.taskStreak++;
        if (toComplete.getType() == Task.EAT) {
            ModifierEffects.onBigEaterTaskComplete(partner);
        }
        notifyNearbyTaskComplete(partner);

        for (TrainTask leftover : toDismiss) {
            component.tasks.remove(leftover.getType());
            component.parallelTaskTypes.remove(leftover.getType());
            clearSceneTask(partner, leftover.getType());
        }
        clearSceneTask(partner, toComplete.getType());

        if (component.tasks.isEmpty()) {
            component.currentTaskAge = 0;
            component.parallelTaskGenerated = false;
            component.parallelTaskTypes.clear();
        }
        component.sync();
    }

    private static void completeMinigameTaskIfPresent(ServerPlayer partner) {
        SREPlayerMinigameTaskComponent minigame = SREPlayerMinigameTaskComponent.KEY.get(partner);
        if (minigame == null || !minigame.hasPendingTask()) {
            return;
        }
        minigame.onMinigameBlockCompleted(partner, partner.blockPosition(),
                GameConstants.MINIGAME_TASK_TOKEN_REWARD, minigame.targetMinigameId);
    }

    private static void clearSceneTask(ServerPlayer partner, Task taskType) {
        SceneTaskManager.Type sceneType = switch (taskType) {
            case LIGHT_STOVE -> SceneTaskManager.Type.LIGHT_STOVE;
            case CLEAN_DUST -> SceneTaskManager.Type.CLEAN_DUST;
            case TRANSPORT -> SceneTaskManager.Type.TRANSPORT;
            case PRAY -> SceneTaskManager.Type.PRAY;
            case PRUNE_BUSH -> SceneTaskManager.Type.PRUNE_BUSH;
            case HARVEST_CROP -> SceneTaskManager.Type.HARVEST_CROP;
            case BE_ALONE -> SceneTaskManager.Type.BE_ALONE;
            default -> null;
        };
        if (sceneType != null) {
            SceneTaskManager.clear(partner, sceneType);
        }
    }

    private static void notifyNearbyTaskComplete(ServerPlayer completingPlayer) {
        var worldModifiers = WorldModifierComponent.KEY.get(completingPlayer.level());
        if (worldModifiers != null) {
            for (Player nearby : completingPlayer.level().players()) {
                if (nearby != completingPlayer && nearby.distanceTo(completingPlayer) <= 11.0
                        && nearby instanceof ServerPlayer nearbySp
                        && GameUtils.isPlayerAliveAndSurvival(nearbySp)
                        && worldModifiers.isModifier(nearbySp.getUUID(), TraitorAndModifiers.MANIC)) {
                    ModifierEffects.onNearbyTaskComplete(nearbySp, completingPlayer);
                }
            }
        }
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(completingPlayer.level());
        if (gameWorld != null) {
            for (Player nearby : completingPlayer.level().players()) {
                if (nearby != completingPlayer
                        && nearby.distanceToSqr(completingPlayer) <= RavenRoleData.CHARGE_RADIUS
                                * RavenRoleData.CHARGE_RADIUS
                        && nearby instanceof ServerPlayer nearbySp
                        && GameUtils.isPlayerAliveAndSurvival(nearbySp)
                        && gameWorld.isRole(nearbySp, ModRoles.RAVEN)) {
                    RavenRoleData ravenData = RoleData.getNullable(RavenRoleData.class, nearbySp);
                    if (ravenData != null) {
                        ravenData.onNearbyTaskComplete();
                    }
                }
            }
        }
    }
}
