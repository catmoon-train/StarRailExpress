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
import io.wifi.starrailexpress.morph.MorphAppearance;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 变形外观同步包（S2C）。
 * {@code fullSync=true} 时客户端先清空再应用；增量更新中 {@link MorphAppearance#NONE} 表示解除变形。
 */
public record MorphSyncPayload(boolean fullSync, Map<UUID, MorphAppearance> entries) implements CustomPacketPayload {
    public static final Type<MorphSyncPayload> ID = new Type<>(SRE.id("morph_appearance_sync"));
    public static final StreamCodec<FriendlyByteBuf, MorphSyncPayload> CODEC = StreamCodec
            .ofMember(MorphSyncPayload::encode, MorphSyncPayload::decode);

    public static MorphSyncPayload full(Map<UUID, MorphAppearance> entries) {
        return new MorphSyncPayload(true, entries);
    }

    public static MorphSyncPayload incremental(Map<UUID, MorphAppearance> entries) {
        return new MorphSyncPayload(false, entries);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(fullSync);
        buf.writeVarInt(entries.size());
        for (Map.Entry<UUID, MorphAppearance> entry : entries.entrySet()) {
            buf.writeUUID(entry.getKey());
            MorphAppearance appearance = entry.getValue() == null ? MorphAppearance.NONE : entry.getValue();
            appearance.write(buf);
        }
    }

    public static MorphSyncPayload decode(FriendlyByteBuf buf) {
        boolean full = buf.readBoolean();
        int size = buf.readVarInt();
        Map<UUID, MorphAppearance> entries = new HashMap<>();
        for (int i = 0; i < size; i++) {
            UUID uuid = buf.readUUID();
            entries.put(uuid, MorphAppearance.read(buf));
        }
        return new MorphSyncPayload(full, entries);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
