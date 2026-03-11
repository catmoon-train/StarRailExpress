package org.agmas.noellesroles.packet;

import org.agmas.noellesroles.Noellesroles;

import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ThiefStealC2SPacket(UUID target) implements CustomPacketPayload {
    public static final ResourceLocation STEAL_PAYLOAD_ID = ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "steal");
    public static final Type<ThiefStealC2SPacket> ID = new Type<>(STEAL_PAYLOAD_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, ThiefStealC2SPacket> CODEC = StreamCodec.ofMember(
            ThiefStealC2SPacket::write,
            ThiefStealC2SPacket::read
    );

    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(this.target);
    }

    public static ThiefStealC2SPacket read(FriendlyByteBuf buf) {
        UUID target = buf.readUUID();
        return new ThiefStealC2SPacket(target);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}