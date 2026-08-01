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

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * 监察员标记目标数据包 (客户端 -> 服务端)
 */
public record MonitorMarkC2SPacket(UUID target) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<MonitorMarkC2SPacket> ID = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "monitor_mark"));

    public static final StreamCodec<FriendlyByteBuf, MonitorMarkC2SPacket> CODEC = CustomPacketPayload.codec(
            MonitorMarkC2SPacket::write,
            MonitorMarkC2SPacket::new);

    public MonitorMarkC2SPacket(FriendlyByteBuf buf) {
        this(buf.readUUID());
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(this.target);
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}