package io.wifi.starrailexpress.network;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.content.block_entity.EntityInteractionBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;

/**
 * 实体交互方块的网络通信 - Payload 定义
 * 只包含服务端逻辑，客户端相关代码应放在 EntityInteractionBlockClientNetwork 中
 */
public class EntityInteractionBlockPayload {

    // 打开UI的包
    public record OpenUI(BlockPos pos, CompoundTag data) implements CustomPacketPayload {
        public static final Type<OpenUI> TYPE = new Type<>(SRE.id("entity_interaction_open_ui"));
        public static final StreamCodec<FriendlyByteBuf, OpenUI> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, OpenUI::pos,
                ByteBufCodecs.COMPOUND_TAG, OpenUI::data,
                OpenUI::new);

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
                SyncBlockEntity::new);

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
                SaveConfig::new);

        @Override
        public Type<SaveConfig> type() {
            return TYPE;
        }
    }

    /**
     * 构建 SaveConfig 的 NBT 数据
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
