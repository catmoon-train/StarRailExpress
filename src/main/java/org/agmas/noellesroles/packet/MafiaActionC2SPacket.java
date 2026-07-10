package org.agmas.noellesroles.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;
import java.util.UUID;

public record MafiaActionC2SPacket(int action, UUID target, String rolePath) implements CustomPacketPayload {
    public static final ResourceLocation ID_LOC = ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "mafia_action");
    public static final Type<MafiaActionC2SPacket> ID = new Type<>(ID_LOC);
    public static final StreamCodec<RegistryFriendlyByteBuf, MafiaActionC2SPacket> CODEC;
    public static final int RECRUIT_ROLE = 0, CLEAN_BODY = 1;
    @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    public void write(FriendlyByteBuf buf) { buf.writeInt(action); buf.writeUUID(target); buf.writeUtf(rolePath == null ? "" : rolePath); }
    public static MafiaActionC2SPacket read(FriendlyByteBuf buf) { return new MafiaActionC2SPacket(buf.readInt(), buf.readUUID(), buf.readUtf()); }
    static { CODEC = StreamCodec.ofMember(MafiaActionC2SPacket::write, MafiaActionC2SPacket::read); }
}
