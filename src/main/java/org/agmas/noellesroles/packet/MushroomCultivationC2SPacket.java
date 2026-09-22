package org.agmas.noellesroles.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

public record MushroomCultivationC2SPacket(int[] pours) implements CustomPacketPayload {
    public static final Type<MushroomCultivationC2SPacket> ID = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "mushroom_cultivation"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MushroomCultivationC2SPacket> CODEC =
            StreamCodec.ofMember(MushroomCultivationC2SPacket::write, MushroomCultivationC2SPacket::read);

    private void write(RegistryFriendlyByteBuf buf) {
        int count = Math.min(pours == null ? 0 : pours.length, 3);
        buf.writeByte(count);
        for (int i = 0; i < count; i++) {
            buf.writeByte(pours[i]);
        }
    }

    private static MushroomCultivationC2SPacket read(RegistryFriendlyByteBuf buf) {
        int count = Math.min(buf.readUnsignedByte(), 3);
        int[] pours = new int[count];
        for (int i = 0; i < count; i++) {
            pours[i] = buf.readByte();
        }
        return new MushroomCultivationC2SPacket(pours);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
