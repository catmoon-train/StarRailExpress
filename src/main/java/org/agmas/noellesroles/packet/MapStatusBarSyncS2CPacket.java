package org.agmas.noellesroles.packet;

import io.wifi.starrailexpress.game.data.MapStatusBarType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.agmas.noellesroles.Noellesroles;

public record MapStatusBarSyncS2CPacket(MapStatusBarType barType, int value, int maxValue)
        implements CustomPacketPayload {
    public static final Type<MapStatusBarSyncS2CPacket> ID = new Type<>(Noellesroles.id("map_status_bar_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MapStatusBarSyncS2CPacket> CODEC = StreamCodec
            .ofMember(MapStatusBarSyncS2CPacket::encode, MapStatusBarSyncS2CPacket::decode);

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeUtf((barType == null ? MapStatusBarType.NONE : barType).name());
        buf.writeVarInt(value);
        buf.writeVarInt(maxValue);
    }

    public static MapStatusBarSyncS2CPacket decode(RegistryFriendlyByteBuf buf) {
        return new MapStatusBarSyncS2CPacket(MapStatusBarType.byName(buf.readUtf()), buf.readVarInt(), buf.readVarInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
