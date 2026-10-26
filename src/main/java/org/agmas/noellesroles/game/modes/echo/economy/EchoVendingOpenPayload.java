package org.agmas.noellesroles.game.modes.echo.economy;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/**
 * 让本人打开某台售货机的回响商店。
 *
 * <p>只带机器坐标，不带商品、价格或余额——这些都留在服务端，避免客户端改价。
 */
public record EchoVendingOpenPayload(BlockPos pos) implements CustomPacketPayload {
    public static final Type<EchoVendingOpenPayload> ID =
            new Type<>(ResourceLocation.fromNamespaceAndPath("noellesroles", "echo/vending_open"));
    public static final StreamCodec<RegistryFriendlyByteBuf, EchoVendingOpenPayload> CODEC = StreamCodec.of(
            (buf, value) -> buf.writeBlockPos(value.pos()),
            buf -> new EchoVendingOpenPayload(buf.readBlockPos()));

    @Override public Type<? extends CustomPacketPayload> type() { return ID; }

    public static void send(ServerPlayer player, BlockPos pos) {
        ServerPlayNetworking.send(player, new EchoVendingOpenPayload(pos));
    }
}
