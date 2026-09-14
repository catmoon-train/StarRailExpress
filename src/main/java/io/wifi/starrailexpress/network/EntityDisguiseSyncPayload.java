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
import io.wifi.starrailexpress.disguise.EntityDisguiseState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 实体伪装状态同步包（S2C）。
 * {@code fullSync=true} 时客户端先清空再应用；增量更新中
 * {@link EntityDisguiseState#NONE} 表示解除该玩家的伪装。
 * <p>
 * 编码带**状态调色板**：相同的状态（含「解除」）只写一次，每条记录只花一个 UUID + 一个索引。
 * 批量伪装（一局里一堆人同时变成同一种怪）时，体积几乎只剩 UUID；外观 NBT 越大收益越明显。
 * <p>
 * 服务端把同一 tick 内的变更合并成一个包发出（见 {@code EntityDisguiseManager#flush}），
 * 所以 16 人同时伪装是 1 个包 × 16 个收件人，而不是 16 个包 × 16 个收件人。
 */
public record EntityDisguiseSyncPayload(boolean fullSync, Map<UUID, EntityDisguiseState> entries)
        implements CustomPacketPayload {

    public static final Type<EntityDisguiseSyncPayload> ID = new Type<>(SRE.id("entity_disguise_sync"));
    public static final StreamCodec<FriendlyByteBuf, EntityDisguiseSyncPayload> CODEC = StreamCodec
            .ofMember(EntityDisguiseSyncPayload::encode, EntityDisguiseSyncPayload::decode);

    public static EntityDisguiseSyncPayload full(Map<UUID, EntityDisguiseState> entries) {
        return new EntityDisguiseSyncPayload(true, entries);
    }

    public static EntityDisguiseSyncPayload incremental(Map<UUID, EntityDisguiseState> entries) {
        return new EntityDisguiseSyncPayload(false, entries);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(fullSync);
        Map<EntityDisguiseState, Integer> palette = new HashMap<>();
        List<EntityDisguiseState> unique = new ArrayList<>();
        for (EntityDisguiseState state : entries.values()) {
            EntityDisguiseState key = state == null ? EntityDisguiseState.NONE : state;
            if (palette.putIfAbsent(key, unique.size()) == null) {
                unique.add(key);
            }
        }
        buf.writeVarInt(unique.size());
        for (EntityDisguiseState state : unique) {
            state.write(buf);
        }
        buf.writeVarInt(entries.size());
        for (Map.Entry<UUID, EntityDisguiseState> entry : entries.entrySet()) {
            buf.writeUUID(entry.getKey());
            EntityDisguiseState key = entry.getValue() == null ? EntityDisguiseState.NONE : entry.getValue();
            buf.writeVarInt(palette.get(key));
        }
    }

    public static EntityDisguiseSyncPayload decode(FriendlyByteBuf buf) {
        boolean full = buf.readBoolean();
        int paletteSize = buf.readVarInt();
        List<EntityDisguiseState> palette = new ArrayList<>(Math.max(paletteSize, 0));
        for (int i = 0; i < paletteSize; i++) {
            palette.add(EntityDisguiseState.read(buf));
        }
        int size = buf.readVarInt();
        Map<UUID, EntityDisguiseState> entries = new HashMap<>();
        for (int i = 0; i < size; i++) {
            UUID uuid = buf.readUUID();
            int index = buf.readVarInt();
            EntityDisguiseState state = index >= 0 && index < palette.size()
                    ? palette.get(index)
                    : EntityDisguiseState.NONE;
            entries.put(uuid, state);
        }
        return new EntityDisguiseSyncPayload(full, entries);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
