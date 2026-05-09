package io.wifi.starrailexpress.network;

import io.wifi.starrailexpress.content.block_entity.EntityInteractionBlockEntity;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * 实体交互方块的服务端网络处理
 * 只在服务端加载，负责注册服务端处理器
 */
public class EntityInteractionBlockServerNetwork {

    /**
     * 服务端初始化：注册处理器
     */
    public static void register() {
        // 处理客户端发来的 SaveConfig 包
        ServerPlayNetworking.registerGlobalReceiver(EntityInteractionBlockPayload.SaveConfig.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                BlockEntity be = player.level().getBlockEntity(payload.pos());
                if (be instanceof EntityInteractionBlockEntity entity) {
                    // 解析数据
                    CompoundTag data = payload.data();
                    List<EntityInteractionBlockEntity.TriggerCondition> conditions = new ArrayList<>();
                    List<EntityInteractionBlockEntity.TriggerAction> actions = new ArrayList<>();

                    if (data.contains("Conditions", ListTag.TAG_LIST)) {
                        ListTag list = data.getList("Conditions", ListTag.TAG_COMPOUND);
                        for (int i = 0; i < list.size(); i++) {
                            conditions.add(EntityInteractionBlockEntity.TriggerCondition.fromNbt(list.getCompound(i)));
                        }
                    }

                    if (data.contains("Actions", ListTag.TAG_LIST)) {
                        ListTag list = data.getList("Actions", ListTag.TAG_COMPOUND);
                        for (int i = 0; i < list.size(); i++) {
                            actions.add(EntityInteractionBlockEntity.TriggerAction.fromNbt(list.getCompound(i)));
                        }
                    }

                    int cooldown = data.getInt("CooldownTicks");
                    boolean isTeleportPoint = data.getBoolean("IsTeleportPoint");
                    int teleportPointId = data.getInt("TeleportPointId");

                    // 任务路标相关
                    boolean isTaskMarker = data.getBoolean("IsTaskMarker");
                    int taskMarkerColor = data.contains("TaskMarkerColor") ? data.getInt("TaskMarkerColor") : 0xFFFFFF;
                    EntityInteractionBlockEntity.TaskHighlightCondition taskHighlightCondition = EntityInteractionBlockEntity.TaskHighlightCondition.NONE;
                    if (data.contains("TaskHighlightCondition")) {
                        taskHighlightCondition = EntityInteractionBlockEntity.TaskHighlightCondition.valueOf(data.getString("TaskHighlightCondition"));
                    }
                    String taskHighlightTaskType = data.getString("TaskHighlightTaskType");
                    if (taskHighlightTaskType == null || taskHighlightTaskType.isEmpty()) taskHighlightTaskType = "*";
                    String taskHighlightCustomTaskId = data.getString("TaskHighlightCustomTaskId");
                    if (taskHighlightCustomTaskId == null) taskHighlightCustomTaskId = "";
                    int taskInstinctId = data.contains("TaskInstinctId") ? data.getInt("TaskInstinctId") : 100;

                    entity.setTeleportPoint(isTeleportPoint);
                    entity.setTeleportPointId(teleportPointId);
                    entity.setTaskMarker(isTaskMarker);
                    entity.setTaskMarkerColor(taskMarkerColor);
                    entity.setTaskHighlightCondition(taskHighlightCondition);
                    entity.setTaskHighlightTaskType(taskHighlightTaskType);
                    entity.setTaskHighlightCustomTaskId(taskHighlightCustomTaskId);
                    entity.setTaskInstinctId(taskInstinctId);

                    entity.updateFromServer(conditions, actions, cooldown);

                    // 标记服务器端 BlockEntity 已变更
                    entity.setChanged();
                    // 发送区块更新通知客户端
                    Level level = player.level();
                    level.sendBlockUpdated(entity.getBlockPos(), entity.getBlockState(), entity.getBlockState(), 3);

                    // 强制同步 BlockEntity 数据到所有客户端
                    if (level instanceof ServerLevel serverLevel) {
                        for (ServerPlayer allPlayer : serverLevel.players()) {
                            sendSyncBlockEntity(allPlayer, payload.pos(), entity);
                        }
                    }
                }
            });
        });
    }

    /**
     * 服务端发送 SyncBlockEntity 包给玩家（不打开UI，只同步数据）
     */
    public static void sendSyncBlockEntity(ServerPlayer player, BlockPos pos, EntityInteractionBlockEntity entity) {
        CompoundTag data = new CompoundTag();

        data.putInt("TeleportPointId", entity.getTeleportPointId());
        data.putBoolean("IsTeleportPoint", entity.isTeleportPoint());

        // 同步 Conditions
        ListTag conditionsTag = new ListTag();
        for (EntityInteractionBlockEntity.TriggerCondition condition : entity.getConditions()) {
            conditionsTag.add(condition.toNbt());
        }
        data.put("Conditions", conditionsTag);

        // 同步 Actions
        ListTag actionsTag = new ListTag();
        for (EntityInteractionBlockEntity.TriggerAction action : entity.getActions()) {
            actionsTag.add(action.toNbt());
        }
        data.put("Actions", actionsTag);

        data.putInt("CooldownTicks", entity.getCooldownTicks());

        // 任务路标相关
        data.putBoolean("IsTaskMarker", entity.isTaskMarker());
        data.putInt("TaskMarkerColor", entity.getTaskMarkerColor());
        if (entity.getTaskHighlightCondition() != null) {
            data.putString("TaskHighlightCondition", entity.getTaskHighlightCondition().name());
        }
        data.putString("TaskHighlightTaskType", entity.getTaskHighlightTaskType() != null ? entity.getTaskHighlightTaskType() : "*");
        data.putString("TaskHighlightCustomTaskId", entity.getTaskHighlightCustomTaskId() != null ? entity.getTaskHighlightCustomTaskId() : "");
        data.putInt("TaskInstinctId", entity.getTaskInstinctId());

        ServerPlayNetworking.send(player, new EntityInteractionBlockPayload.SyncBlockEntity(pos, data));
    }

    /**
     * 服务端发送 OpenUI 包给玩家
     */
    public static void sendOpenUI(ServerPlayer player, BlockPos pos, EntityInteractionBlockEntity entity) {
        CompoundTag data = new CompoundTag();

        // 序列化条件
        ListTag conditionsTag = new ListTag();
        for (EntityInteractionBlockEntity.TriggerCondition condition : entity.getConditions()) {
            conditionsTag.add(condition.toNbt());
        }
        data.put("Conditions", conditionsTag);

        // 序列化触发内容
        ListTag actionsTag = new ListTag();
        for (EntityInteractionBlockEntity.TriggerAction action : entity.getActions()) {
            actionsTag.add(action.toNbt());
        }
        data.put("Actions", actionsTag);

        data.putInt("CooldownTicks", entity.getCooldownTicks());
        data.putBoolean("IsTeleportPoint", entity.isTeleportPoint());
        data.putInt("TeleportPointId", entity.getTeleportPointId());

        // 任务路标相关
        data.putBoolean("IsTaskMarker", entity.isTaskMarker());
        data.putInt("TaskMarkerColor", entity.getTaskMarkerColor());
        if (entity.getTaskHighlightCondition() != null) {
            data.putString("TaskHighlightCondition", entity.getTaskHighlightCondition().name());
        }
        data.putString("TaskHighlightTaskType", entity.getTaskHighlightTaskType() != null ? entity.getTaskHighlightTaskType() : "*");
        data.putString("TaskHighlightCustomTaskId", entity.getTaskHighlightCustomTaskId() != null ? entity.getTaskHighlightCustomTaskId() : "");
        data.putInt("TaskInstinctId", entity.getTaskInstinctId());

        ServerPlayNetworking.send(player, new EntityInteractionBlockPayload.OpenUI(pos, data));
    }
}
