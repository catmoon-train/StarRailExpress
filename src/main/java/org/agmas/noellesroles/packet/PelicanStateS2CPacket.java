package org.agmas.noellesroles.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;
import java.util.UUID;

public record PelicanStateS2CPacket(boolean stashed, UUID pelicanId, int pelicanEntityId) implements CustomPacketPayload {
    public static final ResourceLocation PELICAN_STATE_ID = ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "pelican_state");
    public static final Type<PelicanStateS2CPacket> ID = new Type<>(PELICAN_STATE_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, PelicanStateS2CPacket> CODEC;

    public static PelicanStateS2CPacket clear() {
        return new PelicanStateS2CPacket(false, null, -1);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBoolean(stashed);
        buf.writeBoolean(pelicanId != null);
        if (pelicanId != null) buf.writeUUID(pelicanId);
        buf.writeInt(pelicanEntityId);
    }

    public static PelicanStateS2CPacket read(FriendlyByteBuf buf) {
        boolean stashed = buf.readBoolean();
        UUID pelicanId = buf.readBoolean() ? buf.readUUID() : null;
        int entityId = buf.readInt();
        return new PelicanStateS2CPacket(stashed, pelicanId, entityId);
    }

    static {
        CODEC = StreamCodec.ofMember(PelicanStateS2CPacket::write, PelicanStateS2CPacket::read);
    }
}
