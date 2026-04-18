package io.wifi.starrailexpress.content.mail;

import io.wifi.starrailexpress.SRE;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * C2S – 客户端请求删除一封邮件（必须已领取或无附件）。
 */
public record MailDeleteC2SPayload(UUID mailId) implements CustomPacketPayload {
    public static final Type<MailDeleteC2SPayload> ID =
            new Type<>(SRE.id("mail_delete"));
    public static final StreamCodec<FriendlyByteBuf, MailDeleteC2SPayload> CODEC =
            StreamCodec.ofMember(MailDeleteC2SPayload::write, MailDeleteC2SPayload::read);

    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(mailId);
    }

    public static MailDeleteC2SPayload read(FriendlyByteBuf buf) {
        return new MailDeleteC2SPayload(buf.readUUID());
    }

    @Override
    public Type<MailDeleteC2SPayload> type() {
        return ID;
    }

    public static class Receiver implements ServerPlayNetworking.PlayPayloadHandler<MailDeleteC2SPayload> {
        @Override
        public void receive(@NotNull MailDeleteC2SPayload payload,
                            ServerPlayNetworking.@NotNull Context context) {
            MailboxComponent mailbox = MailboxComponent.KEY.get(context.player());
            mailbox.deleteMail(payload.mailId());
        }
    }
}
