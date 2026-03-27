package io.wifi.starrailexpress.network;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public class PlayerDeathPayload implements CustomPacketPayload {
    public static final Type<PlayerDeathPayload> ID = new Type<>(SRE.id("player_death"));
    public static final StreamCodec<FriendlyByteBuf, PlayerDeathPayload> CODEC = CustomPacketPayload.codec(PlayerDeathPayload::write, PlayerDeathPayload::new);

    public PlayerDeathPayload(FriendlyByteBuf friendlyByteBuf) {

    }
    public PlayerDeathPayload() {

    }


    public void write(FriendlyByteBuf buf) {

    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
