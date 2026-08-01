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

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

public record LootDataRefreshS2CPacket(int coinNumber, int lootChance) implements CustomPacketPayload {
    public static ResourceLocation LOOT_DATA_REFRESH_PAYLOAD_ID =
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "loot_data_refresh");
    public static final Type<LootDataRefreshS2CPacket> ID = new Type<>(LOOT_DATA_REFRESH_PAYLOAD_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, LootDataRefreshS2CPacket> CODEC;

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeInt(coinNumber);
        buf.writeInt(lootChance);
    }

    public static LootDataRefreshS2CPacket read(RegistryFriendlyByteBuf buf) {
        return new LootDataRefreshS2CPacket(buf.readInt(), buf.readInt());
    }
    static {
        CODEC = StreamCodec.ofMember(LootDataRefreshS2CPacket::write, LootDataRefreshS2CPacket::read);
    }

}
