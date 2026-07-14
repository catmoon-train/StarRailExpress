package org.agmas.noellesroles.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.agmas.noellesroles.Noellesroles;

/** 服务端→客户端：打开对讲机频道界面（携带当前频道以预填，0 = 未接入）。 */
public record OpenRadioChannelS2CPacket(int currentChannel) implements CustomPacketPayload {
    public static final Type<OpenRadioChannelS2CPacket> ID = new Type<>(Noellesroles.id("open_radio_channel"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenRadioChannelS2CPacket> CODEC = StreamCodec.ofMember(
            (packet, buf) -> buf.writeVarInt(packet.currentChannel()),
            buf -> new OpenRadioChannelS2CPacket(buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
