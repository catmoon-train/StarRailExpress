package org.agmas.noellesroles.game.halloween;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

/** 仅发给锯人双方；remaining=0 清除 HUD，时间完全由服务端决定。 */
public record HalloweenSawS2CPayload(ResourceLocation dimension, int remaining, int total,
                                     boolean victim, boolean paused) implements CustomPacketPayload {
    public static final Type<HalloweenSawS2CPayload> ID = new Type<>(Noellesroles.id("halloween_saw"));
    public static final StreamCodec<RegistryFriendlyByteBuf, HalloweenSawS2CPayload> CODEC = StreamCodec.ofMember(
            (packet, buf) -> {
                buf.writeResourceLocation(packet.dimension());
                buf.writeVarInt(packet.remaining());
                buf.writeVarInt(packet.total());
                buf.writeBoolean(packet.victim());
                buf.writeBoolean(packet.paused());
            }, buf -> new HalloweenSawS2CPayload(buf.readResourceLocation(), buf.readVarInt(), buf.readVarInt(),
                    buf.readBoolean(), buf.readBoolean()));

    @Override public Type<? extends CustomPacketPayload> type() { return ID; }
}
