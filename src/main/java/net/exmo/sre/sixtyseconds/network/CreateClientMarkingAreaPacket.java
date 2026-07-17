package net.exmo.sre.sixtyseconds.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.Noellesroles;

public record CreateClientMarkingAreaPacket(Vec3 position, double radius, int durationTicks)
        implements CustomPacketPayload {
    public static final ResourceLocation ABILITY_PAYLOAD_ID = ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID,
            "marking_area_create");
    public static final Type<CreateClientMarkingAreaPacket> ID = new Type<>(ABILITY_PAYLOAD_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, CreateClientMarkingAreaPacket> CODEC;

    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVec3(position);
        buf.writeDouble(radius);
        buf.writeInt(durationTicks);
    }

    public static CreateClientMarkingAreaPacket read(FriendlyByteBuf buf) {
        return new CreateClientMarkingAreaPacket(buf.readVec3(), buf.readDouble(), buf.readInt());
    }

    static {
        CODEC = StreamCodec.ofMember(CreateClientMarkingAreaPacket::write, CreateClientMarkingAreaPacket::read);
    }
}
