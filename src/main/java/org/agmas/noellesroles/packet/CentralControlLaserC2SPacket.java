package org.agmas.noellesroles.packet;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

/**
 * 中控员在监控页面按技能键发射激光（C2S）。
 *
 * <p>
 * 只上报「哪台摄像头 + 监控画面准星方向」，射线检测全部在服务端做，
 * 避免客户端伪造命中。
 */
public record CentralControlLaserC2SPacket(BlockPos cameraPos, float lookYaw, float lookPitch)
        implements CustomPacketPayload {

    public static final Type<CentralControlLaserC2SPacket> ID = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "central_control_laser"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CentralControlLaserC2SPacket> CODEC = StreamCodec
            .ofMember(CentralControlLaserC2SPacket::write, CentralControlLaserC2SPacket::read);

    private void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(cameraPos);
        buf.writeFloat(lookYaw);
        buf.writeFloat(lookPitch);
    }

    private static CentralControlLaserC2SPacket read(FriendlyByteBuf buf) {
        return new CentralControlLaserC2SPacket(buf.readBlockPos(), buf.readFloat(), buf.readFloat());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
