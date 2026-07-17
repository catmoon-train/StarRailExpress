package net.exmo.sre.sixtyseconds.network;

import net.exmo.sre.sixtyseconds.content.block.RandomSupplyBoxBlock;
import net.exmo.sre.sixtyseconds.content.block_entity.RandomSupplyBoxBlockEntity;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.Noellesroles;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 客户端→服务端：保存随机物资箱的已启用类别配置。
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
        if (player.serverLevel().getBlockEntity(payload.pos())
                instanceof RandomSupplyBoxBlockEntity box) {
            box.setEnabledCategories(payload.enabledCategories());
            player.displayClientMessage(
                    Component.literal("[RandomBox] enabled categories saved: "
                            + payload.enabledCategories().size() + " categories")
                            .withStyle(ChatFormatting.GREEN),
                    true);
        }
    }
}
