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
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

/**
 * 跟踪者突进网络包
 * 客户端 -> 服务端
 * 
 * 用于控制三阶段的蓄力突进
 * - charging=true: 开始蓄力
 * - charging=false: 释放突进
 */
public record StalkerDashC2SPacket(boolean charging) implements CustomPacketPayload {

    public static final Type<StalkerDashC2SPacket> ID = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "stalker_dash"));

    public static final StreamCodec<RegistryFriendlyByteBuf, StalkerDashC2SPacket> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, StalkerDashC2SPacket::charging,
            StalkerDashC2SPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}