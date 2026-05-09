package io.wifi.starrailexpress.network;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.content.block_entity.EntityInteractionBlockEntity;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * 实体交互方块的网络通信 - Payload 定义和注册
 * 只包含服务端逻辑，客户端相关代码应放在 EntityInteractionBlockClientNetwork 中
 */
public class EntityInteractionBlockPayload {

    // 打开UI的包
    public record OpenUI(BlockPos pos, CompoundTag data) implements CustomPacketPayload {
        public static final Type<OpenUI> TYPE = new Type<>(SRE.id("entity_interaction_open_ui"));
        public static final StreamCodec<FriendlyByteBuf, OpenUI> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, OpenUI::pos,
                ByteBufCodecs.COMPOUND_TAG, OpenUI::data,
                OpenUI::new
        );

        @Override
        public Type<OpenUI> type() {
            return TYPE;
        }
    }

    // 同步BlockEntity数据的包（服务端->客户端，不打开UI）
    public record SyncBlockEntity(BlockPos pos, CompoundTag data) implements CustomPacketPayload {
        public static final Type<SyncBlockEntity> TYPE = new Type<>(SRE.id("entity_interaction_sync"));
        public static final StreamCodec<FriendlyByteBuf, SyncBlockEntity> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, SyncBlockEntity::pos,
                ByteBufCodecs.COMPOUND_TAG, SyncBlockEntity::data,
                SyncBlockEntity::new
        );

        @Override
        public Type<SyncBlockEntity> type() {
            return TYPE;
        }
    }

    // 保存配置的包（客户端->服务端）
    public record SaveConfig(BlockPos pos, CompoundTag data) implements CustomPacketPayload {
        public static final Type<SaveConfig> TYPE = new Type<>(SRE.id("entity_interaction_save"));
        public static final StreamCodec<FriendlyByteBuf, SaveConfig> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, SaveConfig::pos,
                ByteBufCodecs.COMPOUND_TAG, SaveConfig::data,
                SaveConfig::new
        );

        @Override
        public Type<SaveConfig> type() {
            return TYPE;
        }
    }

    /**
     * 服务端初始化：注册 Payload 类型和处理器
     */
    public static void register() {
        // S2C: 服务端 -> 客户端
        PayloadTypeRegistry.playS2C().register(OpenUI.TYPE, OpenUI.CODEC);
        PayloadTypeRegistry.playS2C().register(SyncBlockEntity.TYPE, SyncBlockEntity.CODEC);
        // C2S: 客户端 -> 服务端
        PayloadTypeRegistry.playC2S().register(SaveConfig.TYPE, SaveConfig.CODEC);

        // 处理客户端发来的 SaveConfig 包
        ServerPlayNetworking.registerGlobalReceiver(SaveConfig.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                BlockEntity be = player.level().getBlockEntity(payload.pos());
                if (be instanceof EntityInteractionBlockEntity entity) {
                    // 解析数据
                    CompoundTag data = payload.data();
                    List<EntityInteractionBlockEntity.TriggerCondition> conditions = new ArrayList<>();
                    List<EntityInteractionBlockEntity.TriggerAction> actions = new ArrayList<>();

                    if (data.contains("Conditions", ListTag.TAG_LIST)) {
                        ListTag list = data.getList("Conditions", ListTag.TAG_LIST);
                        for (int i = 0; i < list.size(); i++) {
                            conditions.add(EntityInteractionBlockEntity.TriggerCondition.fromNbt(list.getCompound(i)));
                        }
                    }

                    if (data.contains("Actions", ListTag.TAG_LIST)) {
                        ListTag list = data.getList("Actions", ListTag.TAG_LIST);
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
                    // 使用自定义数据包确保客户端收到最新数据
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

        ServerPlayNetworking.send(player, new OpenUI(pos, data));
    }

    /**
     * 服务端发送 SyncBlockEntity 包给玩家（不打开UI，只同步数据）
     */
    public static void sendSyncBlockEntity(ServerPlayer player, BlockPos pos, EntityInteractionBlockEntity entity) {
        CompoundTag data = new CompoundTag();

        data.putInt("TeleportPointId", entity.getTeleportPointId());
        data.putBoolean("IsTeleportPoint", entity.isTeleportPoint());

        // 任务路标相关
        data.putBoolean("IsTaskMarker", entity.isTaskMarker());
        data.putInt("TaskMarkerColor", entity.getTaskMarkerColor());
        if (entity.getTaskHighlightCondition() != null) {
            data.putString("TaskHighlightCondition", entity.getTaskHighlightCondition().name());
        }
        data.putString("TaskHighlightTaskType", entity.getTaskHighlightTaskType() != null ? entity.getTaskHighlightTaskType() : "*");
        data.putString("TaskHighlightCustomTaskId", entity.getTaskHighlightCustomTaskId() != null ? entity.getTaskHighlightCustomTaskId() : "");
        data.putInt("TaskInstinctId", entity.getTaskInstinctId());

        ServerPlayNetworking.send(player, new SyncBlockEntity(pos, data));
    }

    /**
     * 构建 SaveConfig 的 NBT 数据（客户端和服务端共用）
     */
    public static CompoundTag buildSaveConfigData(BlockPos pos,
                                                  List<EntityInteractionBlockEntity.TriggerCondition> conditions,
                                                  List<EntityInteractionBlockEntity.TriggerAction> actions, int cooldown,
                                                  boolean isTeleportPoint, int teleportPointId,
                                                  boolean isTaskMarker, int taskMarkerColor,
                                                  EntityInteractionBlockEntity.TaskHighlightCondition taskHighlightCondition,
                                                  String taskHighlightTaskType, String taskHighlightCustomTaskId,
                                                  int taskInstinctId) {
        CompoundTag data = new CompoundTag();

        ListTag conditionsTag = new ListTag();
        for (EntityInteractionBlockEntity.TriggerCondition condition : conditions) {
            conditionsTag.add(condition.toNbt());
        }
        data.put("Conditions", conditionsTag);

        ListTag actionsTag = new ListTag();
        for (EntityInteractionBlockEntity.TriggerAction action : actions) {
            actionsTag.add(action.toNbt());
        }
        data.put("Actions", actionsTag);

        data.putInt("CooldownTicks", cooldown);
        data.putBoolean("IsTeleportPoint", isTeleportPoint);
        data.putInt("TeleportPointId", teleportPointId);

        // 任务路标相关
        data.putBoolean("IsTaskMarker", isTaskMarker);
        data.putInt("TaskMarkerColor", taskMarkerColor);
        if (taskHighlightCondition != null) {
            data.putString("TaskHighlightCondition", taskHighlightCondition.name());
        }
        data.putString("TaskHighlightTaskType", taskHighlightTaskType != null ? taskHighlightTaskType : "*");
        data.putString("TaskHighlightCustomTaskId", taskHighlightCustomTaskId != null ? taskHighlightCustomTaskId : "");
        data.putInt("TaskInstinctId", taskInstinctId);

        return data;
    }
}
