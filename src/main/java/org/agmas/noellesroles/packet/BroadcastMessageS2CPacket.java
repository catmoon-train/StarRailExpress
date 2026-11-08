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
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

/**
 * 顶部广播消息。
 *
 * @param content       正文
 * @param overlay       兼容字段（当前恒为 true，见 {@link #overlay()}）
 * @param durationTicks 显示时长（tick）。&lt;= 0 表示用客户端配置的默认时长；
 *                      职业入场播报会传入安全时间剩余时长，让播报一直留到安全时间结束。
 */
public record BroadcastMessageS2CPacket(Component content, boolean overlay, int durationTicks)
        implements CustomPacketPayload {
    public static final ResourceLocation BROADCAST_MESSAGE_PAYLOAD_ID = ResourceLocation
            .fromNamespaceAndPath(Noellesroles.MOD_ID, "broadcast_message");
    public static final Type<BroadcastMessageS2CPacket> ID = new Type<>(BROADCAST_MESSAGE_PAYLOAD_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, BroadcastMessageS2CPacket> CODEC = StreamCodec.composite(
            ComponentSerialization.TRUSTED_STREAM_CODEC, BroadcastMessageS2CPacket::content,
            ByteBufCodecs.BOOL, BroadcastMessageS2CPacket::overlay,
            ByteBufCodecs.VAR_INT, BroadcastMessageS2CPacket::durationTicks,
            BroadcastMessageS2CPacket::new);

    public Component content() {
        return this.content;
    }

    public boolean overlay() {
        return true;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    /** 使用客户端配置的默认显示时长。 */
    public BroadcastMessageS2CPacket(Component content) {
        this(content, true, 0);
    }

    /** 使用客户端配置的默认显示时长。 */
    public BroadcastMessageS2CPacket(Component content, boolean overlay) {
        this(content, overlay, 0);
    }
}