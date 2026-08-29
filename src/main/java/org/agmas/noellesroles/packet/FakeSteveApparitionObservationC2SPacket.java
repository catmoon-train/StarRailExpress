package org.agmas.noellesroles.packet;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

import java.util.UUID;

/** Ordered client observation acknowledgement for a target-local apparition. */
public record FakeSteveApparitionObservationC2SPacket(UUID apparitionId, Stage stage)
        implements CustomPacketPayload {
    public enum Stage {
        OBSERVED,
        LOOKED_AWAY
    }

    public static final Type<FakeSteveApparitionObservationC2SPacket> ID = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID,
                    "fake_steve_apparition_observation"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FakeSteveApparitionObservationC2SPacket> CODEC =
            StreamCodec.composite(UUIDUtil.STREAM_CODEC,
                    FakeSteveApparitionObservationC2SPacket::apparitionId,
                    ByteBufCodecs.idMapper(i -> Stage.values()[i], Stage::ordinal),
                    FakeSteveApparitionObservationC2SPacket::stage,
                    FakeSteveApparitionObservationC2SPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
