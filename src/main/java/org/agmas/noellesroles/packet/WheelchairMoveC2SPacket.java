package org.agmas.noellesroles.packet;

import org.agmas.noellesroles.Noellesroles;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record WheelchairMoveC2SPacket(float forwardImpulse, float leftImpulse) implements CustomPacketPayload {
    public static final ResourceLocation ABILITY_PAYLOAD_ID = ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID,
            "wheelchair_movement");
    public static final Type<WheelchairMoveC2SPacket> ID = new Type<>(ABILITY_PAYLOAD_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, WheelchairMoveC2SPacket> CODEC;

    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeFloat(this.forwardImpulse());
        buf.writeFloat(this.leftImpulse());
    }

    public static WheelchairMoveC2SPacket read(FriendlyByteBuf buf) {
        return new WheelchairMoveC2SPacket(buf.readFloat(), buf.readFloat());
    }

    static {
        CODEC = StreamCodec.ofMember(WheelchairMoveC2SPacket::write, WheelchairMoveC2SPacket::read);
    }

}