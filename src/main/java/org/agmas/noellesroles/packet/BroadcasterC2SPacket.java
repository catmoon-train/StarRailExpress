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

public record BroadcasterC2SPacket(String message, boolean onlySave) implements CustomPacketPayload {
    public static final ResourceLocation BROADCASTER_PAYLOAD_ID = ResourceLocation
            .fromNamespaceAndPath(Noellesroles.MOD_ID, "broadcaster");
    public static final Type<BroadcasterC2SPacket> ID = new Type<>(BROADCASTER_PAYLOAD_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, BroadcasterC2SPacket> CODEC = StreamCodec.ofMember(
            (packet, buf) -> {
                buf.writeUtf(packet.message());
                buf.writeBoolean(packet.onlySave());
            },
            buf -> new BroadcasterC2SPacket(buf.readUtf(), buf.readBoolean()));

    public BroadcasterC2SPacket(String msg) {
        this(msg, false);
    }

    public BroadcasterC2SPacket() {
        this("", true);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}