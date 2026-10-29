package org.agmas.noellesroles.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.Noellesroles;

import java.util.UUID;

/** 只同步轨迹与阶段，不传逐方块破坏包，也不传服务端地形快照。 */
public record RoadRollerS2CPacket(UUID owner, int phase, Vec3 origin, double startY, double endY,
        float yaw, float pitch, int hold, int duration, int age) implements CustomPacketPayload {
    public static final int START = 0;
    public static final int FINISH = 1;
    public static final int CLEAR = 2;
    /** 压路机结束后立即回位，与时停剩余时长无关；普通 FINISH 不覆盖回溯/结算传送。 */
    public static final int RETURN = 3;
    public static final Type<RoadRollerS2CPacket> ID = new Type<>(Noellesroles.id("road_roller"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RoadRollerS2CPacket> CODEC = StreamCodec.ofMember(
            RoadRollerS2CPacket::write, RoadRollerS2CPacket::read);

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeUUID(owner);
        buf.writeVarInt(phase);
        buf.writeVec3(origin);
        buf.writeDouble(startY);
        buf.writeDouble(endY);
        buf.writeFloat(yaw);
        buf.writeFloat(pitch);
        buf.writeVarInt(hold);
        buf.writeVarInt(duration);
        buf.writeVarInt(age);
    }

    private static RoadRollerS2CPacket read(RegistryFriendlyByteBuf buf) {
        return new RoadRollerS2CPacket(buf.readUUID(), buf.readVarInt(), buf.readVec3(),
                buf.readDouble(), buf.readDouble(), buf.readFloat(), buf.readFloat(),
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
