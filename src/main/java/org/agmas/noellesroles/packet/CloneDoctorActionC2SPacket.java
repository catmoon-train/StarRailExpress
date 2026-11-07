package org.agmas.noellesroles.packet;

import java.util.UUID;

import org.agmas.noellesroles.Noellesroles;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** 克隆博士的背包选人与返回请求；资格、金币和冷却均由服务端校验。 */
public record CloneDoctorActionC2SPacket(UUID target, boolean returnToSelf) implements CustomPacketPayload {
    public static final Type<CloneDoctorActionC2SPacket> ID = new Type<>(Noellesroles.id("clone_doctor_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CloneDoctorActionC2SPacket> CODEC = StreamCodec.ofMember(
            (packet, buf) -> {
                buf.writeUUID(packet.target());
                buf.writeBoolean(packet.returnToSelf());
            }, buf -> new CloneDoctorActionC2SPacket(buf.readUUID(), buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
