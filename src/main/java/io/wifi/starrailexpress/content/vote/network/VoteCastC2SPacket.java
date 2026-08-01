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

package io.wifi.starrailexpress.content.vote.network;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

public record VoteCastC2SPacket(List<Integer> optionIndices) implements CustomPacketPayload {
    public static final Type<VoteCastC2SPacket> TYPE = new Type<>(SRE.id("vote_cast"));
    public static final StreamCodec<RegistryFriendlyByteBuf, VoteCastC2SPacket> CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeVarInt(packet.optionIndices.size());
                for (int idx : packet.optionIndices) {
                    buf.writeVarInt(idx);
                }
            },
            buf -> {
                int size = buf.readVarInt();
                List<Integer> list = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    list.add(buf.readVarInt());
                }
                return new VoteCastC2SPacket(list);
            }
    );

    @Override
    public Type<VoteCastC2SPacket> type() {
        return TYPE;
    }
}