package org.agmas.noellesroles.packet;

import org.agmas.noellesroles.Noellesroles;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OpenIntroPayload() implements CustomPacketPayload {
    public static final ResourceLocation ABILITY_PAYLOAD_ID = ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID,
            "open_intro");
    public static final Type<OpenIntroPayload> ID = new Type<>(ABILITY_PAYLOAD_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenIntroPayload> CODEC;

    public OpenIntroPayload() {
    }

    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public void write(FriendlyByteBuf buf) {

    }

    public static OpenIntroPayload read(FriendlyByteBuf buf) {
        return new OpenIntroPayload();
    }

    static {
        CODEC = StreamCodec.ofMember(OpenIntroPayload::write, OpenIntroPayload::read);
    }
}