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

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.agmas.noellesroles.Noellesroles;

import java.util.UUID;

public record MediumSeanceOpenS2CPacket(UUID mediumId, String mediumName, long sessionEndTick)
        implements CustomPacketPayload {

    public static final Type<MediumSeanceOpenS2CPacket> ID = new Type<>(Noellesroles.id("medium_seance_open"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MediumSeanceOpenS2CPacket> CODEC = StreamCodec
            .ofMember(MediumSeanceOpenS2CPacket::encode, MediumSeanceOpenS2CPacket::decode);

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeUUID(this.mediumId);
        buf.writeUtf(this.mediumName);
        buf.writeLong(this.sessionEndTick);
    }

    public static MediumSeanceOpenS2CPacket decode(RegistryFriendlyByteBuf buf) {
        return new MediumSeanceOpenS2CPacket(buf.readUUID(), buf.readUtf(), buf.readLong());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
