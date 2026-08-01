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

package net.exmo.sre.repair.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.agmas.noellesroles.Noellesroles;

public record OpenRepairStationScreenS2CPacket(BlockPos blockPos) implements CustomPacketPayload {
    public static final Type<OpenRepairStationScreenS2CPacket> ID = new Type<>(Noellesroles.id("open_repair_station"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenRepairStationScreenS2CPacket> CODEC = StreamCodec
            .ofMember(OpenRepairStationScreenS2CPacket::encode, OpenRepairStationScreenS2CPacket::decode);

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(blockPos);
    }

    public static OpenRepairStationScreenS2CPacket decode(RegistryFriendlyByteBuf buf) {
        return new OpenRepairStationScreenS2CPacket(buf.readBlockPos());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
