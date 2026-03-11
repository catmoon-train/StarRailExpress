package org.agmas.noellesroles.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

public record VendingBuyMessageCallBackS2CPacket(String componentKey) implements CustomPacketPayload {
    public static final ResourceLocation VENDING_BUY_MESSAGE_CALLBACK_S2C = ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID,
            "vending_buy_message_callback_s2c");
    public static final Type<VendingBuyMessageCallBackS2CPacket> ID = new Type<>(
            VENDING_BUY_MESSAGE_CALLBACK_S2C);

    public static final StreamCodec<RegistryFriendlyByteBuf, VendingBuyMessageCallBackS2CPacket> CODEC = StreamCodec.ofMember(
            VendingBuyMessageCallBackS2CPacket::encode,
            VendingBuyMessageCallBackS2CPacket::decode
    );


    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeUtf(this.componentKey);
    }

    public static VendingBuyMessageCallBackS2CPacket decode(RegistryFriendlyByteBuf buf) {
        return new VendingBuyMessageCallBackS2CPacket(buf.readUtf());
    }
}
