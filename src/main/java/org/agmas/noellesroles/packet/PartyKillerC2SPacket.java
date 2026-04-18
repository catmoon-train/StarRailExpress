package org.agmas.noellesroles.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

import java.util.UUID;

public record PartyKillerC2SPacket(UUID targetPlayer) implements CustomPacketPayload {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "party_killer");
    public static final CustomPacketPayload.Type<PartyKillerC2SPacket> TYPE = new CustomPacketPayload.Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, PartyKillerC2SPacket> CODEC;

    public PartyKillerC2SPacket(UUID targetPlayer) {
        this.targetPlayer = targetPlayer;
    }

    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(this.targetPlayer);
    }

    public static PartyKillerC2SPacket read(FriendlyByteBuf buf) {
        return new PartyKillerC2SPacket(buf.readUUID());
    }

    public UUID targetPlayer() {
        return this.targetPlayer;
    }

    static {
        CODEC = StreamCodec.ofMember(PartyKillerC2SPacket::write, PartyKillerC2SPacket::read);
    }
}
