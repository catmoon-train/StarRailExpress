package org.agmas.noellesroles.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.Noellesroles;

/**
 * 幽露自由摄像机 C2S：客户端周期性上报当前摄像机位置（服务端按最大距离校验后保存）。
 */
public record YouluCamPosC2SPacket(Vec3 pos) implements CustomPacketPayload {
    public static final ResourceLocation PAYLOAD_ID = ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID,
            "youlu_cam_pos_c2s");
    public static final Type<YouluCamPosC2SPacket> ID = new Type<>(PAYLOAD_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, YouluCamPosC2SPacket> CODEC = StreamCodec
            .ofMember(YouluCamPosC2SPacket::write, YouluCamPosC2SPacket::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVec3(pos);
    }

    public static YouluCamPosC2SPacket read(FriendlyByteBuf buf) {
        return new YouluCamPosC2SPacket(buf.readVec3());
    }
}
