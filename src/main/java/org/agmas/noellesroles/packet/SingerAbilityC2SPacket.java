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
 * 歌手技能网络包
 * 客户端 -> 服务端
 * 
 * 当玩家按下技能键时发送，请求播放随机音乐
 */
public record SingerAbilityC2SPacket() implements CustomPacketPayload {
    
    public static final Type<SingerAbilityC2SPacket> ID = new Type<>(
        ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "singer_ability")
    );
    
    public static final StreamCodec<RegistryFriendlyByteBuf, SingerAbilityC2SPacket> CODEC = StreamCodec.unit(new SingerAbilityC2SPacket());
    
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}