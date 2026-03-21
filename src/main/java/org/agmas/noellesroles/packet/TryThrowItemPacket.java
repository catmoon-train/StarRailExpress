package org.agmas.noellesroles.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

public record TryThrowItemPacket() implements CustomPacketPayload {

    public static final Type<TryThrowItemPacket> ID = new Type<>(
        ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "try_item_knife")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, TryThrowItemPacket> CODEC = StreamCodec.ofMember(
        (packet, buf) -> {
            // 无需写入数据，只是触发技能
        },
        buf -> new TryThrowItemPacket()
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}