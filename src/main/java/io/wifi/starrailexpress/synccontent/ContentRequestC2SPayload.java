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

package io.wifi.starrailexpress.synccontent;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 同步请求（客户端 → 服务端）：本地缓存未命中，请求该通道的完整内容。
 *
 * <p>
 * 只有真正没缓存的客户端才会发这个包，服务端因此不需要维护「每个玩家已有哪些哈希」的表，
 * 也不再无条件地把全文推给所有人。
 */
public record ContentRequestC2SPayload(String channel, String hash) implements CustomPacketPayload {

    public static final Type<ContentRequestC2SPayload> TYPE = new Type<>(SRE.id("content_request"));
    public static final StreamCodec<FriendlyByteBuf, ContentRequestC2SPayload> CODEC = StreamCodec
            .ofMember(ContentRequestC2SPayload::write, ContentRequestC2SPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(channel);
        buf.writeUtf(hash);
    }

    public static ContentRequestC2SPayload read(FriendlyByteBuf buf) {
        return new ContentRequestC2SPayload(buf.readUtf(), buf.readUtf());
    }
}
