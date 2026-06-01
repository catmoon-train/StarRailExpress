package org.agmas.noellesroles.packet;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.game.modes.repair.RepairSearchState;

public record RepairSearchBeginC2SPacket(BlockPos pos) implements CustomPacketPayload {
    public static final Type<RepairSearchBeginC2SPacket> ID = new Type<>(Noellesroles.id("repair_search_begin"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RepairSearchBeginC2SPacket> CODEC = StreamCodec
            .ofMember(RepairSearchBeginC2SPacket::encode, RepairSearchBeginC2SPacket::decode);

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
    }

    public static RepairSearchBeginC2SPacket decode(RegistryFriendlyByteBuf buf) {
        return new RepairSearchBeginC2SPacket(buf.readBlockPos());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static void handle(RepairSearchBeginC2SPacket payload, ServerPlayNetworking.Context context) {
        RepairSearchState.begin(context.player(), payload.pos());
    }
}
