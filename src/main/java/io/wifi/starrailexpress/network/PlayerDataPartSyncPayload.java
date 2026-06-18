package io.wifi.starrailexpress.network;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.UUID;

public record PlayerDataPartSyncPayload(UUID playerUuid, String part, String json, long updatedAt)
        implements CustomPacketPayload {
    public static final Type<PlayerDataPartSyncPayload> ID = new Type<>(SRE.id("player_data_part_sync"));
    public static final StreamCodec<FriendlyByteBuf, PlayerDataPartSyncPayload> CODEC =
            CustomPacketPayload.codec(PlayerDataPartSyncPayload::write, PlayerDataPartSyncPayload::new);

    private PlayerDataPartSyncPayload(FriendlyByteBuf buffer) {
        this(buffer.readUUID(), buffer.readUtf(64), buffer.readUtf(1_048_576), buffer.readVarLong());
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeUUID(playerUuid);
        buffer.writeUtf(part, 64);
        buffer.writeUtf(json, 1_048_576);
        buffer.writeVarLong(updatedAt);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
