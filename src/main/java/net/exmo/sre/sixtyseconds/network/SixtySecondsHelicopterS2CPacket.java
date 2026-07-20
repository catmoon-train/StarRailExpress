package net.exmo.sre.sixtyseconds.network;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.Noellesroles;

/**
 * 服务端→客户端：直升机撤离状态同步（全天一次或不发）。
 *
 * @param active       true=直升机已抵达 / false=直升机离场/取消（清客户端渲染）
 * @param x           降落点 X
 * @param y           降落点 Y
 * @param z           降落点 Z
 * @param evacRadius  撤离区半径（格）
 * @param evacMax     撤离上限
 * @param evacCount   已撤离人数
 */
public record SixtySecondsHelicopterS2CPacket(boolean active, int x, int y, int z,
        int evacRadius, int evacMax, int evacCount) implements CustomPacketPayload {

    public static final Type<SixtySecondsHelicopterS2CPacket> ID =
            new Type<>(Noellesroles.id("sixty_seconds_helicopter"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SixtySecondsHelicopterS2CPacket> CODEC =
            StreamCodec.ofMember((p, buf) -> {
                buf.writeBoolean(p.active());
                buf.writeVarInt(p.x());
                buf.writeVarInt(p.y());
                buf.writeVarInt(p.z());
                buf.writeVarInt(p.evacRadius());
                buf.writeVarInt(p.evacMax());
                buf.writeVarInt(p.evacCount());
            }, buf -> new SixtySecondsHelicopterS2CPacket(
                    buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    /** 发送直升机抵达包给一个玩家。 */
    public static void sendArrive(ServerPlayer player, BlockPos pos, int radius, int max, int count) {
        ServerPlayNetworking.send(player, new SixtySecondsHelicopterS2CPacket(true,
                pos.getX(), pos.getY(), pos.getZ(), radius, max, count));
    }

    /** 发送清空包。 */
    public static void sendClear(ServerPlayer player) {
        ServerPlayNetworking.send(player, new SixtySecondsHelicopterS2CPacket(false,
                0, 0, 0, 0, 0, 0));
    }

    /** 广播给所有玩家。 */
    public static void broadcastArrive(java.util.Collection<ServerPlayer> players,
            BlockPos pos, int radius, int max, int count) {
        var pkt = new SixtySecondsHelicopterS2CPacket(true,
                pos.getX(), pos.getY(), pos.getZ(), radius, max, count);
        for (ServerPlayer p : players) {
            ServerPlayNetworking.send(p, pkt);
        }
    }
}
