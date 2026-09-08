package org.agmas.noellesroles.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import org.agmas.noellesroles.Noellesroles;

public record InsuranceSubmitC2SPacket(InteractionHand hand, String reason) implements CustomPacketPayload {
    public static final Type<InsuranceSubmitC2SPacket> ID = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "insurance_submit"));
    public static final StreamCodec<RegistryFriendlyByteBuf, InsuranceSubmitC2SPacket> CODEC =
            StreamCodec.ofMember(InsuranceSubmitC2SPacket::write, InsuranceSubmitC2SPacket::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeEnum(hand);
        buf.writeUtf(reason, 32);
    }

    private static InsuranceSubmitC2SPacket read(FriendlyByteBuf buf) {
        return new InsuranceSubmitC2SPacket(buf.readEnum(InteractionHand.class), buf.readUtf(32));
    }
}
