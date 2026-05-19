package org.agmas.noellesroles.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

import java.util.UUID;

public class MorticianCreateBodyC2SPacket implements CustomPacketPayload {

    public static final Type<MorticianCreateBodyC2SPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "mortician_create_body"));

    private static final StreamCodec<RegistryFriendlyByteBuf, UUID> UUID_CODEC = StreamCodec.of(
            (buf, uuid) -> {
                buf.writeLong(uuid.getMostSignificantBits());
                buf.writeLong(uuid.getLeastSignificantBits());
            },
            buf -> new UUID(buf.readLong(), buf.readLong())
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, MorticianCreateBodyC2SPacket> CODEC = StreamCodec.composite(
            UUID_CODEC,
            MorticianCreateBodyC2SPacket::targetUuid,
            ByteBufCodecs.STRING_UTF8,
            MorticianCreateBodyC2SPacket::deathReason,
            MorticianCreateBodyC2SPacket::new
    );

    private final UUID targetUuid;
    private final String deathReason;

    public MorticianCreateBodyC2SPacket(UUID targetUuid, String deathReason) {
        this.targetUuid = targetUuid;
        this.deathReason = deathReason;
    }

    public UUID targetUuid() {
        return targetUuid;
    }

    public String deathReason() {
        return deathReason;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
