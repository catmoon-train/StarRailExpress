package net.exmo.sre.sixtyseconds.network;

import net.exmo.sre.sixtyseconds.content.block.RandomSupplyBoxBlock;
import net.exmo.sre.sixtyseconds.content.block_entity.RandomSupplyBoxBlockEntity;
import net.exmo.sre.sixtyseconds.loot.SixtySecondsRandomBoxConfigStore;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import org.agmas.noellesroles.Noellesroles;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 客户端→服务端：保存<b>全局</b>随机物资箱的已启用类别配置。
 * 不再写入单个方块实体的 NBT，而是写入 {@link SixtySecondsRandomBoxConfigStore}，
 * 同等级的全部随机箱立刻生效。
 */
public record RandomSupplyBoxConfigSaveC2SPacket(
        BlockPos pos,
        Set<String> enabledCategories) implements CustomPacketPayload {

    public static final Type<RandomSupplyBoxConfigSaveC2SPacket> ID =
            new Type<>(Noellesroles.id("random_supply_box_config_save"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RandomSupplyBoxConfigSaveC2SPacket> CODEC =
            StreamCodec.ofMember(RandomSupplyBoxConfigSaveC2SPacket::encode,
                    RandomSupplyBoxConfigSaveC2SPacket::decode);

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeVarInt(enabledCategories.size());
        for (String cat : enabledCategories) {
            buf.writeUtf(cat);
        }
    }

    public static RandomSupplyBoxConfigSaveC2SPacket decode(RegistryFriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        int size = buf.readVarInt();
        Set<String> enabled = new LinkedHashSet<>();
        for (int i = 0; i < size; i++) {
            enabled.add(buf.readUtf());
        }
        return new RandomSupplyBoxConfigSaveC2SPacket(pos, enabled);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static void handle(RandomSupplyBoxConfigSaveC2SPacket payload,
            ServerPlayNetworking.Context context) {
        ServerPlayer player = context.player();
        if (!player.isCreative()) {
            return;
        }
        ServerLevel level = context.player().serverLevel();
        BlockState state = level.getBlockState(payload.pos());
        // 从方块状态获取等级
        if (!(state.getBlock() instanceof RandomSupplyBoxBlock rb)) {
            return;
        }
        // 验证该位置确实有随机箱方块实体（防止破坏后滥用）
        if (!(level.getBlockEntity(payload.pos()) instanceof RandomSupplyBoxBlockEntity)) {
            return;
        }

        String tier = rb.tier();
        SixtySecondsRandomBoxConfigStore.Data config = SixtySecondsRandomBoxConfigStore.get(level);
        config.setEnabled(tier, payload.enabledCategories());
        SixtySecondsRandomBoxConfigStore.save(level, config);

        player.displayClientMessage(
                Component.literal("[RandomBox] global " + tier + "-tier config saved: "
                        + payload.enabledCategories().size() + " categories")
                        .withStyle(ChatFormatting.GREEN),
                true);
    }
}
