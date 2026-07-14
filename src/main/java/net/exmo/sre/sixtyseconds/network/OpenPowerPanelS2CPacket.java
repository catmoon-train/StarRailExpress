package net.exmo.sre.sixtyseconds.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.agmas.noellesroles.Noellesroles;

/** 服务端→客户端：打开电力面板（{@code remainingTicks}=当前供电剩余 tick，客户端本地倒计时）。 */
public record OpenPowerPanelS2CPacket(long remainingTicks) implements CustomPacketPayload {
    public static final Type<OpenPowerPanelS2CPacket> ID = new Type<>(Noellesroles.id("open_power_panel"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenPowerPanelS2CPacket> CODEC = StreamCodec.ofMember(
            (packet, buf) -> buf.writeVarLong(packet.remainingTicks()),
            buf -> new OpenPowerPanelS2CPacket(buf.readVarLong()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
