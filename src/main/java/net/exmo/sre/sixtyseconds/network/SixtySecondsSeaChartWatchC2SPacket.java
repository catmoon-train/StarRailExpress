package net.exmo.sre.sixtyseconds.network;

import net.exmo.sre.sixtyseconds.island.SixtySecondsIslands;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.Noellesroles;

/**
 * 客户端→服务端：海图开屏 / 关屏订阅开关。
 * <p>
 * 订阅后服务端每秒向该玩家推 {@link SixtySecondsSeaChartPositionsS2CPacket}（庇护所 + 队友点位），
 * 关屏即退订。位置数据无法用「重大更改才同步」的纪律覆盖（队友随时在动），
 * 因此改用「只在有人看时才推」把流量压到最低——玩家不开海图就是零流量。
 * </p>
 *
 * @param watching true=开屏订阅，false=关屏退订
 */
public record SixtySecondsSeaChartWatchC2SPacket(boolean watching) implements CustomPacketPayload {

    public static final Type<SixtySecondsSeaChartWatchC2SPacket> ID =
            new Type<>(Noellesroles.id("sixty_seconds_sea_chart_watch"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SixtySecondsSeaChartWatchC2SPacket> CODEC =
            StreamCodec.ofMember(
                    (packet, buf) -> buf.writeBoolean(packet.watching()),
                    buf -> new SixtySecondsSeaChartWatchC2SPacket(buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    /** 服务端接收：登记/注销观看者，订阅时立刻回推一份，免得开屏第一秒是空的。 */
    public static void handle(SixtySecondsSeaChartWatchC2SPacket packet, ServerPlayNetworking.Context context) {
        ServerPlayer player = context.player();
        player.server.execute(() -> SixtySecondsIslands.setChartWatching(player, packet.watching()));
    }
}
