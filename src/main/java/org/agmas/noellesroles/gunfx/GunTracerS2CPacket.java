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

package org.agmas.noellesroles.gunfx;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.agmas.noellesroles.Noellesroles;

/**
 * 服务端→客户端：一次枪械射击的弹道轨迹（射手实体 id + 终点坐标），
 * 客户端由 {@link GunTracerRenderer} 渲染渐隐轨迹线。
 */
public record GunTracerS2CPacket(int shooterId, double toX, double toY, double toZ)
        implements CustomPacketPayload {
    public static final Type<GunTracerS2CPacket> ID = new Type<>(Noellesroles.id("gun_tracer"));
    public static final StreamCodec<RegistryFriendlyByteBuf, GunTracerS2CPacket> CODEC =
            StreamCodec.ofMember(GunTracerS2CPacket::encode, GunTracerS2CPacket::decode);

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(shooterId);
        buf.writeDouble(toX);
        buf.writeDouble(toY);
        buf.writeDouble(toZ);
    }

    public static GunTracerS2CPacket decode(RegistryFriendlyByteBuf buf) {
        return new GunTracerS2CPacket(buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
