package org.agmas.noellesroles.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import org.agmas.noellesroles.Noellesroles;

public record InsuranceOpenS2CPacket(InteractionHand hand) implements CustomPacketPayload {
    public static final Type<InsuranceOpenS2CPacket> ID = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "insurance_open"));
    public static final StreamCodec<RegistryFriendlyByteBuf, InsuranceOpenS2CPacket> CODEC =
            StreamCodec.ofMember(InsuranceOpenS2CPacket::write, InsuranceOpenS2CPacket::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeEnum(hand);
    }

    private static InsuranceOpenS2CPacket read(FriendlyByteBuf buf) {
        return new InsuranceOpenS2CPacket(buf.readEnum(InteractionHand.class));
    }
}
