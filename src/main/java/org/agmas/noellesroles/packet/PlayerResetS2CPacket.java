package org.agmas.noellesroles.packet;

import org.agmas.noellesroles.Noellesroles;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record PlayerResetS2CPacket() implements CustomPacketPayload {
    public static final ResourceLocation ABILITY_PAYLOAD_ID = ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "player_reset");
    public static final Type<PlayerResetS2CPacket> ID = new Type<>(ABILITY_PAYLOAD_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, PlayerResetS2CPacket> CODEC;

    public PlayerResetS2CPacket() {
    }

    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public void write(FriendlyByteBuf buf) {

    }

    public static PlayerResetS2CPacket read(FriendlyByteBuf buf) {
        return new PlayerResetS2CPacket();
    }


    static {
        CODEC = StreamCodec.ofMember(PlayerResetS2CPacket::write, PlayerResetS2CPacket::read);
    }
}