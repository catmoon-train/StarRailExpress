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
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

public record RhythmGameResultC2SPacket(int score) implements CustomPacketPayload {
    public static final ResourceLocation ABILITY_PAYLOAD_ID = ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID,
            "rhythm_game_result");
    public static final Type<RhythmGameResultC2SPacket> ID = new Type<>(ABILITY_PAYLOAD_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, RhythmGameResultC2SPacket> CODEC;

    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeInt(score());
    }

    public static RhythmGameResultC2SPacket read(FriendlyByteBuf buf) {
        return new RhythmGameResultC2SPacket(buf.readInt());
    }

    static {
        CODEC = StreamCodec.ofMember(RhythmGameResultC2SPacket::write, RhythmGameResultC2SPacket::read);
    }
}