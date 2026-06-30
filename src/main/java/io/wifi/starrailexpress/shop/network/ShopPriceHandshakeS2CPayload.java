package io.wifi.starrailexpress.shop.network;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * S2C 握手包：玩家进服（或服务端重载商店）时，服务端先只发送商店价格表的哈希。
 * 客户端若本地已缓存相同哈希的配置则直接应用、无需服务端再发送完整内容；否则回一个
 * {@link ShopPriceRequestC2SPayload} 请求完整数据。
 *
 * <p>S2C handshake: on join (or shop reload) the server sends only the hash of its shop price table.
 * If the client already has a cached config with that hash it applies it locally and the server does
 * not send the full content; otherwise the client replies with {@link ShopPriceRequestC2SPayload}.
 */
public record ShopPriceHandshakeS2CPayload(String hash) implements CustomPacketPayload {
    public static final Type<ShopPriceHandshakeS2CPayload> TYPE = new Type<>(SRE.id("shop_price_handshake"));
    public static final StreamCodec<FriendlyByteBuf, ShopPriceHandshakeS2CPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, ShopPriceHandshakeS2CPayload::hash, ShopPriceHandshakeS2CPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
