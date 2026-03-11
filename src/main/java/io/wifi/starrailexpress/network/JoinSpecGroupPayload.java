package io.wifi.starrailexpress.network;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record JoinSpecGroupPayload(boolean isJoin) implements CustomPacketPayload {
    public static final Type<JoinSpecGroupPayload> ID = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SRE.MOD_ID, "join_spec_group"));
    public static final StreamCodec<FriendlyByteBuf, JoinSpecGroupPayload> CODEC = StreamCodec
            .ofMember(JoinSpecGroupPayload::encode, JoinSpecGroupPayload::decode);

    public static JoinSpecGroupPayload decode(FriendlyByteBuf buf) {
        return new JoinSpecGroupPayload(buf.readBoolean());
    }

    public static void encode(JoinSpecGroupPayload payload, FriendlyByteBuf buf) {
        buf.writeBoolean(payload.isJoin());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}