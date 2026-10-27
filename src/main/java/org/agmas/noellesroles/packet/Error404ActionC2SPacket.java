package org.agmas.noellesroles.packet;

import java.util.UUID;
import org.agmas.noellesroles.Noellesroles;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** 战术终端只提交意图；所有权限、目标、坐标和物品消耗由服务端裁决。 */
public record Error404ActionC2SPacket(int action, UUID puppet, UUID target, double x, double y, double z)
        implements CustomPacketPayload {
    public static final int OPEN = 0, SELECT = 1, MOVE = 2, ATTACK = 3, HOLD = 4, DETONATE = 5,
            ACCELERATE = 6, EXIT = 7;
    public static final UUID NONE = new UUID(0L, 0L);
    public static final Type<Error404ActionC2SPacket> ID = new Type<>(Noellesroles.id("error404_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, Error404ActionC2SPacket> CODEC = StreamCodec.ofMember(
            (packet, buf) -> {
                buf.writeVarInt(packet.action());
                buf.writeUUID(packet.puppet());
                buf.writeUUID(packet.target());
                buf.writeDouble(packet.x());
                buf.writeDouble(packet.y());
                buf.writeDouble(packet.z());
            }, buf -> new Error404ActionC2SPacket(buf.readVarInt(), buf.readUUID(), buf.readUUID(),
                    buf.readDouble(), buf.readDouble(), buf.readDouble()));
    @Override
    public Type<? extends CustomPacketPayload> type() { return ID; }
}
