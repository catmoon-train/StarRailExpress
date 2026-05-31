package org.agmas.noellesroles.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

public record WarlockMarkC2SPacket() implements CustomPacketPayload {
    public static final ResourceLocation ID_RES = ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "warlock_mark");
    public static final Type<WarlockMarkC2SPacket> ID = new Type<>(ID_RES);
    public static final StreamCodec<RegistryFriendlyByteBuf, WarlockMarkC2SPacket> CODEC = StreamCodec.unit(new WarlockMarkC2SPacket());
    @Override public Type<? extends CustomPacketPayload> type() { return ID; }
}
