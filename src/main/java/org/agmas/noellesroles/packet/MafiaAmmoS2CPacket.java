package org.agmas.noellesroles.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

public record MafiaAmmoS2CPacket(int loaded, int max) implements CustomPacketPayload {
    public static final ResourceLocation ID_LOC = ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "mafia_ammo");
    public static final Type<MafiaAmmoS2CPacket> ID = new Type<>(ID_LOC);
    public static final StreamCodec<RegistryFriendlyByteBuf, MafiaAmmoS2CPacket> CODEC;
    @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    public void write(FriendlyByteBuf buf) { buf.writeInt(loaded); buf.writeInt(max); }
    public static MafiaAmmoS2CPacket read(FriendlyByteBuf buf) { return new MafiaAmmoS2CPacket(buf.readInt(), buf.readInt()); }
    static { CODEC = StreamCodec.ofMember(MafiaAmmoS2CPacket::write, MafiaAmmoS2CPacket::read); }
}
