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
 * 明星技能网络包
 * 客户端 -> 服务端
 * 
 * 当玩家按下技能键时发送，请求激活"聚光灯"技能
 * 让10格范围内的玩家视野都看向明星
 */
public record StarAbilityC2SPacket() implements CustomPacketPayload {
    
    public static final Type<StarAbilityC2SPacket> ID = new Type<>(
        ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "star_ability")
    );
    
    public static final StreamCodec<RegistryFriendlyByteBuf, StarAbilityC2SPacket> CODEC = StreamCodec.unit(new StarAbilityC2SPacket());
    
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}