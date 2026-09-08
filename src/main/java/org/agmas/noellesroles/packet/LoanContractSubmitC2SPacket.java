package org.agmas.noellesroles.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

import java.util.UUID;

/** Client request to sign a loan contract. The server revalidates every field. */
public record LoanContractSubmitC2SPacket(UUID lender, int amount) implements CustomPacketPayload {
    public static final Type<LoanContractSubmitC2SPacket> ID = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "loan_contract_submit"));
    public static final StreamCodec<RegistryFriendlyByteBuf, LoanContractSubmitC2SPacket> CODEC =
            StreamCodec.ofMember(LoanContractSubmitC2SPacket::write, LoanContractSubmitC2SPacket::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeUUID(lender);
        buf.writeVarInt(amount);
    }

    private static LoanContractSubmitC2SPacket read(FriendlyByteBuf buf) {
        return new LoanContractSubmitC2SPacket(buf.readUUID(), buf.readVarInt());
    }
}
