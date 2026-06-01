package org.agmas.noellesroles.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

public class MorticianToggleModeC2SPacket implements CustomPacketPayload {

    public static final Type<MorticianToggleModeC2SPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "mortician_toggle_mode"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MorticianToggleModeC2SPacket> CODEC = StreamCodec.of(
            (buf, packet) -> {},
            buf -> new MorticianToggleModeC2SPacket()
    );

    public MorticianToggleModeC2SPacket() {
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
