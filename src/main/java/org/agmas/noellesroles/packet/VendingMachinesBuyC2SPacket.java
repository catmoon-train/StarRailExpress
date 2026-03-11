package org.agmas.noellesroles.packet;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

public record VendingMachinesBuyC2SPacket(BlockPos blockPos, String item) implements CustomPacketPayload {
    public static final ResourceLocation VENDING_MACHINES_BUY_C2S = ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID,
            "vending_machines_buy_c2s");
    public static final CustomPacketPayload.Type<VendingMachinesBuyC2SPacket> TYPE = new CustomPacketPayload.Type<>(
            VENDING_MACHINES_BUY_C2S);

    public static final StreamCodec<RegistryFriendlyByteBuf, VendingMachinesBuyC2SPacket> CODEC = StreamCodec.ofMember(
            VendingMachinesBuyC2SPacket::encode,
            VendingMachinesBuyC2SPacket::decode
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(this.blockPos);
        buf.writeUtf(this.item);
    }

    public static VendingMachinesBuyC2SPacket decode(RegistryFriendlyByteBuf buf) {
        return new VendingMachinesBuyC2SPacket(
                buf.readBlockPos(),
                buf.readUtf()
        );
    }
}
