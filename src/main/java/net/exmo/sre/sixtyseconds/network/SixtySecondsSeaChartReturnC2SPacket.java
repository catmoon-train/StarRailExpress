package net.exmo.sre.sixtyseconds.network;

import net.exmo.sre.sixtyseconds.arena.SixtySecondsSearchZones;
import net.exmo.sre.sixtyseconds.island.SixtySecondsIslands;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.Noellesroles;

/**
 * 客户端→服务端：海图「返回住所」请求。
 * <p>
 * 服务端校验：1) 未处于战斗中；2) 玩家在登岛点附近。
 * 校验通过后向客户端发送 {@link SixtySecondsSeaChartReturnStartS2CPacket}
 * 启动 10 秒划船动画，动画结束后才真正传送回家。
 * </p>
 */
public record SixtySecondsSeaChartReturnC2SPacket() implements CustomPacketPayload {

    public static final Type<SixtySecondsSeaChartReturnC2SPacket> ID =
            new Type<>(Noellesroles.id("sixty_seconds_sea_chart_return"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SixtySecondsSeaChartReturnC2SPacket> CODEC =
            StreamCodec.ofMember(
                    (packet, buf) -> {},
                    buf -> new SixtySecondsSeaChartReturnC2SPacket()
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    /** 服务端接收：校验状态后启动返回流程。 */
    public static void handle(SixtySecondsSeaChartReturnC2SPacket packet, ServerPlayNetworking.Context context) {
        ServerPlayer player = context.player();
        player.server.execute(() -> SixtySecondsIslands.requestReturn(player));
    }
}
