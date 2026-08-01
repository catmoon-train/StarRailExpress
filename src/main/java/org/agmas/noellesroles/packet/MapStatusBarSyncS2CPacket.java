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

package org.agmas.noellesroles.packet;

import io.wifi.starrailexpress.game.data.MapStatusBarType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.agmas.noellesroles.Noellesroles;

public record MapStatusBarSyncS2CPacket(MapStatusBarType barType, int value, int maxValue)
        implements CustomPacketPayload {
    public static final Type<MapStatusBarSyncS2CPacket> ID = new Type<>(Noellesroles.id("map_status_bar_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MapStatusBarSyncS2CPacket> CODEC = StreamCodec
            .ofMember(MapStatusBarSyncS2CPacket::encode, MapStatusBarSyncS2CPacket::decode);

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeUtf((barType == null ? MapStatusBarType.NONE : barType).name());
        buf.writeVarInt(value);
        buf.writeVarInt(maxValue);
    }

    public static MapStatusBarSyncS2CPacket decode(RegistryFriendlyByteBuf buf) {
        return new MapStatusBarSyncS2CPacket(MapStatusBarType.byName(buf.readUtf()), buf.readVarInt(), buf.readVarInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
