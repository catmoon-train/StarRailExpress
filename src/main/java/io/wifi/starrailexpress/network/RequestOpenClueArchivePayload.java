package io.wifi.starrailexpress.network;

import io.wifi.starrailexpress.SRE;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.agmas.noellesroles.init.ModEffects;
import org.jetbrains.annotations.NotNull;

public record RequestOpenClueArchivePayload() implements CustomPacketPayload {
    public static final Type<RequestOpenClueArchivePayload> ID = new Type<>(SRE.id("request_open_clue_archive"));
    public static final StreamCodec<FriendlyByteBuf, RequestOpenClueArchivePayload> CODEC =
            CustomPacketPayload.codec(RequestOpenClueArchivePayload::encode, RequestOpenClueArchivePayload::decode);
    public static final RequestOpenClueArchivePayload INSTANCE = new RequestOpenClueArchivePayload();

    public static void encode(RequestOpenClueArchivePayload payload, FriendlyByteBuf buf) {
    }

    public static RequestOpenClueArchivePayload decode(FriendlyByteBuf buf) {
        return INSTANCE;
    }

    @Override
    public Type<RequestOpenClueArchivePayload> type() {
        return ID;
    }

    public static class Receiver implements ServerPlayNetworking.PlayPayloadHandler<RequestOpenClueArchivePayload> {
        @Override
        public void receive(@NotNull RequestOpenClueArchivePayload payload,
                ServerPlayNetworking.@NotNull Context context) {
            context.server().execute(() -> {
                if (!context.player().hasEffect(ModEffects.GHOST_STATE)) {
                    context.player().displayClientMessage(Component.translatable(
                            "message.sre.clue_archive.ghost_only").withStyle(ChatFormatting.GRAY), true);
                    return;
                }
                ServerPlayNetworking.send(context.player(), OpenClueArchivePayload.INSTANCE);
            });
        }
    }
}
