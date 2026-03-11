package io.wifi.starrailexpress.network.original;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ShootMuzzleS2CPayload(int shooterId) implements CustomPacketPayload {
    public static final Type<ShootMuzzleS2CPayload> ID = new Type<>(SRE.id("shoot_muzzle_s2c"));
    public static final StreamCodec<FriendlyByteBuf, ShootMuzzleS2CPayload> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, ShootMuzzleS2CPayload::shooterId, ShootMuzzleS2CPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

}