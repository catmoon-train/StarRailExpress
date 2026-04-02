package org.agmas.noellesroles.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

public record NinjaAbilityC2SPacket() implements CustomPacketPayload {
    public static final Type<NinjaAbilityC2SPacket> ID = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "ninja_ability")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, NinjaAbilityC2SPacket> CODEC =
            StreamCodec.unit(new NinjaAbilityC2SPacket());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}