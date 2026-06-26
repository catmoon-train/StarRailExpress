package org.agmas.noellesroles.packet;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.agmas.noellesroles.Noellesroles;

import java.util.UUID;

/**
 * Server → Client: syncs a client-side smoker or cake block for the Cake Maker.
 *
 * @param id     unique block instance ID
 * @param pos    block position
 * @param cake   {@code true} = cake, {@code false} = smoker
 * @param bites  cake bite count (0–6), ignored for smoker
 * @param ticks  remaining lifetime in ticks, 0 to remove immediately
 * @param remove {@code true} to remove the block from the client world
 */
public record CakeMakerBlockS2CPacket(
        UUID id,
        BlockPos pos,
        boolean cake,
        int bites,
        int ticks,
        boolean remove
) implements CustomPacketPayload {

    public static final Type<CakeMakerBlockS2CPacket> ID =
            new Type<>(Noellesroles.id("cake_maker_block"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CakeMakerBlockS2CPacket> CODEC =
            StreamCodec.ofMember(CakeMakerBlockS2CPacket::write, CakeMakerBlockS2CPacket::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(id);
        buf.writeBlockPos(pos);
        buf.writeBoolean(cake);
        buf.writeInt(bites);
        buf.writeInt(ticks);
        buf.writeBoolean(remove);
    }

    public static CakeMakerBlockS2CPacket read(FriendlyByteBuf buf) {
        return new CakeMakerBlockS2CPacket(
                buf.readUUID(),
                buf.readBlockPos(),
                buf.readBoolean(),
                buf.readInt(),
                buf.readInt(),
                buf.readBoolean());
    }
}
