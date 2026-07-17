package net.exmo.sre.sixtyseconds.network;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.Noellesroles;

/**
 * 服务端→客户端：取消返回住所动画（受伤/离开登岛点等原因）。
 * <p>
 * 客户端收到后立即停止划船 HUD 并恢复游戏。
 * </p>
 */
public record SixtySecondsSeaChartReturnCancelS2CPacket() implements CustomPacketPayload {

    public static final Type<SixtySecondsSeaChartReturnCancelS2CPacket> ID =
            new Type<>(Noellesroles.id("sixty_seconds_sea_chart_return_cancel"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SixtySecondsSeaChartReturnCancelS2CPacket> CODEC =
            StreamCodec.ofMember(
                    (packet, buf) -> {},
                    buf -> new SixtySecondsSeaChartReturnCancelS2CPacket()
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static void send(ServerPlayer player) {
        ServerPlayNetworking.send(player, new SixtySecondsSeaChartReturnCancelS2CPacket());
    }
}
