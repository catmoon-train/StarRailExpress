package io.wifi.starrailexpress.shop.network;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * C2S 请求包：客户端本地缓存未命中握手哈希时，向服务端请求完整的商店价格表。
 * C2S request: sent when the client's local cache misses the handshake hash, asking the server for the
 * full shop price table.
 */
public record ShopPriceRequestC2SPayload(String hash) implements CustomPacketPayload {
    public static final Type<ShopPriceRequestC2SPayload> TYPE = new Type<>(SRE.id("shop_price_request"));
    public static final StreamCodec<FriendlyByteBuf, ShopPriceRequestC2SPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, ShopPriceRequestC2SPayload::hash, ShopPriceRequestC2SPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
