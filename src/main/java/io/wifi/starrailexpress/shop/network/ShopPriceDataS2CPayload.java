/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

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
