package io.wifi.starrailexpress.client.network;

import io.wifi.starrailexpress.content.block_entity.EntityInteractionBlockEntity;
import io.wifi.starrailexpress.network.EntityInteractionBlockPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * 实体交互方块的客户端网络处理
 * 只在客户端加载，负责注册 S2C 数据包的接收器和发送 C2S 数据包
 */
public class EntityInteractionBlockClientNetwork {

    /**
     * 在客户端初始化时注册所有相关数据包的接收器
     */
    public static void register() {
        // 注意：Payload 类型已在 SRE.registerPayloadTypes() 中注册，此处不再重复注册
        // 只注册接收器（ClientPlayNetworking）

        // 注册 SyncBlockEntity 接收器
        ClientPlayNetworking.registerGlobalReceiver(EntityInteractionBlockPayload.SyncBlockEntity.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                var clientLevel = Minecraft.getInstance().level;
                if (clientLevel != null) {
                    BlockEntity be = clientLevel.getBlockEntity(payload.pos());
                    if (be instanceof EntityInteractionBlockEntity entity) {
                        CompoundTag data = payload.data();
                        // 同步传送点数据
                        entity.setTeleportPoint(data.getBoolean("IsTeleportPoint"));
                        entity.setTeleportPointId(data.getInt("TeleportPointId"));

                        // 同步 Conditions
                        List<EntityInteractionBlockEntity.TriggerCondition> conditions = new ArrayList<>();
                        if (data.contains("Conditions", ListTag.TAG_LIST)) {
                            var list = data.getList("Conditions", ListTag.TAG_COMPOUND);
                            for (int i = 0; i < list.size(); i++) {
                                conditions.add(EntityInteractionBlockEntity.TriggerCondition.fromNbt(list.getCompound(i)));
                            }
                        }
                        entity.getConditions().clear();
                        entity.getConditions().addAll(conditions);

                        // 同步 Actions
                        List<EntityInteractionBlockEntity.TriggerAction> actions = new ArrayList<>();
                        if (data.contains("Actions", ListTag.TAG_LIST)) {
                            var list = data.getList("Actions", ListTag.TAG_COMPOUND);
                            for (int i = 0; i < list.size(); i++) {
                                actions.add(EntityInteractionBlockEntity.TriggerAction.fromNbt(list.getCompound(i)));
                            }
                        }
                        entity.getActions().clear();
                        entity.getActions().addAll(actions);

                        // 同步冷却
                        entity.setCooldownTicks(data.getInt("CooldownTicks"));

                        // 同步任务路标数据
                        entity.setTaskMarker(data.getBoolean("IsTaskMarker"));
                        entity.setTaskMarkerColor(data.contains("TaskMarkerColor") ? data.getInt("TaskMarkerColor") : 0xFFFFFF);
                        if (data.contains("TaskHighlightCondition")) {
                            entity.setTaskHighlightCondition(EntityInteractionBlockEntity.TaskHighlightCondition.valueOf(data.getString("TaskHighlightCondition")));
                        }
                        entity.setTaskHighlightTaskType(data.getString("TaskHighlightTaskType"));
                        entity.setTaskHighlightCustomTaskId(data.getString("TaskHighlightCustomTaskId"));
                        entity.setTaskInstinctId(data.contains("TaskInstinctId") ? data.getInt("TaskInstinctId") : 100);

                        // 手动调用 setChanged 通知客户端 BlockEntity 已更新
                        entity.setChanged();
                    }
                }
            });
        });

        // 注册 OpenUI 接收器
        ClientPlayNetworking.registerGlobalReceiver(EntityInteractionBlockPayload.OpenUI.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                if (context.client().level == null) return;
                var data = payload.data();
                var conditions = new ArrayList<EntityInteractionBlockEntity.TriggerCondition>();
                var actions = new ArrayList<EntityInteractionBlockEntity.TriggerAction>();

                if (data.contains("Conditions", ListTag.TAG_LIST)) {
                    var list = data.getList("Conditions", ListTag.TAG_COMPOUND);
                    for (int i = 0; i < list.size(); i++) {
                        conditions.add(EntityInteractionBlockEntity.TriggerCondition.fromNbt(list.getCompound(i)));
                    }
                }

                if (data.contains("Actions", ListTag.TAG_LIST)) {
                    var list = data.getList("Actions", ListTag.TAG_COMPOUND);
                    for (int i = 0; i < list.size(); i++) {
                        actions.add(EntityInteractionBlockEntity.TriggerAction.fromNbt(list.getCompound(i)));
                    }
                }

                int cooldown = data.getInt("CooldownTicks");
                boolean isTeleportPoint = data.getBoolean("IsTeleportPoint");
                int teleportPointId = data.getInt("TeleportPointId");

                // 任务路标相关数据
                boolean isTaskMarker = data.getBoolean("IsTaskMarker");
                int taskMarkerColor = data.contains("TaskMarkerColor") ? data.getInt("TaskMarkerColor") : 0xFFFFFF;
                EntityInteractionBlockEntity.TaskHighlightCondition taskHighlightCondition =
                        EntityInteractionBlockEntity.TaskHighlightCondition.NONE;
                if (data.contains("TaskHighlightCondition")) {
                    taskHighlightCondition = EntityInteractionBlockEntity.TaskHighlightCondition.valueOf(
                            data.getString("TaskHighlightCondition"));
                }
                String taskHighlightTaskType = data.getString("TaskHighlightTaskType");
                if (taskHighlightTaskType == null || taskHighlightTaskType.isEmpty()) taskHighlightTaskType = "*";
                String taskHighlightCustomTaskId = data.getString("TaskHighlightCustomTaskId");
                if (taskHighlightCustomTaskId == null) taskHighlightCustomTaskId = "";
                int taskInstinctId = data.contains("TaskInstinctId") ? data.getInt("TaskInstinctId") : 100;

                context.client().setScreen(new io.wifi.starrailexpress.client.gui.screen.EntityInteractionBlockScreen(
                        payload.pos(), conditions, actions, cooldown, isTeleportPoint, teleportPointId,
                        isTaskMarker, taskMarkerColor, taskHighlightCondition, taskHighlightTaskType, taskHighlightCustomTaskId, taskInstinctId));
            });
        });
    }

    /**
     * 发送保存配置的请求到服务端
     */
    public static void sendSaveConfig(BlockPos pos,
                                       List<EntityInteractionBlockEntity.TriggerCondition> conditions,
                                       List<EntityInteractionBlockEntity.TriggerAction> actions, int cooldown) {
        sendSaveConfig(pos, conditions, actions, cooldown, false, -1, false, 0xFFFFFF,
                EntityInteractionBlockEntity.TaskHighlightCondition.NONE, "*", "", 100);
    }

    public static void sendSaveConfig(BlockPos pos,
                                       List<EntityInteractionBlockEntity.TriggerCondition> conditions,
                                       List<EntityInteractionBlockEntity.TriggerAction> actions, int cooldown,
                                       boolean isTeleportPoint, int teleportPointId) {
        sendSaveConfig(pos, conditions, actions, cooldown, isTeleportPoint, teleportPointId, false, 0xFFFFFF,
                EntityInteractionBlockEntity.TaskHighlightCondition.NONE, "*", "", 100);
    }

    public static void sendSaveConfig(BlockPos pos,
                                       List<EntityInteractionBlockEntity.TriggerCondition> conditions,
                                       List<EntityInteractionBlockEntity.TriggerAction> actions, int cooldown,
                                       boolean isTeleportPoint, int teleportPointId,
                                       boolean isTaskMarker, int taskMarkerColor,
                                       EntityInteractionBlockEntity.TaskHighlightCondition taskHighlightCondition,
                                       String taskHighlightTaskType, String taskHighlightCustomTaskId,
                                       int taskInstinctId) {
        if (Minecraft.getInstance().getConnection() != null) {
            CompoundTag data = EntityInteractionBlockPayload.buildSaveConfigData(
                    pos, conditions, actions, cooldown, isTeleportPoint, teleportPointId,
                    isTaskMarker, taskMarkerColor, taskHighlightCondition, taskHighlightTaskType, taskHighlightCustomTaskId, taskInstinctId);
            ClientPlayNetworking.send(new EntityInteractionBlockPayload.SaveConfig(pos, data));
        }
    }
}
