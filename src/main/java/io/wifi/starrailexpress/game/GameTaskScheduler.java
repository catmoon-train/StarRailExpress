/* SPDX-License-Identifier: GPL-3.0-or-later */
package io.wifi.starrailexpress.game;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.agmas.noellesroles.utils.EntityClearUtils;

import static io.wifi.starrailexpress.game.GameUtils.*;

/** 一次注册的服务器事件与重置任务调度，保留串行队首/并行快照的推进方式。 */
final class GameTaskScheduler {
    private static boolean registered;

    private GameTaskScheduler() {
    }

    static void register() {
        if (registered) return;
        registered = true;
        ServerEntityEvents.ENTITY_LOAD.register(GameTaskScheduler::onEntityLoad);
        ServerTickEvents.START_SERVER_TICK.register(GameTaskScheduler::tick);
    }

    private static void onEntityLoad(Entity entity, ServerLevel world) {
        // 强载区块的实体延后数刻入世界，只在重置后的限时区块集合中清理。
        // 不能改成整图 resetEntities，否则局中重载区块会误删当前局的坐骑、掉落物等。
        if (chunksToClearEntities.isEmpty()) return;
        if (world.getGameTime() > chunksToClearEntitiesDeadline) {
            chunksToClearEntities.clear();
            return;
        }
        if (!chunksToClearEntities.contains(entity.chunkPosition())) return;
        if (EntityClearUtils.shouldClearOnReset(entity)) {
            // 避免在入世界回调内重入实体管理器。
            world.getServer().execute(() -> {
                if (!entity.isRemoved()) entity.discard();
            });
        }
    }

    private static void tick(MinecraftServer server) {
        if (!serverTaskQueue.isEmpty()) {
            var task = serverTaskQueue.getFirst();
            if (!task.finished && task.onTick(server)) {
                task.finished = true;
                if (!task.cancelled) task.onFinished();
            }
            serverTaskQueue.removeIf(t -> t.finished || t.cancelled);
        }
        if (!serverAsynTaskLists.isEmpty()) {
            // 本 tick 运行期间新加入的任务仍等下个 tick，不改变原有执行顺序。
            int size = serverAsynTaskLists.size();
            for (int i = 0; i < size; i++) {
                var task = serverAsynTaskLists.get(i);
                if (!task.finished && task.onTick(server)) {
                    task.finished = true;
                    if (!task.cancelled) task.onFinished();
                }
            }
            serverAsynTaskLists.removeIf(t -> t.finished || t.cancelled);
        }
    }
}
