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

package io.wifi.starrailexpress.network.packet;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record MobRiotStateS2CPacket(
        boolean active,
        boolean day,
        int remainingSeconds,
        int tokens,
        int tokenGoal,
        boolean unlocked) implements CustomPacketPayload {
    public static final Type<MobRiotStateS2CPacket> ID = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SRE.MOD_ID, "mob_riot_state"));
    public static final StreamCodec<FriendlyByteBuf, MobRiotStateS2CPacket> CODEC = StreamCodec.ofMember(
            (packet, buf) -> {
                buf.writeBoolean(packet.active());
                buf.writeBoolean(packet.day());
                buf.writeVarInt(packet.remainingSeconds());
                buf.writeVarInt(packet.tokens());
                buf.writeVarInt(packet.tokenGoal());
                buf.writeBoolean(packet.unlocked());
            },
            buf -> new MobRiotStateS2CPacket(
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
