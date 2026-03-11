package org.agmas.noellesroles.packet;

import org.agmas.noellesroles.Noellesroles;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ProblemScreenOpenC2SPacket(boolean forced, int maxTrial) implements CustomPacketPayload {
    public static final ResourceLocation ABILITY_PAYLOAD_ID = ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID,
            "problem_set_open");
    public static final Type<ProblemScreenOpenC2SPacket> ID = new Type<>(ABILITY_PAYLOAD_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, ProblemScreenOpenC2SPacket> CODEC;

    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBoolean(forced());
        buf.writeInt(maxTrial());
    }

    public static ProblemScreenOpenC2SPacket read(FriendlyByteBuf buf) {
        return new ProblemScreenOpenC2SPacket(buf.readBoolean(), buf.readInt());
    }

    static {
        CODEC = StreamCodec.ofMember(ProblemScreenOpenC2SPacket::write, ProblemScreenOpenC2SPacket::read);
    }
}