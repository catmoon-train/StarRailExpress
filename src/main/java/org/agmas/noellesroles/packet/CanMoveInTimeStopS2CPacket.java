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
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record CanMoveInTimeStopS2CPacket(List<UUID> uuids,int times) implements CustomPacketPayload {
    public static final ResourceLocation PACKET_ID =
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "can_move_in_time_stop");
    public static final Type<CanMoveInTimeStopS2CPacket> ID = new Type<>(PACKET_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, CanMoveInTimeStopS2CPacket> CODEC;

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeCollection(uuids, RegistryFriendlyByteBuf::writeUUID);
        buf.writeInt(times);
    }

    public static CanMoveInTimeStopS2CPacket read(RegistryFriendlyByteBuf buf) {
        return new CanMoveInTimeStopS2CPacket(
                buf.readCollection(ArrayList::new, RegistryFriendlyByteBuf::readUUID)
                ,buf.readInt()
        );
    }

    static {
        CODEC = StreamCodec.ofMember(CanMoveInTimeStopS2CPacket::write, CanMoveInTimeStopS2CPacket::read);
    }
}
