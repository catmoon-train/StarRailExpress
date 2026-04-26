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