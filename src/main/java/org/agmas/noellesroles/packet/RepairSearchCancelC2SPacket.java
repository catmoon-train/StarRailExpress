package org.agmas.noellesroles.packet;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.game.modes.repair.RepairSearchState;

public record RepairSearchCancelC2SPacket() implements CustomPacketPayload {
    public static final Type<RepairSearchCancelC2SPacket> ID = new Type<>(Noellesroles.id("repair_search_cancel"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RepairSearchCancelC2SPacket> CODEC = StreamCodec
            .ofMember(RepairSearchCancelC2SPacket::encode, RepairSearchCancelC2SPacket::decode);

    public void encode(RegistryFriendlyByteBuf buf) {
    }

    public static RepairSearchCancelC2SPacket decode(RegistryFriendlyByteBuf buf) {
        return new RepairSearchCancelC2SPacket();
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static void handle(RepairSearchCancelC2SPacket payload, ServerPlayNetworking.Context context) {
        RepairSearchState.cancel(context.player());
    }
}
