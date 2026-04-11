package org.agmas.noellesroles.roles.fool;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

/**
 * V键祷告/加入会议 C2S 包
 *
 * 客户端按V键时发送到服务端
 */
public record FoolPrayerC2SPacket() implements CustomPacketPayload {
    public static final ResourceLocation PAYLOAD_ID = ResourceLocation.fromNamespaceAndPath(
            Noellesroles.MOD_ID, "fool_prayer");
    public static final Type<FoolPrayerC2SPacket> ID = new Type<>(PAYLOAD_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, FoolPrayerC2SPacket> CODEC;

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public void write(FriendlyByteBuf buf) {
    }

    public static FoolPrayerC2SPacket read(FriendlyByteBuf buf) {
        return new FoolPrayerC2SPacket();
    }

    static {
        CODEC = StreamCodec.ofMember(FoolPrayerC2SPacket::write, FoolPrayerC2SPacket::read);
    }
}
