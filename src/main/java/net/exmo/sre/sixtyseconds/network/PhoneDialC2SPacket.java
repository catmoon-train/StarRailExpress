package net.exmo.sre.sixtyseconds.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 拨号C2S包
 */
public record PhoneDialC2SPacket(String number) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<PhoneDialC2SPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.tryBuild("noellesroles", "phone_dial"));

    public static final StreamCodec<FriendlyByteBuf, PhoneDialC2SPacket> CODEC = StreamCodec.ofMember(
            PhoneDialC2SPacket::encode, PhoneDialC2SPacket::decode
    );

    private void encode(FriendlyByteBuf buf) {
        buf.writeUtf(number);
    }

    private static PhoneDialC2SPacket decode(FriendlyByteBuf buf) {
        return new PhoneDialC2SPacket(buf.readUtf(6));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
