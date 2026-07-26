package org.agmas.noellesroles.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

/**
 * 幽露自由摄像机 C2S：ESC 取消（退出摄像机，不生成球烟、不进冷却）。
 */
public record YouluFreeCamCancelC2SPacket() implements CustomPacketPayload {
    public static final ResourceLocation PAYLOAD_ID = ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID,
            "youlu_free_cam_cancel_c2s");
    public static final Type<YouluFreeCamCancelC2SPacket> ID = new Type<>(PAYLOAD_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, YouluFreeCamCancelC2SPacket> CODEC = StreamCodec
            .ofMember(YouluFreeCamCancelC2SPacket::write, YouluFreeCamCancelC2SPacket::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public void write(FriendlyByteBuf buf) {
    }

    public static YouluFreeCamCancelC2SPacket read(FriendlyByteBuf buf) {
        return new YouluFreeCamCancelC2SPacket();
    }
}
