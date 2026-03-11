package org.agmas.noellesroles.packet.Loot;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

import java.util.List;

public record LootPoolsInfoRequestC2SPacket(List<Integer> poolIds) implements CustomPacketPayload {
    public static final ResourceLocation LOOT_POOLS_INFO_REQUEST_PAYLOAD_ID =
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "loot_pools_info_request");
    public static final Type<LootPoolsInfoRequestC2SPacket> ID = new CustomPacketPayload.Type<>(LOOT_POOLS_INFO_REQUEST_PAYLOAD_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, LootPoolsInfoRequestC2SPacket> CODEC;
    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeCollection(poolIds, FriendlyByteBuf::writeInt);
    }

    public static LootPoolsInfoRequestC2SPacket read(FriendlyByteBuf buf) {
        return new LootPoolsInfoRequestC2SPacket(
                buf.readList(FriendlyByteBuf::readInt)
        );
    }
    static {
        CODEC = StreamCodec.ofMember(LootPoolsInfoRequestC2SPacket::write, LootPoolsInfoRequestC2SPacket::read);
    }
}
