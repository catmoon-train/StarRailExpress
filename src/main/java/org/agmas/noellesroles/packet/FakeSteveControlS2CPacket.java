package org.agmas.noellesroles.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

/** Short-lived vanilla-input lease for the client that owns a possessed body. */
public record FakeSteveControlS2CPacket(long sequence, int durationTicks,
        float forward, float strafe, boolean jump, boolean sprint,
        boolean crouch, float targetYaw, float targetPitch,
        boolean active) implements CustomPacketPayload {
    public static final Type<FakeSteveControlS2CPacket> ID = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "fake_steve_control"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FakeSteveControlS2CPacket> CODEC =
            StreamCodec.ofMember(FakeSteveControlS2CPacket::write, FakeSteveControlS2CPacket::read);

    private void write(FriendlyByteBuf buf) {
        buf.writeVarLong(sequence);
        buf.writeVarInt(durationTicks);
        buf.writeFloat(forward);
        buf.writeFloat(strafe);
        buf.writeBoolean(jump);
        buf.writeBoolean(sprint);
        buf.writeBoolean(crouch);
        buf.writeFloat(targetYaw);
        buf.writeFloat(targetPitch);
        buf.writeBoolean(active);
    }

    private static FakeSteveControlS2CPacket read(FriendlyByteBuf buf) {
        return new FakeSteveControlS2CPacket(buf.readVarLong(), buf.readVarInt(),
                buf.readFloat(), buf.readFloat(), buf.readBoolean(),
                buf.readBoolean(), buf.readBoolean(), buf.readFloat(),
                buf.readFloat(), buf.readBoolean());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
