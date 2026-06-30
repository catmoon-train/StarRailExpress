package io.wifi.starrailexpress.shop.network;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * S2C 数据包：完整的商店价格表（已规范化序列化的字节）+ 其哈希。仅在客户端缓存未命中、主动请求后才发送。
 * S2C data: the full, canonically-serialized shop price table bytes plus its hash. Only sent after the
 * client misses its cache and explicitly requests it.
 */
public record ShopPriceDataS2CPayload(String hash, byte[] data) implements CustomPacketPayload {
    public static final Type<ShopPriceDataS2CPayload> TYPE = new Type<>(SRE.id("shop_price_data"));
    public static final StreamCodec<FriendlyByteBuf, ShopPriceDataS2CPayload> CODEC = StreamCodec.ofMember(
            ShopPriceDataS2CPayload::write, ShopPriceDataS2CPayload::read);

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(hash);
        buf.writeByteArray(data);
    }

    public static ShopPriceDataS2CPayload read(FriendlyByteBuf buf) {
        return new ShopPriceDataS2CPayload(buf.readUtf(), buf.readByteArray());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
