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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 同步握手（服务端 → 客户端）：**只发哈希，不发内容**。
 *
 * <p>
 * 一个包覆盖全部通道，所以玩家加入服务器时，四个自定义内容系统的同步成本从
 * 「四份 JSON 全文（可能各拆成多个 30k 字符分块）」降到这一个几十字节的包；
 * 客户端本地缓存命中时服务端不再发送任何全文（见
 * {@link io.wifi.starrailexpress.client.network.ContentSyncClient}）。
 *
 * <p>
 * 包里的通道顺序按 {@link ContentChannel#LOAD_ORDER} 发送（依赖项在前），便于排查同步问题；
 * 客户端不依赖这个顺序（自己按 LOAD_ORDER 定序，见 {@code ContentSyncClient}）。
 */
public record ContentHandshakeS2CPayload(int protocolVersion, Map<String, String> hashes)
        implements CustomPacketPayload {

    public static final Type<ContentHandshakeS2CPayload> TYPE = new Type<>(SRE.id("content_handshake"));
    public static final StreamCodec<FriendlyByteBuf, ContentHandshakeS2CPayload> CODEC = StreamCodec
            .ofMember(ContentHandshakeS2CPayload::write, ContentHandshakeS2CPayload::read);

    public ContentHandshakeS2CPayload(int protocolVersion, Map<String, String> hashes) {
        this.protocolVersion = protocolVersion;
        // 不能用 Map.copyOf：它返回的不可变 Map 迭代顺序未定义（同一次握手的顺序会随 JVM 变化，
        // 排查同步问题时不可复现）。这里用 LinkedHashMap 保住传入顺序，顺手挡掉 null（writeUtf 会炸）。
        Map<String, String> ordered = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : hashes.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            ordered.put(entry.getKey(), entry.getValue());
        }
        this.hashes = Collections.unmodifiableMap(ordered);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(protocolVersion);
        buf.writeVarInt(hashes.size());
        for (Map.Entry<String, String> entry : hashes.entrySet()) {
            buf.writeUtf(entry.getKey());
            buf.writeUtf(entry.getValue());
        }
    }

    public static ContentHandshakeS2CPayload read(FriendlyByteBuf buf) {
        int protocolVersion = buf.readVarInt();
        int size = buf.readVarInt();
        Map<String, String> hashes = new LinkedHashMap<>();
        for (int i = 0; i < size; i++) {
            hashes.put(buf.readUtf(), buf.readUtf());
        }
        return new ContentHandshakeS2CPayload(protocolVersion, hashes);
    }
}
