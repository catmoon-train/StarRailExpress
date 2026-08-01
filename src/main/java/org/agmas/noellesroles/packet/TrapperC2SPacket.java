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

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

/**
 * 设陷者技能网络包
 * 用于客户端向服务端发送放置陷阱请求
 */
public record TrapperC2SPacket() implements CustomPacketPayload {
    
    public static final Type<TrapperC2SPacket> ID = new Type<>(
        ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "trapper_ability")
    );
    
    public static final StreamCodec<RegistryFriendlyByteBuf, TrapperC2SPacket> CODEC = StreamCodec.ofMember(
        (packet, buf) -> {
            // 无需写入数据，只是触发技能
        },
        buf -> new TrapperC2SPacket()
    );
    
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}