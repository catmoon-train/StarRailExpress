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
 * 帽子装备同步包（S2C）。
 * <p>
 * 服务器向所有客户端广播每个玩家当前装备的帽子皮肤名称（皮肤类型 "hat"），
 * 使所有客户端都能渲染其他玩家的帽子。{@code fullSync=true} 时客户端应先清空缓存再应用；
 * 否则为增量更新（skinName 为 "default" 或空时表示该玩家未装备帽子，客户端移除对应条目）。
 */
public record HatEquipmentSyncPayload(boolean fullSync, Map<UUID, String> entries) implements CustomPacketPayload {
    public static final Type<HatEquipmentSyncPayload> ID = new Type<>(SRE.id("hat_equipment_sync"));
    public static final StreamCodec<FriendlyByteBuf, HatEquipmentSyncPayload> CODEC = StreamCodec
            .ofMember(HatEquipmentSyncPayload::encode, HatEquipmentSyncPayload::decode);

    public static HatEquipmentSyncPayload full(Map<UUID, String> entries) {
        return new HatEquipmentSyncPayload(true, entries);
    }

    public static HatEquipmentSyncPayload incremental(Map<UUID, String> entries) {
        return new HatEquipmentSyncPayload(false, entries);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(fullSync);
        buf.writeVarInt(entries.size());
        for (Map.Entry<UUID, String> entry : entries.entrySet()) {
            buf.writeUUID(entry.getKey());
            buf.writeUtf(entry.getValue() == null ? "" : entry.getValue(), 256);
        }
    }

    public static HatEquipmentSyncPayload decode(FriendlyByteBuf buf) {
        boolean full = buf.readBoolean();
        int size = buf.readVarInt();
        Map<UUID, String> entries = new HashMap<>();
        for (int i = 0; i < size; i++) {
            UUID uuid = buf.readUUID();
            String skinName = buf.readUtf(256);
            entries.put(uuid, skinName);
        }
        return new HatEquipmentSyncPayload(full, entries);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
