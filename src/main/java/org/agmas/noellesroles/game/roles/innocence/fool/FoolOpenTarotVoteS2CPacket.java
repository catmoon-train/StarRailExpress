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

package org.agmas.noellesroles.game.roles.innocence.fool;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record FoolOpenTarotVoteS2CPacket(List<CandidateEntry> candidates, int durationSeconds)
        implements CustomPacketPayload {
    public record CandidateEntry(UUID candidateId, int voteCount, boolean alive) {
    }

    public static final ResourceLocation PAYLOAD_ID = ResourceLocation.fromNamespaceAndPath(
            Noellesroles.MOD_ID, "fool_open_tarot_vote");
    public static final Type<FoolOpenTarotVoteS2CPacket> ID = new Type<>(PAYLOAD_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, FoolOpenTarotVoteS2CPacket> CODEC = StreamCodec
            .ofMember(FoolOpenTarotVoteS2CPacket::encode, FoolOpenTarotVoteS2CPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeCollection(this.candidates, (friendlyByteBuf, candidate) -> {
            friendlyByteBuf.writeUUID(candidate.candidateId());
            friendlyByteBuf.writeVarInt(candidate.voteCount());
            friendlyByteBuf.writeBoolean(candidate.alive());
        });
        buf.writeVarInt(this.durationSeconds);
    }

    public static FoolOpenTarotVoteS2CPacket decode(RegistryFriendlyByteBuf buf) {
        return new FoolOpenTarotVoteS2CPacket(new ArrayList<>(buf.readList(friendlyByteBuf ->
                new CandidateEntry(
                        friendlyByteBuf.readUUID(),
                        friendlyByteBuf.readVarInt(),
                        friendlyByteBuf.readBoolean()))),
                buf.readVarInt());
    }
}