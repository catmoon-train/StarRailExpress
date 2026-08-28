package org.agmas.noellesroles.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

import java.util.UUID;

public record FakeSteveApparitionS2CPacket(UUID apparitionId, double x, double y, double z, boolean remove)
        implements CustomPacketPayload {
    public static final Type<FakeSteveApparitionS2CPacket> ID = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "fake_steve_apparition"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FakeSteveApparitionS2CPacket> CODEC =
            StreamCodec.ofMember(FakeSteveApparitionS2CPacket::write, FakeSteveApparitionS2CPacket::read);

    private void write(FriendlyByteBuf buf) {
        buf.writeUUID(apparitionId);
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        buf.writeBoolean(remove);
    }

    private static FakeSteveApparitionS2CPacket read(FriendlyByteBuf buf) {
        return new FakeSteveApparitionS2CPacket(buf.readUUID(), buf.readDouble(), buf.readDouble(),
                buf.readDouble(), buf.readBoolean());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
