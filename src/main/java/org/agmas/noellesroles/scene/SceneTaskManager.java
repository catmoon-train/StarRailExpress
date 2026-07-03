package org.agmas.noellesroles.scene;

import io.wifi.starrailexpress.api.RoleMethodDispatcher;
import io.wifi.starrailexpress.cca.SREPlayerTaskComponent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.agmas.noellesroles.content.block.scene.StatueBlock;

import java.util.*;

/**
 * 独立场景任务系统（不进入随机任务池）。任务由任务点方块交互或指令指派，完成后走现有任务奖励流程。
 */
public final class SceneTaskManager {
    private SceneTaskManager() {
    }

    /**
     * 场景任务完成回调接口。
     * 当任意场景任务完成时触发，用于 SREPlayerTaskComponent 等外部系统监听任务完成事件。
     */
    @FunctionalInterface
    public interface SceneTaskCompletionCallback {
        /**
         * @param player 完成任务的玩家
         * @param type   完成的任务类型
         */
        void onSceneTaskCompleted(ServerPlayer player, Type type);
    }

    /** 已注册的任务完成回调列表。 */
    private static final List<SceneTaskCompletionCallback> COMPLETION_CALLBACKS = new ArrayList<>();

    /** 注册场景任务完成回调。 */
    public static void registerCompletionCallback(SceneTaskCompletionCallback callback) {
        COMPLETION_CALLBACKS.add(callback);
    }

    /** 取消注册场景任务完成回调。 */
    public static void unregisterCompletionCallback(SceneTaskCompletionCallback callback) {
        COMPLETION_CALLBACKS.remove(callback);
    }

    public enum Type {
        LIGHT_STOVE, CLEAN_DUST, BE_ALONE, TRANSPORT, PRAY, PRUNE_BUSH, HARVEST_CROP
    }

    /** 5 秒。 */
    private static final int STOVE_TICKS = 100;
    private static final int PRAY_TICKS = 100;
    /** 6 秒。 */
    private static final int ALONE_TICKS = 120;
    private static final double ALONE_RADIUS = 4.0;
    private static final double STOVE_RADIUS = 4.0;
    private static final int CROP_BOUNCES = 4;
    private static final int DUST_STROKES = 4;

    private static final class State {
        Type type;
        int timer;
        int counter;
        boolean carrying;
        BlockPos anchor;
    }

    private static final Map<UUID, State> ACTIVE = new HashMap<>();

    public static void assign(ServerPlayer player, Type type) {
        State s = new State();
        s.type = type;
        ACTIVE.put(player.getUUID(), s);
        player.displayClientMessage(
                Component.translatable("message.noellesroles.scene_task.assigned",
                        Component.translatable("scene_task.noellesroles." + type.name().toLowerCase())),
                true);
    }

    public static void clear(Player player) {
        ACTIVE.remove(player.getUUID());
    }

    public static void clear(Player player, Type type) {
        State s = ACTIVE.get(player.getUUID());
        if (s != null && s.type == type) {
            ACTIVE.remove(player.getUUID());
        }
    }

    public static Type getType(Player player) {
        State s = ACTIVE.get(player.getUUID());
        return s == null ? null : s.type;
    }

    public static boolean hasTransportTask(Player player) {
        return getType(player) == Type.TRANSPORT;
    }

    private static boolean has(Player player, Type type) {
        State s = ACTIVE.get(player.getUUID());
        return s != null && s.type == type;
    }

    // ───────────── 交互型任务上报 ─────────────

    public static void reportStoveLit(Player player, BlockPos stove) {
        State s = ACTIVE.get(player.getUUID());
        if (s != null && s.type == Type.LIGHT_STOVE) {
            s.anchor = stove.immutable();
        }
    }

    /** 返回是否需要清理玩家快捷栏的刷子（任务完成）。 */
    public static void reportDustStroke(ServerPlayer player, boolean blockCleaned) {
        State s = ACTIVE.get(player.getUUID());
        if (s != null && s.type == Type.CLEAN_DUST) {
            s.counter++;
            if (blockCleaned || s.counter >= DUST_STROKES) {
                // 清理快捷栏中的刷子
                net.minecraft.world.entity.player.Inventory inv = player.getInventory();
                for (int i = 0; i < inv.getContainerSize(); i++) {
                    if (inv.getItem(i).is(net.minecraft.world.item.Items.BRUSH)) {
                        inv.setItem(i, net.minecraft.world.item.ItemStack.EMPTY);
                    }
                }
                complete(player);
            }
        } else if (blockCleaned && hasSceneTask(player, Type.CLEAN_DUST)) {
            removeInventoryItem(player, net.minecraft.world.item.Items.BRUSH);
            notifyTaskComponent(player, Type.CLEAN_DUST);
        }
    }

    public static void reportTransportPickup(ServerPlayer player) {
        State s = ACTIVE.get(player.getUUID());
        if (s != null && s.type == Type.TRANSPORT) {
            s.carrying = true;
            player.displayClientMessage(Component.translatable("message.noellesroles.scene_task.transport_pickup"), true);
        }
    }

    public static void reportTransportDeliver(ServerPlayer player) {
        State s = ACTIVE.get(player.getUUID());
        if (s != null && s.type == Type.TRANSPORT && s.carrying) {
            complete(player);
        }
    }

    public static void reportBushPruned(ServerPlayer player) {
        State s = ACTIVE.get(player.getUUID());
        if (s != null && s.type == Type.PRUNE_BUSH) {
            // 清理快捷栏中的剪刀
            net.minecraft.world.entity.player.Inventory inv = player.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                if (inv.getItem(i).is(net.minecraft.world.item.Items.SHEARS)) {
                    inv.setItem(i, net.minecraft.world.item.ItemStack.EMPTY);
                }
            }
            complete(player);
        } else if (hasSceneTask(player, Type.PRUNE_BUSH)) {
            removeInventoryItem(player, net.minecraft.world.item.Items.SHEARS);
            notifyTaskComponent(player, Type.PRUNE_BUSH);
        }
    }

    public static void reportCropBounce(ServerPlayer player) {
        State s = ACTIVE.get(player.getUUID());
        if (s != null && s.type == Type.HARVEST_CROP) {
            s.counter++;
            if (s.counter >= CROP_BOUNCES) {
                complete(player);
            }
        } else if (hasSceneTask(player, Type.HARVEST_CROP)) {
            notifyTaskComponent(player, Type.HARVEST_CROP);
        }
    }

    // ───────────── 定时型任务（每世界 tick） ─────────────

    public static void tick(ServerLevel level) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, State>> it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, State> e = it.next();
            Player p = level.getPlayerByUUID(e.getKey());
            if (!(p instanceof ServerPlayer player)) {
                continue; // 玩家不在此维度
            }
            State s = e.getValue();
            switch (s.type) {
                case LIGHT_STOVE -> tickStove(level, player, s, it);
                case PRAY -> tickPray(level, player, s, it);
                case BE_ALONE -> tickAlone(level, player, s, it);
                default -> {
                }
            }
        }
    }

    private static void tickStove(ServerLevel level, ServerPlayer player, State s,
            Iterator<Map.Entry<UUID, State>> it) {
        if (s.anchor == null) {
            return;
        }
        boolean lit = level.getBlockState(s.anchor)
                .getBlock() instanceof org.agmas.noellesroles.content.block.scene.StoveBlock
                && level.getBlockState(s.anchor)
                        .getValue(org.agmas.noellesroles.content.block.scene.StoveBlock.LIT);
        if (lit && player.distanceToSqr(s.anchor.getX() + 0.5, s.anchor.getY() + 0.5,
                s.anchor.getZ() + 0.5) <= STOVE_RADIUS * STOVE_RADIUS) {
            if (++s.timer >= STOVE_TICKS) {
                it.remove();
                completeNoRemove(player, s.type);
            }
        } else {
            s.timer = 0;
        }
    }

    private static void tickPray(ServerLevel level, ServerPlayer player, State s,
            Iterator<Map.Entry<UUID, State>> it) {
        HitResult hit = player.pick(6.0, 1.0F, false);
        boolean looking = hit instanceof BlockHitResult bhr
                && level.getBlockState(bhr.getBlockPos()).getBlock() instanceof StatueBlock;
        if (looking) {
            if (++s.timer >= PRAY_TICKS) {
                it.remove();
                completeNoRemove(player, s.type);
            }
        } else {
            s.timer = 0;
        }
    }

    private static void tickAlone(ServerLevel level, ServerPlayer player, State s,
            Iterator<Map.Entry<UUID, State>> it) {
        AABB box = player.getBoundingBox().inflate(ALONE_RADIUS);
        boolean someoneNear = level.getEntitiesOfClass(Player.class, box,
                other -> other != player && other.isAlive() && !other.isSpectator()).size() > 0;
        if (!someoneNear) {
            if (++s.timer >= ALONE_TICKS) {
                it.remove();
                completeNoRemove(player, s.type);
            }
        } else {
            s.timer = 0;
        }
    }

    // ───────────── 完成 ─────────────

    public static void complete(ServerPlayer player) {
        State s = ACTIVE.get(player.getUUID());
        if (s != null) {
            Type completedType = s.type;
            ACTIVE.remove(player.getUUID());
            completeNoRemove(player, completedType);
        }
    }

    private static void completeNoRemove(ServerPlayer player, Type completedType) {
        if (!hasSceneTask(player, completedType)) {
            RoleMethodDispatcher.callOnFinishQuest(player, "scene_task", 0, false);
        }
        // 触发所有已注册的场景任务完成回调
        notifyTaskComponent(player, completedType);
        if (player.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                    player.getX(), player.getY() + 1.0, player.getZ(), 20, 0.4, 0.6, 0.4, 0.0);
        }
        player.displayClientMessage(Component.translatable("message.noellesroles.scene_task.complete"), true);
    }

    private static void notifyTaskComponent(ServerPlayer player, Type completedType) {
        if (!COMPLETION_CALLBACKS.isEmpty()) {
            for (SceneTaskCompletionCallback callback : COMPLETION_CALLBACKS) {
                callback.onSceneTaskCompleted(player, completedType);
            }
        }
    }

    private static boolean hasSceneTask(ServerPlayer player, Type type) {
        SREPlayerTaskComponent.Task taskType = switch (type) {
            case LIGHT_STOVE -> SREPlayerTaskComponent.Task.LIGHT_STOVE;
            case CLEAN_DUST -> SREPlayerTaskComponent.Task.CLEAN_DUST;
            case TRANSPORT -> SREPlayerTaskComponent.Task.TRANSPORT;
            case PRAY -> SREPlayerTaskComponent.Task.PRAY;
            case PRUNE_BUSH -> SREPlayerTaskComponent.Task.PRUNE_BUSH;
            case HARVEST_CROP -> SREPlayerTaskComponent.Task.HARVEST_CROP;
            case BE_ALONE -> SREPlayerTaskComponent.Task.BE_ALONE;
        };
        SREPlayerTaskComponent comp = SREPlayerTaskComponent.KEY.get(player);
        return comp != null && comp.tasks.get(taskType) instanceof SREPlayerTaskComponent.SceneTriggeredTask;
    }

    private static void removeInventoryItem(ServerPlayer player, net.minecraft.world.item.Item item) {
        net.minecraft.world.entity.player.Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).is(item)) {
                inv.setItem(i, net.minecraft.world.item.ItemStack.EMPTY);
                return;
            }
        }
    }
}
