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
 * 服务端向客户端广播「当前应当播放哪个时刻的音乐」。
 *
 * <p>
 * {@code ordinal < 0} 表示当前没有任何时刻音乐（应停止）。由 {@code MomentManager}
 * 在时刻开始/结束、亡命徒插队/恢复、玩家加入时下发，客户端 {@code MomentMusicClient}
 * 据此循环播放唯一一条时刻音乐，实现跨时刻的音乐优先级与「时刻结束立即停播」。
 * </p>
 */
public record MomentMusicS2CPacket(int ordinal) implements CustomPacketPayload {
    public static final Type<MomentMusicS2CPacket> ID = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "moment_music"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MomentMusicS2CPacket> CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeVarInt(payload.ordinal),
            buf -> new MomentMusicS2CPacket(buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
