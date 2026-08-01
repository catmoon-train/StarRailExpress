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
 * 客户端的抽卡信息发包
 * <p>
 *  - 向服务器请求抽卡信息比对
 * </p>
 */
public record LootPoolsInfoCheckC2SPacket() implements CustomPacketPayload {
    public static final ResourceLocation LOOT_POOLS_INFO_CHECK_CLIENT_PAYLOAD_ID =
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "loot_pools_info_check_client");
    public static final Type<LootPoolsInfoCheckC2SPacket> ID = new CustomPacketPayload.Type<>(LOOT_POOLS_INFO_CHECK_CLIENT_PAYLOAD_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, LootPoolsInfoCheckC2SPacket> CODEC;
    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public void write(FriendlyByteBuf buf) {
    }

    public static LootPoolsInfoCheckC2SPacket read(FriendlyByteBuf buf) {
        return new LootPoolsInfoCheckC2SPacket(
        );
    }
    static {
        CODEC = StreamCodec.ofMember(LootPoolsInfoCheckC2SPacket::write, LootPoolsInfoCheckC2SPacket::read);
    }
}
