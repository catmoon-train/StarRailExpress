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
 * 服务端→客户端：一次枪械射击的弹道轨迹（射手实体 id + 定格起终点），
 * 客户端由 {@link GunTracerRenderer} 渲染渐隐轨迹线。
 */
public record GunTracerS2CPacket(int shooterId,
        double fromX, double fromY, double fromZ,
        double toX, double toY, double toZ,
        int style)
        implements CustomPacketPayload {
    /** 默认样式：琥珀色轨迹线。 */
    public static final int STYLE_DEFAULT = 0;
    /** 狙击枪样式：轨迹线之外，客户端再沿弹道生成烟雾。 */
    public static final int STYLE_SNIPER = 1;

    public static final Type<GunTracerS2CPacket> ID = new Type<>(Noellesroles.id("gun_tracer"));
    public static final StreamCodec<RegistryFriendlyByteBuf, GunTracerS2CPacket> CODEC =
            StreamCodec.ofMember(GunTracerS2CPacket::encode, GunTracerS2CPacket::decode);

    /** 默认样式（琥珀色轨迹线）的兼容构造。 */
    public GunTracerS2CPacket(int shooterId,
            double fromX, double fromY, double fromZ,
            double toX, double toY, double toZ) {
        this(shooterId, fromX, fromY, fromZ, toX, toY, toZ, STYLE_DEFAULT);
    }

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(shooterId);
        buf.writeDouble(fromX);
        buf.writeDouble(fromY);
        buf.writeDouble(fromZ);
        buf.writeDouble(toX);
        buf.writeDouble(toY);
        buf.writeDouble(toZ);
        buf.writeVarInt(style);
    }

    public static GunTracerS2CPacket decode(RegistryFriendlyByteBuf buf) {
        return new GunTracerS2CPacket(buf.readVarInt(),
                buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readVarInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
