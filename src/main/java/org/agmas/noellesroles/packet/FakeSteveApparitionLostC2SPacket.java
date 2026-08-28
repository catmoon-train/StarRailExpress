package org.agmas.noellesroles.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

import java.util.UUID;

public record FakeSteveApparitionLostC2SPacket(UUID apparitionId) implements CustomPacketPayload {
    public static final Type<FakeSteveApparitionLostC2SPacket> ID = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "fake_steve_apparition_lost"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FakeSteveApparitionLostC2SPacket> CODEC =
            StreamCodec.composite(UUIDUtil.STREAM_CODEC, FakeSteveApparitionLostC2SPacket::apparitionId,
                    FakeSteveApparitionLostC2SPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
