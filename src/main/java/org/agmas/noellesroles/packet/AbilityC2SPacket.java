package org.agmas.noellesroles.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

public record AbilityC2SPacket() implements CustomPacketPayload {
    public static final ResourceLocation ABILITY_PAYLOAD_ID = ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "ability_no");
    public static final Type<AbilityC2SPacket> ID = new Type<>(ABILITY_PAYLOAD_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, AbilityC2SPacket> CODEC;

    public AbilityC2SPacket() {
    }

    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public void write(FriendlyByteBuf buf) {

    }

    public static AbilityC2SPacket read(FriendlyByteBuf buf) {
        return new AbilityC2SPacket();
    }


    static {
        CODEC = StreamCodec.ofMember(AbilityC2SPacket::write, AbilityC2SPacket::read);
    }
}