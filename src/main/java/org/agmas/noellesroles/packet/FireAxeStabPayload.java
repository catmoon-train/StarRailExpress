package org.agmas.noellesroles.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.agmas.noellesroles.Noellesroles;

public record FireAxeStabPayload(int target) implements CustomPacketPayload {
    public static final Type<FireAxeStabPayload> ID = new Type<>(Noellesroles.id("fire_axe_stab"));
    public static final StreamCodec<FriendlyByteBuf, FireAxeStabPayload> CODEC = StreamCodec.composite(ByteBufCodecs.INT,
            FireAxeStabPayload::target, FireAxeStabPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}