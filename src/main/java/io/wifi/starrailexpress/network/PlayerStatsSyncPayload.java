package io.wifi.starrailexpress.network;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.UUID;

public record PlayerStatsSyncPayload(UUID playerUuid, String json) implements CustomPacketPayload {
    public static final Type<PlayerStatsSyncPayload> ID = new Type<>(SRE.id("player_stats_sync"));
    public static final StreamCodec<FriendlyByteBuf, PlayerStatsSyncPayload> CODEC =
            CustomPacketPayload.codec(PlayerStatsSyncPayload::write, PlayerStatsSyncPayload::new);

    private PlayerStatsSyncPayload(FriendlyByteBuf buffer) {
        this(buffer.readUUID(), buffer.readUtf(1_048_576));
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeUUID(playerUuid);
        buffer.writeUtf(json, 1_048_576);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
