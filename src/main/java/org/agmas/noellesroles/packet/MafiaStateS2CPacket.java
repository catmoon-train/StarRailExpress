package org.agmas.noellesroles.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record MafiaStateS2CPacket(List<UUID> memberIds, int color) implements CustomPacketPayload {
    public static final ResourceLocation ID_LOC = ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "mafia_state");
    public static final Type<MafiaStateS2CPacket> ID = new Type<>(ID_LOC);
    public static final StreamCodec<RegistryFriendlyByteBuf, MafiaStateS2CPacket> CODEC;
    @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    public void write(FriendlyByteBuf buf) {
        buf.writeInt(memberIds.size());
        for (UUID id : memberIds) buf.writeUUID(id);
        buf.writeInt(color);
    }
    public static MafiaStateS2CPacket read(FriendlyByteBuf buf) {
        int size = buf.readInt(); List<UUID> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) list.add(buf.readUUID());
        return new MafiaStateS2CPacket(list, buf.readInt());
    }
    static { CODEC = StreamCodec.ofMember(MafiaStateS2CPacket::write, MafiaStateS2CPacket::read); }
}
