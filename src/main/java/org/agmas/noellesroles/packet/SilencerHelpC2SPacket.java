package org.agmas.noellesroles.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

import java.util.UUID;

public record SilencerHelpC2SPacket(UUID targetPlayer) implements CustomPacketPayload {
    public static final ResourceLocation SILENCER_HELP_PAYLOAD_ID = ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "silencer_help");
    public static final CustomPacketPayload.Type<SilencerHelpC2SPacket> ID = new CustomPacketPayload.Type<>(SILENCER_HELP_PAYLOAD_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, SilencerHelpC2SPacket> CODEC;

    public SilencerHelpC2SPacket(UUID targetPlayer) {
        this.targetPlayer = targetPlayer;
    }

    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeUUID(this.targetPlayer);
    }

    public static SilencerHelpC2SPacket read(RegistryFriendlyByteBuf buf) {
        return new SilencerHelpC2SPacket(buf.readUUID());
    }

    static {
        CODEC = StreamCodec.ofMember(SilencerHelpC2SPacket::write, SilencerHelpC2SPacket::read);
    }
}
