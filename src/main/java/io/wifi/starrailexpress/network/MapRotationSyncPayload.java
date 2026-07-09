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
