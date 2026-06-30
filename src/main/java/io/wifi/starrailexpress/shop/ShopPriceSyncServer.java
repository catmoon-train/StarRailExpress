package io.wifi.starrailexpress.shop;

import io.wifi.starrailexpress.shop.network.ShopPriceDataS2CPayload;
import io.wifi.starrailexpress.shop.network.ShopPriceHandshakeS2CPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * 服务端商店价格同步协调器：缓存当前价格表，按「先发哈希、按需发完整数据」的握手协议同步给客户端。
 * Server-side coordinator for shop-price sync: caches the current price table and pushes it to clients
 * via a "hash first, full data on demand" handshake.
 */
public final class ShopPriceSyncServer {

    private static volatile ShopPriceTable current;

    private ShopPriceSyncServer() {
    }

    /** 当前价格表（懒构建并缓存）。 / The current price table (lazily built &amp; cached). */
    public static synchronized ShopPriceTable current() {
        if (current == null) {
            current = ShopPriceTable.build();
        }
        return current;
    }

    /** 商店被重建后调用，使下次访问重新构建。 / Invalidate after the shop is rebuilt. */
    public static synchronized void invalidate() {
        current = null;
    }

    /** 给单个玩家发送握手（仅哈希）。 / Send the handshake (hash only) to one player. */
    public static void syncTo(ServerPlayer player) {
        if (player == null) {
            return;
        }
        ServerPlayNetworking.send(player, new ShopPriceHandshakeS2CPayload(current().hash()));
    }

    /**
     * 重建价格表并向所有在线玩家重新握手（用于 {@code /sre config reload} 之后）。
     * Rebuild the table and re-handshake every online player (after {@code /sre config reload}).
     */
    public static void resyncAll(MinecraftServer server) {
        invalidate();
        if (server == null) {
            return;
        }
        ShopPriceHandshakeS2CPayload payload = new ShopPriceHandshakeS2CPayload(current().hash());
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    /**
     * 处理客户端的完整数据请求：把当前价格表完整发回（连同其哈希）。
     * Handle a client's full-data request: send back the full table bytes (with its hash).
     */
    public static void handleRequest(ServerPlayer player, String requestedHash) {
        if (player == null) {
            return;
        }
        ShopPriceTable table = current();
        ServerPlayNetworking.send(player, new ShopPriceDataS2CPayload(table.hash(), table.toBytes()));
    }
}
