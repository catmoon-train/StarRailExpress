package org.agmas.noellesroles.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

import java.util.UUID;

/** Opens the loan contract screen for the player who accepted a request. */
public record LoanContractOpenS2CPacket(UUID lender) implements CustomPacketPayload {
    public static final Type<LoanContractOpenS2CPacket> ID = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "loan_contract_open"));
    public static final StreamCodec<RegistryFriendlyByteBuf, LoanContractOpenS2CPacket> CODEC =
            StreamCodec.ofMember(LoanContractOpenS2CPacket::write, LoanContractOpenS2CPacket::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeUUID(lender);
    }

    private static LoanContractOpenS2CPacket read(FriendlyByteBuf buf) {
        return new LoanContractOpenS2CPacket(buf.readUUID());
    }
}
