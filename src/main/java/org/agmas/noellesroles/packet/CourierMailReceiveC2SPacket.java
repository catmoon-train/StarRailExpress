package org.agmas.noellesroles.packet;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.content.item.CourierMailData;
import org.agmas.noellesroles.init.ModEffects;
import org.jetbrains.annotations.NotNull;

/** 收信人领取信件内容 C2S */
public record CourierMailReceiveC2SPacket(boolean mainHand) implements CustomPacketPayload {
    public static final Type<CourierMailReceiveC2SPacket> TYPE = new Type<>(Noellesroles.id("courier_mail_receive"));
    public static final StreamCodec<FriendlyByteBuf, CourierMailReceiveC2SPacket> STREAM_CODEC = StreamCodec.ofMember(
            CourierMailReceiveC2SPacket::write, CourierMailReceiveC2SPacket::new);

    private CourierMailReceiveC2SPacket(FriendlyByteBuf buf) { this(buf.readBoolean()); }
    private void write(FriendlyByteBuf buf) { buf.writeBoolean(mainHand); }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(CourierMailReceiveC2SPacket p, ServerPlayNetworking.Context ctx) {
        ServerPlayer player = ctx.player();
        InteractionHand hand = p.mainHand ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        ItemStack stack = player.getItemInHand(hand);
        int effect = CourierMailData.getEffect(stack);
        switch (effect) {
            case 1 -> { // 恢复0.2 san
                var mood = player.getComponent(io.wifi.starrailexpress.cca.SREPlayerMoodComponent.KEY);
                if (mood != null) mood.addMood(0.2f);
            }
            case 2 -> player.addEffect(new MobEffectInstance(
                    MobEffects.MOVEMENT_SPEED,
                    15 * 20, 0));
            case 3 -> player.addEffect(new MobEffectInstance(
                    ModEffects.DISGUISE, 10 * 20, 2)); // 三级伪装的amplifier是2
        }
        // 不删除信封——收信人还需要用它来回信；回信发包时由 CourierMailReplyC2SPacket 删除
    }
}
