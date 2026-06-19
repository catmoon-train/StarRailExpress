package io.wifi.starrailexpress.network;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 服务端 -> 客户端：请求打开职业轮换界面。{@code admin} 为 true 时打开管理员编辑界面，否则打开玩家查看界面。
 */
public record OpenRoleRosterScreenPayload(boolean admin) implements CustomPacketPayload {
    public static final Type<OpenRoleRosterScreenPayload> ID = new Type<>(SRE.id("open_role_roster_screen"));
    public static final StreamCodec<FriendlyByteBuf, OpenRoleRosterScreenPayload> CODEC =
            CustomPacketPayload.codec(OpenRoleRosterScreenPayload::write, OpenRoleRosterScreenPayload::new);

    private OpenRoleRosterScreenPayload(FriendlyByteBuf buffer) {
        this(buffer.readBoolean());
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeBoolean(admin);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
