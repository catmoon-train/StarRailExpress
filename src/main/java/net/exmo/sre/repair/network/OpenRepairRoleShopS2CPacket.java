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

public record OpenRepairRoleShopS2CPacket(int skinCoins, List<String> ownedRoles) implements CustomPacketPayload {
    public static final Type<OpenRepairRoleShopS2CPacket> ID = new Type<>(Noellesroles.id("open_repair_role_shop"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenRepairRoleShopS2CPacket> CODEC = StreamCodec
            .ofMember(OpenRepairRoleShopS2CPacket::encode, OpenRepairRoleShopS2CPacket::decode);

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(skinCoins);
        buf.writeVarInt(ownedRoles.size());
        ownedRoles.forEach(buf::writeUtf);
    }

    public static OpenRepairRoleShopS2CPacket decode(RegistryFriendlyByteBuf buf) {
        int coins = buf.readVarInt();
        int size = buf.readVarInt();
        List<String> owned = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            owned.add(buf.readUtf());
        }
        return new OpenRepairRoleShopS2CPacket(coins, owned);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
