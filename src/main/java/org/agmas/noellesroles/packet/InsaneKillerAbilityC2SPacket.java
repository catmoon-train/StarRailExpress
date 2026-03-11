package org.agmas.noellesroles.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

public record InsaneKillerAbilityC2SPacket() implements CustomPacketPayload {
    public static final Type<InsaneKillerAbilityC2SPacket> ID = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "insane_killer_ability"));
    public static final StreamCodec<FriendlyByteBuf, InsaneKillerAbilityC2SPacket> CODEC = StreamCodec
            .unit(new InsaneKillerAbilityC2SPacket());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}