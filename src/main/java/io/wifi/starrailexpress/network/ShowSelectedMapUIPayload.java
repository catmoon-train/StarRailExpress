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
import io.wifi.starrailexpress.game.data.MapConfig;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * 「显示已选地图 UI」：开票时告诉客户端**本轮候选的地图 id**。
 *
 * <p>
 * 以前这里塞的是整份 {@code MapConfig} 的 JSON 字符串（无长度上限），客户端再反序列化；
 * 现在只发候选 id —— 地图的显示名/描述/颜色/人数区间/游戏模式都已经在
 * {@link MapIntroSyncPayload} 的 {@link MapDisplayInfo} 里同步过了。
 */
public record ShowSelectedMapUIPayload(List<String> candidateIds) implements CustomPacketPayload {
    public static final Type<ShowSelectedMapUIPayload> ID = new Type<>(SRE.id("show_selected_map_ui"));
    public static final StreamCodec<FriendlyByteBuf, ShowSelectedMapUIPayload> CODEC = CustomPacketPayload
            .codec(ShowSelectedMapUIPayload::write, ShowSelectedMapUIPayload::new);

    /** 包格式魔数（与 {@code MapIntroSyncPayload} 同一套思路）：对不上就当空候选忽略，绝不断开连接。 */
    private static final int MAGIC = 0x4D494E32;
    private static final int MAX_IDS = 1024;
    private static final int MAX_ID_CHARS = 256;

    public ShowSelectedMapUIPayload(FriendlyByteBuf buffer) {
        this(readIds(buffer));
    }

    /** 由服务端候选条目构造。 */
    public static ShowSelectedMapUIPayload ofCandidates(List<MapConfig.MapEntry> entries) {
        List<String> ids = new ArrayList<>();
        if (entries != null) {
            for (MapConfig.MapEntry entry : entries) {
                if (entry != null && entry.id != null && !entry.id.isBlank()) {
                    ids.add(entry.id);
                }
            }
        }
        return new ShowSelectedMapUIPayload(ids);
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeInt(MAGIC);
        List<String> safeIds = candidateIds == null ? List.of() : candidateIds;
        buffer.writeVarInt(safeIds.size());
        for (String id : safeIds) {
            String safeId = id == null ? "" : id;
            buffer.writeUtf(safeId.length() <= MAX_ID_CHARS ? safeId : safeId.substring(0, MAX_ID_CHARS),
                    MAX_ID_CHARS);
        }
    }

    private static List<String> readIds(FriendlyByteBuf buffer) {
        try {
            // 旧服务端这里塞的是整份 MapConfig 的 JSON 字符串：魔数对不上就忽略（空候选），不抛异常
            if (buffer.readableBytes() < 4 || buffer.readInt() != MAGIC) {
                return List.of();
            }
            int size = Math.min(MAX_IDS, Math.max(0, buffer.readVarInt()));
            List<String> ids = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                if (buffer.readableBytes() <= 0) {
                    break;
                }
                String id = buffer.readUtf(MAX_ID_CHARS);
                if (!id.isBlank()) {
                    ids.add(id);
                }
            }
            return List.copyOf(ids);
        } catch (Exception e) {
            SRE.LOGGER.warn("Ignoring incompatible/truncated selected-map payload (server format mismatch?)", e);
            return List.of();
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
