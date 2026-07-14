package net.exmo.sre.sixtyseconds.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.agmas.noellesroles.Noellesroles;

/** 服务端→客户端：打开拆解台界面（拆解表两端共享静态定义，只需站点坐标）。 */
public record OpenDismantleS2CPacket(BlockPos pos) implements CustomPacketPayload {
    public static final Type<OpenDismantleS2CPacket> ID = new Type<>(Noellesroles.id("open_dismantle"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenDismantleS2CPacket> CODEC =
            StreamCodec.ofMember(OpenDismantleS2CPacket::encode, OpenDismantleS2CPacket::decode);

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
    }

    public static OpenDismantleS2CPacket decode(RegistryFriendlyByteBuf buf) {
        return new OpenDismantleS2CPacket(buf.readBlockPos());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
