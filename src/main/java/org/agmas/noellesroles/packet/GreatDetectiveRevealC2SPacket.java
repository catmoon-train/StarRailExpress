package org.agmas.noellesroles.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

import java.util.UUID;

/**
 * 大侦探"目标情况"请求包：客户端在推理之书上点击某凶手的"目标情况"时发送，
 * 服务端据此记录该凶手与侦探当前的距离快照。
 */
public record GreatDetectiveRevealC2SPacket(UUID killer) implements CustomPacketPayload {

    public static final ResourceLocation PAYLOAD_ID = ResourceLocation.fromNamespaceAndPath(
            Noellesroles.MOD_ID, "great_detective_reveal");
    public static final Type<GreatDetectiveRevealC2SPacket> ID = new Type<>(PAYLOAD_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, GreatDetectiveRevealC2SPacket> CODEC =
            StreamCodec.ofMember(
                    (packet, buf) -> buf.writeUUID(packet.killer()),
                    buf -> new GreatDetectiveRevealC2SPacket(buf.readUUID()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
