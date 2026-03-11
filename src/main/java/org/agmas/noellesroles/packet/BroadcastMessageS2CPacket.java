package org.agmas.noellesroles.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

public record BroadcastMessageS2CPacket(Component content, boolean overlay) implements CustomPacketPayload {
    public static final ResourceLocation BROADCAST_MESSAGE_PAYLOAD_ID = ResourceLocation
            .fromNamespaceAndPath(Noellesroles.MOD_ID, "broadcast_message");
    public static final Type<BroadcastMessageS2CPacket> ID = new Type<>(BROADCAST_MESSAGE_PAYLOAD_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, BroadcastMessageS2CPacket> CODEC = StreamCodec.composite(
            ComponentSerialization.TRUSTED_STREAM_CODEC, BroadcastMessageS2CPacket::content, ByteBufCodecs.BOOL,
            BroadcastMessageS2CPacket::overlay, BroadcastMessageS2CPacket::new);

    public boolean isSkippable() {
        return true;
    }

    public Component content() {
        return this.content;
    }

    public boolean overlay() {
        return true;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public BroadcastMessageS2CPacket(Component content) {
        this(content, true);
    }

    public BroadcastMessageS2CPacket(Component content, boolean overlay) {
        this.content = content;
        this.overlay = overlay;
    }
}