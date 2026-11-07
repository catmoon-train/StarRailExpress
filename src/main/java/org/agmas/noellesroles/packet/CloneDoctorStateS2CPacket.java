package org.agmas.noellesroles.packet;

import org.agmas.noellesroles.Noellesroles;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** 只发送给本人；所有剩余时长均为 tick，不携带其他玩家的临时状态。 */
public record CloneDoctorStateS2CPacket(boolean isClone, int castTicks, int transformTicks,
        int copyCooldown, int substituteCooldown) implements CustomPacketPayload {
    public static final Type<CloneDoctorStateS2CPacket> ID = new Type<>(Noellesroles.id("clone_doctor_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CloneDoctorStateS2CPacket> CODEC = StreamCodec.ofMember(
            (packet, buf) -> {
                buf.writeBoolean(packet.isClone());
                buf.writeVarInt(packet.castTicks());
                buf.writeVarInt(packet.transformTicks());
                buf.writeVarInt(packet.copyCooldown());
                buf.writeVarInt(packet.substituteCooldown());
            }, buf -> new CloneDoctorStateS2CPacket(buf.readBoolean(), buf.readVarInt(), buf.readVarInt(),
                    buf.readVarInt(), buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
