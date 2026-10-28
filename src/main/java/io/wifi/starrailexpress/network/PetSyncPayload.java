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
 * 随从装备同步包（S2C）。
 * <p>
 * 皮肤组件 {@code SREPlayerSkinsComponent} 只同步给玩家本人，其他客户端无从得知
 * 某位玩家装备了哪个随从、是否愿意公开，因此本包负责补齐这份「他人可见」的信息。
 * 与帽子同步（{@link HatEquipmentSyncPayload}）同款语义：
 * <ul>
 * <li>{@code fullSync = true}：客户端先清空缓存，再应用 {@code entries}；</li>
 * <li>{@code fullSync = false}：增量更新，只覆盖 {@code entries} 里出现的玩家。</li>
 * </ul>
 * 每个条目只在「装备了随从」时才出现——未装备等于默认状态，客户端清空后即为默认，
 * 因此无人装备随从时连包都不用发。
 */
public record PetSyncPayload(boolean fullSync, Map<UUID, Entry> entries) implements CustomPacketPayload {

    /**
     * 一名玩家的随从外观状态。
     *
     * @param skinId 装备的随从皮肤 ID；{@link #NONE} 表示未装备
     * @param shared 主人是否勾选了「展示给其他玩家」
     */
    public record Entry(String skinId, boolean shared) {
        /** 未装备随从。 */
        public static final String NONE = "";

        public static Entry none() {
            return new Entry(NONE, false);
        }

        /** 是否真的装备了随从。 */
        public boolean hasPet() {
            return skinId != null && !skinId.isBlank();
        }
    }

    public static final Type<PetSyncPayload> ID = new Type<>(SRE.id("pet_sync"));
    public static final StreamCodec<FriendlyByteBuf, PetSyncPayload> CODEC = StreamCodec
            .ofMember(PetSyncPayload::encode, PetSyncPayload::decode);

    public static PetSyncPayload full(Map<UUID, Entry> entries) {
        return new PetSyncPayload(true, entries);
    }

    public static PetSyncPayload incremental(Map<UUID, Entry> entries) {
        return new PetSyncPayload(false, entries);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(fullSync);
        buf.writeVarInt(entries.size());
        for (Map.Entry<UUID, Entry> entry : entries.entrySet()) {
            buf.writeUUID(entry.getKey());
            Entry value = entry.getValue() == null ? Entry.none() : entry.getValue();
            buf.writeUtf(value.skinId() == null ? Entry.NONE : value.skinId(), 128);
            buf.writeBoolean(value.shared());
        }
    }

    public static PetSyncPayload decode(FriendlyByteBuf buf) {
        boolean full = buf.readBoolean();
        int size = buf.readVarInt();
        Map<UUID, Entry> entries = new HashMap<>(Math.max(4, size));
        for (int i = 0; i < size; i++) {
            UUID uuid = buf.readUUID();
            String skinId = buf.readUtf(128);
            boolean shared = buf.readBoolean();
            entries.put(uuid, new Entry(skinId, shared));
        }
        return new PetSyncPayload(full, entries);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
