package org.agmas.noellesroles.game.roles.Innocent.fool;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

/**
 * 退出塔罗会 C2S 包
 *
 * 玩家按ESC或右键时发送到服务端
 */
public record FoolLeaveMeetingC2SPacket() implements CustomPacketPayload {
    public static final ResourceLocation PAYLOAD_ID = ResourceLocation.fromNamespaceAndPath(
            Noellesroles.MOD_ID, "fool_leave_meeting");
    public static final Type<FoolLeaveMeetingC2SPacket> ID = new Type<>(PAYLOAD_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, FoolLeaveMeetingC2SPacket> CODEC;

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public void write(FriendlyByteBuf buf) {
    }

    public static FoolLeaveMeetingC2SPacket read(FriendlyByteBuf buf) {
        return new FoolLeaveMeetingC2SPacket();
    }

    static {
        CODEC = StreamCodec.ofMember(FoolLeaveMeetingC2SPacket::write, FoolLeaveMeetingC2SPacket::read);
    }
}
