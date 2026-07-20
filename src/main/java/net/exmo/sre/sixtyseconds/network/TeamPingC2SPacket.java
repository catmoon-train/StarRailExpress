package net.exmo.sre.sixtyseconds.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.agmas.noellesroles.Noellesroles;

/**
 * 客户端→服务端：玩家在指定世界坐标打标点，服务端转发给同队队友。
 * @param x 世界坐标 X
 * @param y 世界坐标 Y
 * @param z 世界坐标 Z
 */
public record TeamPingC2SPacket(int x, int y, int z) implements CustomPacketPayload {
    public static final Type<TeamPingC2SPacket> ID = new Type<>(Noellesroles.id("team_ping_c2s"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TeamPingC2SPacket> CODEC =
            StreamCodec.ofMember(TeamPingC2SPacket::encode, TeamPingC2SPacket::decode);

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(x);
        buf.writeVarInt(y);
        buf.writeVarInt(z);
    }

    public static TeamPingC2SPacket decode(RegistryFriendlyByteBuf buf) {
        return new TeamPingC2SPacket(buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
