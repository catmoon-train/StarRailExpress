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

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.agmas.noellesroles.Noellesroles;

import java.util.ArrayList;
import java.util.List;

public record OpenRepairRoleSelectionS2CPacket(String faction, long endTick, List<String> playerNames)
        implements CustomPacketPayload {
    public static final Type<OpenRepairRoleSelectionS2CPacket> ID = new Type<>(Noellesroles.id("open_repair_role_selection"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenRepairRoleSelectionS2CPacket> CODEC = StreamCodec
            .ofMember(OpenRepairRoleSelectionS2CPacket::encode, OpenRepairRoleSelectionS2CPacket::decode);

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeUtf(faction);
        buf.writeLong(endTick);
        buf.writeVarInt(playerNames.size());
        playerNames.forEach(buf::writeUtf);
    }

    public static OpenRepairRoleSelectionS2CPacket decode(RegistryFriendlyByteBuf buf) {
        String faction = buf.readUtf();
        long endTick = buf.readLong();
        int size = buf.readVarInt();
        List<String> names = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            names.add(buf.readUtf());
        }
        return new OpenRepairRoleSelectionS2CPacket(faction, endTick, names);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
