package org.agmas.noellesroles.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

/**
 * 幽露自由摄像机 S2C：{@code active=true} 进入自由摄像机，{@code false} 退出。
 */
public record YouluFreeCamS2CPacket(boolean active) implements CustomPacketPayload {
    public static final ResourceLocation PAYLOAD_ID = ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID,
            "youlu_free_cam_s2c");
    public static final Type<YouluFreeCamS2CPacket> ID = new Type<>(PAYLOAD_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, YouluFreeCamS2CPacket> CODEC = StreamCodec
            .ofMember(YouluFreeCamS2CPacket::write, YouluFreeCamS2CPacket::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBoolean(active);
    }

    public static YouluFreeCamS2CPacket read(FriendlyByteBuf buf) {
        return new YouluFreeCamS2CPacket(buf.readBoolean());
    }
}
