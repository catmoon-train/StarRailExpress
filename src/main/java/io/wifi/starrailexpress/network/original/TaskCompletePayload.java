package io.wifi.starrailexpress.network.original;

import io.wifi.starrailexpress.client.gui.MoodRenderer;
import io.wifi.starrailexpress.SRE;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jetbrains.annotations.NotNull;

public record TaskCompletePayload() implements CustomPacketPayload {
    public static final Type<TaskCompletePayload> ID = new Type<>(SRE.id("taskcomplete"));
    public static final StreamCodec<FriendlyByteBuf, TaskCompletePayload> CODEC = StreamCodec.unit(new TaskCompletePayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    @Environment(EnvType.CLIENT)
    public static class Receiver implements ClientPlayNetworking.PlayPayloadHandler<TaskCompletePayload> {
        @Override
        public void receive(@NotNull TaskCompletePayload payload, ClientPlayNetworking.@NotNull Context context) {
            MoodRenderer.arrowProgress = 1f;
        }
    }
}