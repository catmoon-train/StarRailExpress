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
 
public record ShowStatsPayload(UUID targetPlayerUuid) implements CustomPacketPayload {
    public static final Type<ShowStatsPayload> ID = new Type<>(SRE.id("show_stats"));
    public static final StreamCodec<FriendlyByteBuf, ShowStatsPayload> CODEC = CustomPacketPayload.codec(ShowStatsPayload::write, ShowStatsPayload::new);
 
    public ShowStatsPayload(FriendlyByteBuf buf) {
        this(buf.readUUID());
    }
 
    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(targetPlayerUuid);
    }
 
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
