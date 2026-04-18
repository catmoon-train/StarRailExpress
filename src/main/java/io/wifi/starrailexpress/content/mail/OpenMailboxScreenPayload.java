package io.wifi.starrailexpress.content.mail;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * S2C – 通知客户端打开邮箱界面。
 */
public record OpenMailboxScreenPayload() implements CustomPacketPayload {
    public static final Type<OpenMailboxScreenPayload> ID =
            new Type<>(SRE.id("open_mailbox_screen"));
    public static final StreamCodec<FriendlyByteBuf, OpenMailboxScreenPayload> CODEC =
            CustomPacketPayload.codec(OpenMailboxScreenPayload::encode, OpenMailboxScreenPayload::decode);
    public static final OpenMailboxScreenPayload INSTANCE = new OpenMailboxScreenPayload();

    public static void encode(OpenMailboxScreenPayload payload, FriendlyByteBuf buf) {
    }

    public static OpenMailboxScreenPayload decode(FriendlyByteBuf buf) {
        return INSTANCE;
    }

    @Override
    public Type<OpenMailboxScreenPayload> type() {
        return ID;
    }
}
