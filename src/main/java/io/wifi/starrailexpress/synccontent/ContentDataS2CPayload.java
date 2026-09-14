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
 * 同步数据（服务端 → 客户端）：一个通道的完整内容，**deflate 压缩后按字节传输**。
 *
 * <p>
 * 相比旧实现（JSON 文本按 30000 字符切片 + {@code writeUtf}）：
 * <ul>
 * <li>压缩后体积通常只有 1/5 ~ 1/10，绝大多数配置一个包就发完（旧实现要拆几包）；</li>
 * <li>不再有「字符串切一半、客户端拼字符串」的分配与编码开销；</li>
 * <li>{@code chunkIndex}/{@code totalChunks} 只在压缩后仍超过单包上限时才用得上。</li>
 * </ul>
 */
public record ContentDataS2CPayload(String channel, String hash, int chunkIndex, int totalChunks, byte[] data)
        implements CustomPacketPayload {

    public static final Type<ContentDataS2CPayload> TYPE = new Type<>(SRE.id("content_data"));
    public static final StreamCodec<FriendlyByteBuf, ContentDataS2CPayload> CODEC = StreamCodec
            .ofMember(ContentDataS2CPayload::write, ContentDataS2CPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(channel);
        buf.writeUtf(hash);
        buf.writeVarInt(chunkIndex);
        buf.writeVarInt(totalChunks);
        buf.writeByteArray(data);
    }

    public static ContentDataS2CPayload read(FriendlyByteBuf buf) {
        return new ContentDataS2CPayload(buf.readUtf(), buf.readUtf(), buf.readVarInt(), buf.readVarInt(),
                buf.readByteArray());
    }
}
