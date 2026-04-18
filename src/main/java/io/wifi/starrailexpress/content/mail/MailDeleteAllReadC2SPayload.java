package io.wifi.starrailexpress.content.mail;

import io.wifi.starrailexpress.SRE;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jetbrains.annotations.NotNull;

/**
 * C2S – 客户端请求删除所有已领取/已读邮件。
 */
public record MailDeleteAllReadC2SPayload() implements CustomPacketPayload {
    public static final Type<MailDeleteAllReadC2SPayload> ID =
            new Type<>(SRE.id("mail_delete_all_read"));
    public static final StreamCodec<FriendlyByteBuf, MailDeleteAllReadC2SPayload> CODEC =
            CustomPacketPayload.codec(MailDeleteAllReadC2SPayload::encode, MailDeleteAllReadC2SPayload::decode);
    public static final MailDeleteAllReadC2SPayload INSTANCE = new MailDeleteAllReadC2SPayload();

    public static void encode(MailDeleteAllReadC2SPayload payload, FriendlyByteBuf buf) {
    }

    public static MailDeleteAllReadC2SPayload decode(FriendlyByteBuf buf) {
        return INSTANCE;
    }

    @Override
    public Type<MailDeleteAllReadC2SPayload> type() {
        return ID;
    }

    public static class Receiver implements ServerPlayNetworking.PlayPayloadHandler<MailDeleteAllReadC2SPayload> {
        @Override
        public void receive(@NotNull MailDeleteAllReadC2SPayload payload,
                            ServerPlayNetworking.@NotNull Context context) {
            MailboxComponent mailbox = MailboxComponent.KEY.get(context.player());
            mailbox.deleteAllClaimed();
        }
    }
}
