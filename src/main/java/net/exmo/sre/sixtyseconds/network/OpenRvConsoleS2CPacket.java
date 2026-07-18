package net.exmo.sre.sixtyseconds.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.agmas.noellesroles.Noellesroles;

/** 服务端→客户端：打开房车控制台，携带房车实体网络 id（客户端据此读取已同步的实时状态）。 */
public record OpenRvConsoleS2CPacket(int entityId) implements CustomPacketPayload {

    public static final Type<OpenRvConsoleS2CPacket> ID = new Type<>(Noellesroles.id("open_rv_console"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenRvConsoleS2CPacket> CODEC =
            StreamCodec.ofMember(OpenRvConsoleS2CPacket::encode, OpenRvConsoleS2CPacket::decode);

    private void encode(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
    }

    private static OpenRvConsoleS2CPacket decode(RegistryFriendlyByteBuf buf) {
        return new OpenRvConsoleS2CPacket(buf.readVarInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
