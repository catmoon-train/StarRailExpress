package io.wifi.starrailexpress.network.packet;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record EnableTaskHighlightPacket(boolean enable) implements CustomPacketPayload {
    public static final Type<EnableTaskHighlightPacket> ID = new Type<>(
            ResourceLocation.tryBuild(SRE.MOD_ID, "enable_task_highlight"));
    public static final StreamCodec<RegistryFriendlyByteBuf, EnableTaskHighlightPacket> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, EnableTaskHighlightPacket::enable,
            EnableTaskHighlightPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
