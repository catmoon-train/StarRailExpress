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

package io.wifi.starrailexpress.network;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * 地图介绍 / 轮抽 / 投票 UI 用的展示数据。
 *
 * <p>
 * 内容是**解析后的** {@link MapDisplayInfo} 列表（逐字段编码，不含任何 JSON 文本），
 * 取代了以前「每张图发一段瘦身 JSON、客户端再 Gson 解析」的做法。
 * 数据由服务端的 {@link MapIntroData} 提供（只含投票配置里的地图；管理员可请求全部）。
 *
 * <p>
 * 仍然分块：DTO 很小，正常情况下 1 个包就够；分块只是防止地图数量极端时超出 custom_payload 上限。
 */
public record MapIntroSyncPayload(
        int chunkIndex,
        int totalChunks,
        List<MapDisplayInfo> maps) implements CustomPacketPayload {
    public static final Type<MapIntroSyncPayload> ID = new Type<>(SRE.id("map_intro_sync"));
    public static final StreamCodec<FriendlyByteBuf, MapIntroSyncPayload> CODEC =
            CustomPacketPayload.codec(MapIntroSyncPayload::write, MapIntroSyncPayload::new);

    /**
     * 包格式魔数（"MIN2"）。
     * <p>
     * 客户端会先校验它：旧服务端（或任何不兼容的包）读出来对不上就直接判定为「忽略」，
     * 返回一个空的 payload 而不是抛异常 —— 自定义包解码抛异常会导致客户端断开连接。
     */
    private static final int MAGIC = 0x4D494E32;

    /** 被判为不兼容时的哨兵值：客户端看到 {@code chunkIndex < 0} 就不动缓存。 */
    private static final int IGNORED_CHUNK_INDEX = -1;

    /** 单个包最多带几张图。 */
    public static final int MAX_MAPS_PER_CHUNK = 32;
    private static final int MAX_CHUNKS = 512;
    private static final int MAX_MAPS_IN_PAYLOAD = 4096;

    public MapIntroSyncPayload(List<MapDisplayInfo> maps) {
        this(0, 1, maps);
    }

    /** 解码失败/格式不匹配时的空包（不抛异常，避免断开连接）。 */
    public static MapIntroSyncPayload ignored() {
        return new MapIntroSyncPayload(IGNORED_CHUNK_INDEX, 0, List.of());
    }

    /** 是否是「不兼容/损坏、应当忽略」的包。 */
    public boolean isIgnored() {
        return chunkIndex < 0;
    }

    private MapIntroSyncPayload(FriendlyByteBuf buffer) {
        this(decodeOrEmpty(buffer));
    }

    private MapIntroSyncPayload(MapIntroSyncPayload decoded) {
        this(decoded.chunkIndex(), decoded.totalChunks(), decoded.maps());
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeInt(MAGIC);
        buffer.writeVarInt(Math.max(0, chunkIndex));
        buffer.writeVarInt(Math.max(1, totalChunks));
        List<MapDisplayInfo> safeMaps = maps == null ? List.of() : maps;
        buffer.writeVarInt(safeMaps.size());
        for (MapDisplayInfo map : safeMaps) {
            map.write(buffer);
        }
    }

    /** 按条数分块（DTO 很小，通常只有 1 块）。 */
    public static List<MapIntroSyncPayload> chunk(List<MapDisplayInfo> maps) {
        List<MapDisplayInfo> safeMaps = maps == null ? List.of() : maps;
        List<List<MapDisplayInfo>> batches = new ArrayList<>();
        for (int i = 0; i < safeMaps.size(); i += MAX_MAPS_PER_CHUNK) {
            batches.add(safeMaps.subList(i, Math.min(safeMaps.size(), i + MAX_MAPS_PER_CHUNK)));
        }
        if (batches.isEmpty()) {
            batches.add(List.of());
        }
        int total = batches.size();
        List<MapIntroSyncPayload> result = new ArrayList<>(total);
        for (int i = 0; i < total; i++) {
            result.add(new MapIntroSyncPayload(i, total, List.copyOf(batches.get(i))));
        }
        return result;
    }

    private static MapIntroSyncPayload decodeOrEmpty(FriendlyByteBuf buffer) {
        try {
            // 魔数不匹配 = 旧服务端/不兼容格式：直接忽略，绝不抛异常（否则客户端会被断开）
            if (buffer.readableBytes() < 4 || buffer.readInt() != MAGIC) {
                return ignored();
            }
            int chunkIndex = Math.max(0, buffer.readVarInt());
            int totalChunks = Math.min(MAX_CHUNKS, Math.max(1, buffer.readVarInt()));
            int size = Math.min(MAX_MAPS_IN_PAYLOAD, Math.max(0, buffer.readVarInt()));
            List<MapDisplayInfo> maps = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                if (buffer.readableBytes() <= 0) {
                    break;
                }
                maps.add(MapDisplayInfo.read(buffer));
            }
            return new MapIntroSyncPayload(chunkIndex, totalChunks, List.copyOf(maps));
        } catch (Exception e) {
            SRE.LOGGER.warn("Ignoring incompatible/truncated map intro payload (server format mismatch?)", e);
            return ignored();
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
