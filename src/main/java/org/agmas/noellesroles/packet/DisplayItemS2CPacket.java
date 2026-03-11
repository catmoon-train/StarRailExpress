package org.agmas.noellesroles.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.Noellesroles;

public record DisplayItemS2CPacket(ItemStack itemStack) implements CustomPacketPayload {
    public static ResourceLocation DISPLAY_ITEM_PAYLOAD_ID =
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "display_item");
    public static final Type<DisplayItemS2CPacket> ID = new Type<>(DISPLAY_ITEM_PAYLOAD_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, DisplayItemS2CPacket> CODEC;

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeJsonWithCodec(ItemStack.CODEC, itemStack);
    }

    public static DisplayItemS2CPacket read(RegistryFriendlyByteBuf buf) {
        return new DisplayItemS2CPacket(
                buf.readJsonWithCodec(ItemStack.CODEC)
        );
    }
    static {
        CODEC = StreamCodec.ofMember(DisplayItemS2CPacket::write, DisplayItemS2CPacket::read);
    }
}
