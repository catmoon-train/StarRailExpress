package org.agmas.noellesroles.packet;

import org.agmas.noellesroles.Noellesroles;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** 扫描仪请求；false 只读取公开历史，true 请求付费扫描，均由服务端验证。 */
public record IdentityScannerRequestC2SPacket(BlockPos pos, boolean scan) implements CustomPacketPayload {
    public static final Type<IdentityScannerRequestC2SPacket> ID = new Type<>(Noellesroles.id("identity_scanner_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, IdentityScannerRequestC2SPacket> CODEC = StreamCodec.ofMember(
            (packet, buf) -> {
                buf.writeBlockPos(packet.pos());
                buf.writeBoolean(packet.scan());
            }, buf -> new IdentityScannerRequestC2SPacket(buf.readBlockPos(), buf.readBoolean()));

    public IdentityScannerRequestC2SPacket {
        pos = pos.immutable();
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
