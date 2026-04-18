package io.wifi.starrailexpress.content.mail;

import io.wifi.starrailexpress.SRE;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jetbrains.annotations.NotNull;

/**
 * C2S – 客户端请求一键领取所有可领取邮件。
 */
public record MailClaimAllC2SPayload() implements CustomPacketPayload {
    public static final Type<MailClaimAllC2SPayload> ID =
            new Type<>(SRE.id("mail_claim_all"));
    public static final StreamCodec<FriendlyByteBuf, MailClaimAllC2SPayload> CODEC =
            CustomPacketPayload.codec(MailClaimAllC2SPayload::encode, MailClaimAllC2SPayload::decode);
    public static final MailClaimAllC2SPayload INSTANCE = new MailClaimAllC2SPayload();

    public static void encode(MailClaimAllC2SPayload payload, FriendlyByteBuf buf) {
    }

    public static MailClaimAllC2SPayload decode(FriendlyByteBuf buf) {
        return INSTANCE;
    }

    @Override
    public Type<MailClaimAllC2SPayload> type() {
        return ID;
    }

    public static class Receiver implements ServerPlayNetworking.PlayPayloadHandler<MailClaimAllC2SPayload> {
        @Override
        public void receive(@NotNull MailClaimAllC2SPayload payload,
                            ServerPlayNetworking.@NotNull Context context) {
            MailboxComponent mailbox = MailboxComponent.KEY.get(context.player());
            mailbox.claimAll();
        }
    }
}
