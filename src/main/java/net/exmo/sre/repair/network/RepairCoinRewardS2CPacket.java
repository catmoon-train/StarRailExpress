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

public record RepairCoinRewardS2CPacket(int amount, String sourceKey) implements CustomPacketPayload {
    public static final Type<RepairCoinRewardS2CPacket> ID = new Type<>(Noellesroles.id("repair_coin_reward"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RepairCoinRewardS2CPacket> CODEC = StreamCodec
            .ofMember(RepairCoinRewardS2CPacket::encode, RepairCoinRewardS2CPacket::decode);

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(amount);
        buf.writeUtf(sourceKey);
    }

    public static RepairCoinRewardS2CPacket decode(RegistryFriendlyByteBuf buf) {
        return new RepairCoinRewardS2CPacket(buf.readVarInt(), buf.readUtf());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
