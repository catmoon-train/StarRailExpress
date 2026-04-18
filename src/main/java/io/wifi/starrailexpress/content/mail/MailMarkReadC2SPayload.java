package io.wifi.starrailexpress.content.mail;

import io.wifi.starrailexpress.SRE;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * C2S – 客户端通知服务端标记某封邮件为已读。
 */
public record MailMarkReadC2SPayload(UUID mailId) implements CustomPacketPayload {
    public static final Type<MailMarkReadC2SPayload> ID =
            new Type<>(SRE.id("mail_mark_read"));
    public static final StreamCodec<FriendlyByteBuf, MailMarkReadC2SPayload> CODEC =
            StreamCodec.ofMember(MailMarkReadC2SPayload::write, MailMarkReadC2SPayload::read);

    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(mailId);
    }

    public static MailMarkReadC2SPayload read(FriendlyByteBuf buf) {
        return new MailMarkReadC2SPayload(buf.readUUID());
    }

    @Override
    public Type<MailMarkReadC2SPayload> type() {
        return ID;
    }

    public static class Receiver implements ServerPlayNetworking.PlayPayloadHandler<MailMarkReadC2SPayload> {
        @Override
        public void receive(@NotNull MailMarkReadC2SPayload payload,
                            ServerPlayNetworking.@NotNull Context context) {
            MailboxComponent mailbox = MailboxComponent.KEY.get(context.player());
            mailbox.markRead(payload.mailId());
        }
    }
}
