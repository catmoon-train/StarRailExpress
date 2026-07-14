package net.exmo.sre.sixtyseconds.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.agmas.noellesroles.Noellesroles;

/** 服务端→客户端：打开合成站界面（站类型序号 + 站方块坐标 + 本队已解锁科技 + 是否供电）。 */
public record OpenStationS2CPacket(int station, BlockPos pos, String[] unlockedTech, boolean powered)
        implements CustomPacketPayload {
    public static final Type<OpenStationS2CPacket> ID = new Type<>(Noellesroles.id("open_station"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenStationS2CPacket> CODEC =
            StreamCodec.ofMember(OpenStationS2CPacket::encode, OpenStationS2CPacket::decode);

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(station);
        buf.writeBlockPos(pos);
        buf.writeVarInt(unlockedTech.length);
        for (String id : unlockedTech) {
            buf.writeUtf(id);
        }
        buf.writeBoolean(powered);
    }

    public static OpenStationS2CPacket decode(RegistryFriendlyByteBuf buf) {
        int station = buf.readVarInt();
        BlockPos pos = buf.readBlockPos();
        int n = buf.readVarInt();
        String[] tech = new String[n];
        for (int i = 0; i < n; i++) {
            tech[i] = buf.readUtf();
        }
        return new OpenStationS2CPacket(station, pos, tech, buf.readBoolean());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
