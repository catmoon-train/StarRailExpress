package io.wifi.starrailexpress.network.original;

import io.wifi.starrailexpress.cca.PlayerNoteComponent;
import io.wifi.starrailexpress.SRE;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jetbrains.annotations.NotNull;

public record NoteEditPayload(String line1, String line2, String line3, String line4) implements CustomPacketPayload {
    public static final Type<NoteEditPayload> ID = new Type<>(SRE.id("note"));
    public static final StreamCodec<FriendlyByteBuf, NoteEditPayload> CODEC = StreamCodec.composite(ByteBufCodecs.STRING_UTF8, NoteEditPayload::line1, ByteBufCodecs.STRING_UTF8, NoteEditPayload::line2, ByteBufCodecs.STRING_UTF8, NoteEditPayload::line3, ByteBufCodecs.STRING_UTF8, NoteEditPayload::line4, NoteEditPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static class Receiver implements ServerPlayNetworking.PlayPayloadHandler<NoteEditPayload> {
        @Override
        public void receive(@NotNull NoteEditPayload payload, ServerPlayNetworking.@NotNull Context context) {
            PlayerNoteComponent.KEY.get(context.player()).setNote(payload.line1(), payload.line2(), payload.line3(), payload.line4());
        }
    }
}