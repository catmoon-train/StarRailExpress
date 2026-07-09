package io.wifi.starrailexpress.network;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * C2S：管理员在地图轮换界面启用/停用某张地图。
 * 服务端会校验 {@code hasPermissions(2)}，写入 {@code train_vote_maps.json} 的 canSelect 字段，
 * 然后向所有玩家广播 {@link MapRotationSyncPayload}。
 */
public record MapRotationTogglePayload(String mapId, boolean enabled) implements CustomPacketPayload {
    public static final Type<MapRotationTogglePayload> ID = new Type<>(SRE.id("map_rotation_toggle"));
    public static final StreamCodec<FriendlyByteBuf, MapRotationTogglePayload> CODEC =
            CustomPacketPayload.codec(MapRotationTogglePayload::write, MapRotationTogglePayload::new);

    private MapRotationTogglePayload(FriendlyByteBuf buffer) {
        this(buffer.readUtf(256), buffer.readBoolean());
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeUtf(mapId == null ? "" : mapId, 256);
        buffer.writeBoolean(enabled);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
