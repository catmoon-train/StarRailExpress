package org.agmas.noellesroles.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.agmas.noellesroles.Noellesroles;

import java.util.UUID;

/**
 * Client → Server: a player right-clicks a placed cake to eat it.
 *
 * @param cakeId the UUID of the placed cake block
 */
public record CakeMakerEatC2SPacket(UUID cakeId) implements CustomPacketPayload {

    public static final Type<CakeMakerEatC2SPacket> ID =
            new Type<>(Noellesroles.id("cake_maker_eat"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CakeMakerEatC2SPacket> CODEC =
            StreamCodec.ofMember(
                    (packet, buf) -> buf.writeUUID(packet.cakeId),
                    buf -> new CakeMakerEatC2SPacket(buf.readUUID()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
