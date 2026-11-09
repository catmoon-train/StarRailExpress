package org.agmas.noellesroles.packet;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

/**
 * 中控员在监控页面拖动鼠标时，把当前镜头方向同步给服务端（C2S）。
 *
 * <p>
 * 只用于「红外线扫描仪要跟着镜头照亮哪、照到哪」这一件事：
 * 服务端本来不知道玩家在监控画面里把镜头转到哪了，没有这个包，发光区域就只会停在摄像头固定朝向。
 *
 * <p>
 * 客户端会做节流（视角没变不发、按固定间隔发），因此这个包很轻。
 */
public record CameraViewLookC2SPacket(BlockPos cameraPos, float lookYaw, float lookPitch)
        implements CustomPacketPayload {

    public static final Type<CameraViewLookC2SPacket> ID = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "camera_view_look"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CameraViewLookC2SPacket> CODEC = StreamCodec
            .ofMember(CameraViewLookC2SPacket::write, CameraViewLookC2SPacket::read);

    private void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(cameraPos);
        buf.writeFloat(lookYaw);
        buf.writeFloat(lookPitch);
    }

    private static CameraViewLookC2SPacket read(FriendlyByteBuf buf) {
        return new CameraViewLookC2SPacket(buf.readBlockPos(), buf.readFloat(), buf.readFloat());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
