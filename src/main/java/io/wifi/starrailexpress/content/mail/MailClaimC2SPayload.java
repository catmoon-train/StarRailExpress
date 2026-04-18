package io.wifi.starrailexpress.content.mail;

import io.wifi.starrailexpress.SRE;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * C2S – 客户端请求领取一封邮件。
 */
public record MailClaimC2SPayload(UUID mailId) implements CustomPacketPayload {
    public static final Type<MailClaimC2SPayload> ID =
            new Type<>(SRE.id("mail_claim"));
    public static final StreamCodec<FriendlyByteBuf, MailClaimC2SPayload> CODEC =
            StreamCodec.ofMember(MailClaimC2SPayload::write, MailClaimC2SPayload::read);

    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(mailId);
    }

    public static MailClaimC2SPayload read(FriendlyByteBuf buf) {
        return new MailClaimC2SPayload(buf.readUUID());
    }

    @Override
    public Type<MailClaimC2SPayload> type() {
        return ID;
    }

    public static class Receiver implements ServerPlayNetworking.PlayPayloadHandler<MailClaimC2SPayload> {
        @Override
        public void receive(@NotNull MailClaimC2SPayload payload,
                            ServerPlayNetworking.@NotNull Context context) {
            MailboxComponent mailbox = MailboxComponent.KEY.get(context.player());
            mailbox.claimMail(payload.mailId());
        }
    }
}
