package net.exmo.sre.sixtyseconds.network;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.Noellesroles;

/**
 * 服务端→客户端：同步该玩家本次登岛的落点（用于在海图上显示「来时区域」标记）。
 * <p>
 * 每次扬帆登岛时由服务端发送，客户端 SeaChartFullScreen 据此画返回区域圈。
 * </p>
 */
public record SixtySecondsSeaChartArrivalS2CPacket(BlockPos arrivalPos) implements CustomPacketPayload {

    public static final Type<SixtySecondsSeaChartArrivalS2CPacket> ID =
            new Type<>(Noellesroles.id("sixty_seconds_sea_chart_arrival"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SixtySecondsSeaChartArrivalS2CPacket> CODEC =
            StreamCodec.ofMember(
                    (packet, buf) -> buf.writeBlockPos(packet.arrivalPos()),
                    buf -> new SixtySecondsSeaChartArrivalS2CPacket(buf.readBlockPos())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static void send(ServerPlayer player, BlockPos arrivalPos) {
        ServerPlayNetworking.send(player, new SixtySecondsSeaChartArrivalS2CPacket(arrivalPos));
    }
}
