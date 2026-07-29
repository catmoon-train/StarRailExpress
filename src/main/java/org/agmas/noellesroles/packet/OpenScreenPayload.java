package org.agmas.noellesroles.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

public record OpenScreenPayload(ResourceLocation id) implements CustomPacketPayload {
    public static final ResourceLocation PAYLOAD_ID = ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID,
            "open_screen");
    public static final Type<OpenScreenPayload> ID = new Type<>(PAYLOAD_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenScreenPayload> CODEC;

    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeResourceLocation(this.id());
    }

    public static OpenScreenPayload read(FriendlyByteBuf buf) {
        return new OpenScreenPayload(buf.readResourceLocation());
    }

    static {
        CODEC = StreamCodec.ofMember(OpenScreenPayload::write, OpenScreenPayload::read);
    }
}