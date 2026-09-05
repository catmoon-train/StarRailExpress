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

import java.util.UUID;

/**
 * 大侦探「目标情况」请求包：客户端在推理之书上选择查明方位或生死时发送。
 */
public record GreatDetectiveRevealC2SPacket(UUID killer, byte mode) implements CustomPacketPayload {

    public static final byte MODE_DISTANCE = 0;
    public static final byte MODE_VITAL = 1;

    public static final ResourceLocation PAYLOAD_ID = ResourceLocation.fromNamespaceAndPath(
            Noellesroles.MOD_ID, "great_detective_reveal");
    public static final Type<GreatDetectiveRevealC2SPacket> ID = new Type<>(PAYLOAD_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, GreatDetectiveRevealC2SPacket> CODEC =
            StreamCodec.ofMember(
                    (packet, buf) -> {
                        buf.writeUUID(packet.killer());
                        buf.writeByte(packet.mode());
                    },
                    buf -> new GreatDetectiveRevealC2SPacket(buf.readUUID(), buf.readByte()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
