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

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

import java.util.ArrayList;
import java.util.List;

/** 船长舱门调度：服务端下发可选的其他房间门列表。 */
public record ConductorDoorListS2CPacket(List<DoorEntry> doors) implements CustomPacketPayload {
    public record DoorEntry(BlockPos pos, String name) {
    }

    public static final ResourceLocation PAYLOAD_ID =
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "conductor_door_list");
    public static final Type<ConductorDoorListS2CPacket> ID = new Type<>(PAYLOAD_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, ConductorDoorListS2CPacket> CODEC;

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(doors.size());
        for (DoorEntry door : doors) {
            buf.writeBlockPos(door.pos());
            buf.writeUtf(door.name());
        }
    }

    public static ConductorDoorListS2CPacket read(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<DoorEntry> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(new DoorEntry(buf.readBlockPos(), buf.readUtf()));
        }
        return new ConductorDoorListS2CPacket(list);
    }

    static {
        CODEC = StreamCodec.ofMember(ConductorDoorListS2CPacket::write, ConductorDoorListS2CPacket::read);
    }
}
