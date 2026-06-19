package io.wifi.starrailexpress.network;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 服务端 -> 客户端：广播当前职业轮换名单（JSON 序列化的 {@code RoleRosterState}）。
 */
public record RoleRosterSyncPayload(String json) implements CustomPacketPayload {
    public static final Type<RoleRosterSyncPayload> ID = new Type<>(SRE.id("role_roster_sync"));
    public static final StreamCodec<FriendlyByteBuf, RoleRosterSyncPayload> CODEC =
            CustomPacketPayload.codec(RoleRosterSyncPayload::write, RoleRosterSyncPayload::new);

    private RoleRosterSyncPayload(FriendlyByteBuf buffer) {
        this(buffer.readUtf(1_048_576));
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeUtf(json, 1_048_576);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
