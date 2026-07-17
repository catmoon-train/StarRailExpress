package net.exmo.sre.sixtyseconds.network;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.Noellesroles;

/**
 * 服务端→客户端：启动「扬帆前往海岛」划船倒计时动画（去程；回程见
 * {@link SixtySecondsSeaChartReturnStartS2CPacket}）。
 * <p>
 * 与返航共用 {@code SeaChartReturnHud} 的划船演出，只是文案不同、结束时<b>不</b>清空登岛落点缓存
 * （去程结束后玩家正在岛上，落点马上会由 {@link SixtySecondsSeaChartArrivalS2CPacket} 重发）。
 * 倒计时结束由服务端执行传送——客户端动画只是演出，不参与判定。
 * </p>
 *
 * @param durationTicks 倒计时长度（tick）
 * @param islandId      目的岛 id（HUD 报幕岛名用）
 */
public record SixtySecondsSeaChartSailStartS2CPacket(int durationTicks, int islandId)
        implements CustomPacketPayload {

    public static final Type<SixtySecondsSeaChartSailStartS2CPacket> ID =
            new Type<>(Noellesroles.id("sixty_seconds_sea_chart_sail_start"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SixtySecondsSeaChartSailStartS2CPacket> CODEC =
            StreamCodec.ofMember((packet, buf) -> {
                buf.writeVarInt(packet.durationTicks());
                buf.writeVarInt(packet.islandId());
            }, buf -> new SixtySecondsSeaChartSailStartS2CPacket(buf.readVarInt(), buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    /** 发给单个玩家启动扬帆动画。 */
    public static void send(ServerPlayer player, int durationTicks, int islandId) {
        ServerPlayNetworking.send(player, new SixtySecondsSeaChartSailStartS2CPacket(durationTicks, islandId));
    }
}
