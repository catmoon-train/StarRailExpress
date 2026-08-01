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
 * 全服「阿蒙时刻」终幕状态广播。
 * active=true 开启终幕表现（偏灰滤镜、小丑音乐、全局状态栏倒计时）；false 关闭。
 */
public record AmonFinaleS2CPacket(boolean active) implements CustomPacketPayload {
    public static final Type<AmonFinaleS2CPacket> ID = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "amon_finale"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AmonFinaleS2CPacket> CODEC = StreamCodec.of(
            (buf, p) -> buf.writeBoolean(p.active),
            buf -> new AmonFinaleS2CPacket(buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
