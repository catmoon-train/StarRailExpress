package net.exmo.sre.sixtyseconds.network;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.Noellesroles;

import java.util.UUID;

/**
 * 服务端→客户端：通知客户端某队友在指定位置打了标点。
 * @param playerId 打标点的玩家 UUID
 * @param x       标点世界坐标 X
 * @param y       标点世界坐标 Y
 * @param z       标点世界坐标 Z
 */
public record TeamPingS2CPacket(UUID playerId, int x, int y, int z) implements CustomPacketPayload {
    public static final Type<TeamPingS2CPacket> ID = new Type<>(Noellesroles.id("team_ping_s2c"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TeamPingS2CPacket> CODEC =
            StreamCodec.ofMember((packet, buf) -> {
                buf.writeUUID(packet.playerId());
                buf.writeVarInt(packet.x());
                buf.writeVarInt(packet.y());
                buf.writeVarInt(packet.z());
            }, buf -> new TeamPingS2CPacket(buf.readUUID(), buf.readVarInt(),
                    buf.readVarInt(), buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    /** 创建标点包（浮点坐标转整数）。 */
    public static TeamPingS2CPacket of(UUID playerId, Vec3 pos) {
        return new TeamPingS2CPacket(playerId, (int) pos.x, (int) pos.y, (int) pos.z);
    }

    /** 发送给指定玩家 */
    public static void sendTo(ServerPlayer recipient, TeamPingS2CPacket packet) {
        ServerPlayNetworking.send(recipient, packet);
    }
}
