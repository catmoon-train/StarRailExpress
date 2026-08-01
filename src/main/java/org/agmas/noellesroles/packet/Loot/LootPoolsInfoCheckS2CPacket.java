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

import java.util.ArrayList;
import java.util.List;

/**
 * 抽奖信息页发包
 * <p>
 *     NOTE:
 *      服务器有轮换卡池、up卡池，需要将卡池信息发给客户端用于显示
 * </p>
 */
public record LootPoolsInfoCheckS2CPacket(List<Integer> poolIDs) implements CustomPacketPayload {
    public static ResourceLocation LOOT_POOLS_INFO_CHECK_PAYLOAD_ID =
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "loot_check_pools");
    public static final Type<LootPoolsInfoCheckS2CPacket> ID = new Type<>(LOOT_POOLS_INFO_CHECK_PAYLOAD_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, LootPoolsInfoCheckS2CPacket> CODEC;

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeInt(poolIDs.size());
        for (Integer poolID : poolIDs) {
            buf.writeInt(poolID);
        }
    }

    public static LootPoolsInfoCheckS2CPacket read(RegistryFriendlyByteBuf buf) {
        List<Integer> poolIDs = new ArrayList<>();
        int poolSize = buf.readInt();
        for (int i = 0; i < poolSize; ++i) {
            poolIDs.add(buf.readInt());
        }
        return new LootPoolsInfoCheckS2CPacket(
                poolIDs
        );
    }
    static {
        CODEC = StreamCodec.ofMember(LootPoolsInfoCheckS2CPacket::write, LootPoolsInfoCheckS2CPacket::read);
    }
}
