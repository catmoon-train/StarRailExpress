package org.agmas.noellesroles.packet;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.agmas.noellesroles.Noellesroles;

public record OpenRepairStationScreenS2CPacket(BlockPos blockPos) implements CustomPacketPayload {
    public static final Type<OpenRepairStationScreenS2CPacket> ID = new Type<>(Noellesroles.id("open_repair_station"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenRepairStationScreenS2CPacket> CODEC = StreamCodec
            .ofMember(OpenRepairStationScreenS2CPacket::encode, OpenRepairStationScreenS2CPacket::decode);

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(blockPos);
    }

    public static OpenRepairStationScreenS2CPacket decode(RegistryFriendlyByteBuf buf) {
        return new OpenRepairStationScreenS2CPacket(buf.readBlockPos());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
