package org.agmas.noellesroles.game.roles.Innocent.fool;

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