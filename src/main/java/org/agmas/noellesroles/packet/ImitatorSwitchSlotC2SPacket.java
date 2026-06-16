package org.agmas.noellesroles.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

public class ImitatorSwitchSlotC2SPacket implements CustomPacketPayload {

    public static final Type<ImitatorSwitchSlotC2SPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "imitator_switch_slot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ImitatorSwitchSlotC2SPacket> CODEC = StreamCodec.of(
            (buf, packet) -> {},
            buf -> new ImitatorSwitchSlotC2SPacket()
    );

    public ImitatorSwitchSlotC2SPacket() {
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
