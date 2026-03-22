package org.agmas.noellesroles.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

import java.util.UUID;

public record MorphC2SPacket(UUID player) implements CustomPacketPayload {
    public static final ResourceLocation MORPH_PAYLOAD_ID = ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "morph");
    public static final CustomPacketPayload.Type<MorphC2SPacket> ID = new CustomPacketPayload.Type<>(MORPH_PAYLOAD_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, MorphC2SPacket> CODEC;

    public MorphC2SPacket(UUID player) {
        this.player = player;
    }

    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(this.player);
    }

    public static MorphC2SPacket read(FriendlyByteBuf buf) {
        return new MorphC2SPacket(buf.readUUID());
    }


    public UUID player() {
        return this.player;
    }


    static {
        CODEC = StreamCodec.ofMember(MorphC2SPacket::write, MorphC2SPacket::read);
    }
}