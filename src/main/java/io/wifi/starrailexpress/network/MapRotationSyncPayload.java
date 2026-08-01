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
 * S2C：地图轮换的启用状态。切换后广播给所有玩家，只带 id + 开关，
 * 不重发体积很大的 {@link MapIntroSyncPayload}。
 */
public record MapRotationSyncPayload(List<Entry> entries) implements CustomPacketPayload {
    public static final Type<MapRotationSyncPayload> ID = new Type<>(SRE.id("map_rotation_sync"));
    public static final StreamCodec<FriendlyByteBuf, MapRotationSyncPayload> CODEC =
            CustomPacketPayload.codec(MapRotationSyncPayload::write, MapRotationSyncPayload::new);

    public record Entry(String id, boolean enabled) {
        private static Entry read(FriendlyByteBuf buffer) {
            return new Entry(buffer.readUtf(256), buffer.readBoolean());
        }

        private void write(FriendlyByteBuf buffer) {
            buffer.writeUtf(id == null ? "" : id, 256);
            buffer.writeBoolean(enabled);
        }
    }

    private MapRotationSyncPayload(FriendlyByteBuf buffer) {
        this(readEntries(buffer));
    }

    private static List<Entry> readEntries(FriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        List<Entry> result = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            result.add(Entry.read(buffer));
        }
        return result;
    }

    private void write(FriendlyByteBuf buffer) {
        List<Entry> safe = entries == null ? List.of() : entries;
        buffer.writeVarInt(safe.size());
        for (Entry entry : safe) {
            entry.write(buffer);
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
