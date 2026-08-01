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

import java.util.UUID;

public record PlayerDataPartSyncPayload(UUID playerUuid, String part, String json, long updatedAt)
        implements CustomPacketPayload {
    public static final Type<PlayerDataPartSyncPayload> ID = new Type<>(SRE.id("player_data_part_sync"));
    public static final StreamCodec<FriendlyByteBuf, PlayerDataPartSyncPayload> CODEC =
            CustomPacketPayload.codec(PlayerDataPartSyncPayload::write, PlayerDataPartSyncPayload::new);

    private PlayerDataPartSyncPayload(FriendlyByteBuf buffer) {
        this(buffer.readUUID(), buffer.readUtf(64), buffer.readUtf(1_048_576), buffer.readVarLong());
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeUUID(playerUuid);
        buffer.writeUtf(part, 64);
        buffer.writeUtf(json, 1_048_576);
        buffer.writeVarLong(updatedAt);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
