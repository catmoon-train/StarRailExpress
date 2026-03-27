package org.agmas.noellesroles.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

public record OpenKeyForgeGuiS2CPacket(int inspirationPoints) implements CustomPacketPayload {
    public static final ResourceLocation OPEN_KEY_FORGE_GUI_ID =
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "open_key_forge_gui");
    public static final Type<OpenKeyForgeGuiS2CPacket> ID = new Type<>(OPEN_KEY_FORGE_GUI_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenKeyForgeGuiS2CPacket> CODEC;

    static {
        CODEC = StreamCodec.ofMember(OpenKeyForgeGuiS2CPacket::encode, OpenKeyForgeGuiS2CPacket::decode);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeInt(this.inspirationPoints);
    }

    public static OpenKeyForgeGuiS2CPacket decode(RegistryFriendlyByteBuf buf) {
        return new OpenKeyForgeGuiS2CPacket(buf.readInt());
    }
}
