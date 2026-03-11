package org.agmas.noellesroles.packet;

import java.util.UUID;

import org.agmas.noellesroles.Noellesroles;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

public record AbilityWithTargetC2SPacket(UUID target) implements CustomPacketPayload {
    public static final ResourceLocation ABILITY_PAYLOAD_ID = ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID,
            "ability_target");
    public static final Type<AbilityWithTargetC2SPacket> ID = new Type<>(ABILITY_PAYLOAD_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, AbilityWithTargetC2SPacket> CODEC;

    public AbilityWithTargetC2SPacket(Player targetPlayer) {
        this(targetPlayer.getUUID());
    }

    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(this.target());
    }

    public static AbilityWithTargetC2SPacket read(FriendlyByteBuf buf) {
        return new AbilityWithTargetC2SPacket(buf.readUUID());
    }

    static {
        CODEC = StreamCodec.ofMember(AbilityWithTargetC2SPacket::write, AbilityWithTargetC2SPacket::read);
    }
}