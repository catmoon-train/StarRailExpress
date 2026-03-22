package org.agmas.noellesroles.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

import java.util.UUID;

public record SwapperC2SPacket(UUID player, UUID player2) implements CustomPacketPayload {
    public static final ResourceLocation MORPH_PAYLOAD_ID = ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "swapper");
    public static final Type<SwapperC2SPacket> ID = new Type<>(MORPH_PAYLOAD_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, SwapperC2SPacket> CODEC;

    public SwapperC2SPacket(UUID player, UUID player2) {
        this.player = player;
        this.player2 = player2;
    }

    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(this.player);
        buf.writeUUID(this.player2);
    }

    public static SwapperC2SPacket read(FriendlyByteBuf buf) {
        return new SwapperC2SPacket(buf.readUUID(), buf.readUUID());
    }


    public UUID player() {
        return this.player;
    }
    public UUID player2() {
        return this.player2;
    }


    static {
        CODEC = StreamCodec.ofMember(SwapperC2SPacket::write, SwapperC2SPacket::read);
    }
}