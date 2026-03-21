package org.agmas.noellesroles.packet.Loot;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

public record LootMultiRequestC2SPacket(int poolID, int count) implements CustomPacketPayload {
    public static final ResourceLocation LOOT_MULTI_REQUEST_PAYLOAD_ID =
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "loot_multi_request");
    public static final Type<LootMultiRequestC2SPacket> ID = new CustomPacketPayload.Type<>(LOOT_MULTI_REQUEST_PAYLOAD_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, LootMultiRequestC2SPacket> CODEC;
    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeInt(this.poolID);
        buf.writeInt(this.count);
    }

    public static LootMultiRequestC2SPacket read(FriendlyByteBuf buf) {
        return new LootMultiRequestC2SPacket(buf.readInt(), buf.readInt());
    }
    static {
        CODEC = StreamCodec.ofMember(LootMultiRequestC2SPacket::write, LootMultiRequestC2SPacket::read);
    }
}
