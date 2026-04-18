package org.agmas.noellesroles.packet.Loot;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

/**
 * 客户端的抽卡信息发包
 * <p>
 *  - 向服务器请求抽卡信息比对
 * </p>
 */
public record LootPoolsInfoCheckC2SPacket() implements CustomPacketPayload {
    public static final ResourceLocation LOOT_POOLS_INFO_CHECK_CLIENT_PAYLOAD_ID =
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "loot_pools_info_check_client");
    public static final Type<LootPoolsInfoCheckC2SPacket> ID = new CustomPacketPayload.Type<>(LOOT_POOLS_INFO_CHECK_CLIENT_PAYLOAD_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, LootPoolsInfoCheckC2SPacket> CODEC;
    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public void write(FriendlyByteBuf buf) {
    }

    public static LootPoolsInfoCheckC2SPacket read(FriendlyByteBuf buf) {
        return new LootPoolsInfoCheckC2SPacket(
        );
    }
    static {
        CODEC = StreamCodec.ofMember(LootPoolsInfoCheckC2SPacket::write, LootPoolsInfoCheckC2SPacket::read);
    }
}
