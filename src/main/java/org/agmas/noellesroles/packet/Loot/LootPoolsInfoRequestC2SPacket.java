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

package org.agmas.noellesroles.packet.Loot;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

import java.util.List;

public record LootPoolsInfoRequestC2SPacket(List<Integer> poolIds) implements CustomPacketPayload {
    public static final ResourceLocation LOOT_POOLS_INFO_REQUEST_PAYLOAD_ID =
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "loot_pools_info_request");
    public static final Type<LootPoolsInfoRequestC2SPacket> ID = new CustomPacketPayload.Type<>(LOOT_POOLS_INFO_REQUEST_PAYLOAD_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, LootPoolsInfoRequestC2SPacket> CODEC;
    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeCollection(poolIds, FriendlyByteBuf::writeInt);
    }

    public static LootPoolsInfoRequestC2SPacket read(FriendlyByteBuf buf) {
        return new LootPoolsInfoRequestC2SPacket(
                buf.readList(FriendlyByteBuf::readInt)
        );
    }
    static {
        CODEC = StreamCodec.ofMember(LootPoolsInfoRequestC2SPacket::write, LootPoolsInfoRequestC2SPacket::read);
    }
}
