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

/**
 * 抽奖结果包
 * @param ansID 抽奖结果 ID
 * <p>
 * 客户端根据结果生成动画
 * </p>
 */
public record LootResultS2CPacket(int poolID, int quality, int ansID) implements CustomPacketPayload {
    public static final ResourceLocation LOOT_RESULT_PAYLOAD_ID =
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "loot_result");
    public static final Type<LootResultS2CPacket> ID = new Type<>(LOOT_RESULT_PAYLOAD_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, LootResultS2CPacket> CODEC;
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeInt(poolID);
        buf.writeInt(quality);
        buf.writeInt(ansID);
    }

    public static LootResultS2CPacket read(FriendlyByteBuf buf) {
        return new LootResultS2CPacket(
                buf.readInt(),
                buf.readInt(),
                buf.readInt()
        );
    }
    static {
        CODEC = StreamCodec.ofMember(LootResultS2CPacket::write, LootResultS2CPacket::read);
    }
}
