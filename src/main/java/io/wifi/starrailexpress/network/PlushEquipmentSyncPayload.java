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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 身份玩偶同步包（S2C）。与帽子装备同步同款：按玩家 UUID 广播当前应显示的玩偶编码。
 */
public record PlushEquipmentSyncPayload(boolean fullSync, Map<UUID, String> entries) implements CustomPacketPayload {
    public static final Type<PlushEquipmentSyncPayload> ID = new Type<>(SRE.id("plush_equipment_sync"));
    public static final StreamCodec<FriendlyByteBuf, PlushEquipmentSyncPayload> CODEC = StreamCodec
            .ofMember(PlushEquipmentSyncPayload::encode, PlushEquipmentSyncPayload::decode);

    public static PlushEquipmentSyncPayload full(Map<UUID, String> entries) {
        return new PlushEquipmentSyncPayload(true, entries);
    }

    public static PlushEquipmentSyncPayload incremental(Map<UUID, String> entries) {
        return new PlushEquipmentSyncPayload(false, entries);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(fullSync);
        buf.writeVarInt(entries.size());
        for (Map.Entry<UUID, String> entry : entries.entrySet()) {
            buf.writeUUID(entry.getKey());
            buf.writeUtf(entry.getValue() == null ? "" : entry.getValue(), 256);
        }
    }

    public static PlushEquipmentSyncPayload decode(FriendlyByteBuf buf) {
        boolean full = buf.readBoolean();
        int size = buf.readVarInt();
        Map<UUID, String> entries = new HashMap<>();
        for (int i = 0; i < size; i++) {
            UUID uuid = buf.readUUID();
            entries.put(uuid, buf.readUtf(256));
        }
        return new PlushEquipmentSyncPayload(full, entries);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
