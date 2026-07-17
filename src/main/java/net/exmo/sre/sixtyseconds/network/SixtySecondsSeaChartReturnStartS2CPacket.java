package net.exmo.sre.sixtyseconds.network;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.Noellesroles;

/**
 * 服务端→客户端：启动「划船返回住所」10 秒倒计时动画。
 * <p>
 * 客户端收到后渲染划船 HUD 叠加层，倒计时结束后服务端执行传送。
 * </p>
 *
 * @param durationTicks 倒计时长度（tick），默认 200（10 秒）
 */
public record SixtySecondsSeaChartReturnStartS2CPacket(int durationTicks) implements CustomPacketPayload {

    public static final int DEFAULT_DURATION_TICKS = 20 * 10;

    public static final Type<SixtySecondsSeaChartReturnStartS2CPacket> ID =
            new Type<>(Noellesroles.id("sixty_seconds_sea_chart_return_start"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SixtySecondsSeaChartReturnStartS2CPacket> CODEC =
            StreamCodec.ofMember(
                    (packet, buf) -> buf.writeVarInt(packet.durationTicks()),
                    buf -> new SixtySecondsSeaChartReturnStartS2CPacket(buf.readVarInt())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    /** 发给单个玩家启动返回动画。 */
    public static void send(ServerPlayer player, int durationTicks) {
        ServerPlayNetworking.send(player,
                new SixtySecondsSeaChartReturnStartS2CPacket(durationTicks));
    }
}
