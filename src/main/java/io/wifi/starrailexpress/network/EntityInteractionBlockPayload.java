package io.wifi.starrailexpress.network;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.content.block_entity.EntityInteractionBlockEntity;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * 实体交互方块的网络通信
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

    public static void register() {
        PayloadTypeRegistry.playS2C().register(OpenUI.TYPE, OpenUI.CODEC);
        PayloadTypeRegistry.playC2S().register(SaveConfig.TYPE, SaveConfig.CODEC);

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

                    entity.setTeleportPoint(isTeleportPoint);
                    entity.setTeleportPointId(teleportPointId);

                    entity.updateFromServer(conditions, actions, cooldown);
                }
            });
        });
    }

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

        ServerPlayNetworking.send(player, new OpenUI(pos, data));
    }

    public static void sendSaveConfig(BlockPos pos, List<EntityInteractionBlockEntity.TriggerCondition> conditions,
                                      List<EntityInteractionBlockEntity.TriggerAction> actions, int cooldown) {
        sendSaveConfig(pos, conditions, actions, cooldown, false, -1);
    }

    public static void sendSaveConfig(BlockPos pos, List<EntityInteractionBlockEntity.TriggerCondition> conditions,
                                      List<EntityInteractionBlockEntity.TriggerAction> actions, int cooldown,
                                      boolean isTeleportPoint, int teleportPointId) {
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

        ClientPlayNetworking.send(new SaveConfig(pos, data));
    }
}
