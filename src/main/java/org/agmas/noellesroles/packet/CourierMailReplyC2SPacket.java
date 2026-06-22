package org.agmas.noellesroles.packet;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.content.entity.PigeonEntity;
import org.agmas.noellesroles.content.item.CourierMailData;
import org.agmas.noellesroles.init.ModEntities;
import org.jetbrains.annotations.NotNull;

/** 收信人免费回信 C2S */
public record CourierMailReplyC2SPacket(boolean mainHand, byte[] message, int itemSlot) implements CustomPacketPayload {
    public static final Type<CourierMailReplyC2SPacket> TYPE = new Type<>(Noellesroles.id("courier_mail_reply"));
    public static final StreamCodec<FriendlyByteBuf, CourierMailReplyC2SPacket> STREAM_CODEC = StreamCodec.ofMember(
            CourierMailReplyC2SPacket::write, CourierMailReplyC2SPacket::new);

    private CourierMailReplyC2SPacket(FriendlyByteBuf buf) { this(buf.readBoolean(), buf.readByteArray(), buf.readInt()); }
    private void write(FriendlyByteBuf buf) { buf.writeBoolean(mainHand); buf.writeByteArray(message); buf.writeInt(itemSlot); }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(CourierMailReplyC2SPacket p, ServerPlayNetworking.Context ctx) {
        ServerPlayer player = ctx.player();
        ServerLevel level = player.serverLevel();
        ItemStack stack = player.getItemInHand(p.mainHand ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND);
        String sender = CourierMailData.getSender(stack);
        if (sender.isEmpty() || sender.equals("Reply")) return;

        ServerPlayer courier = null;
        try { courier = level.getServer().getPlayerList().getPlayer(java.util.UUID.fromString(sender)); }
        catch (Exception ignored) {}

        if (courier == null || courier.getInventory().getFreeSlot() < 0) {
            player.displayClientMessage(Component.translatable("message.noellesroles.courier.reply_fail"), true);
            return;
        }

        // 生成回信鸽
        PigeonEntity pigeon = new PigeonEntity(ModEntities.PIGEON, level);
        pigeon.setPos(player.getX(), player.getY() + 2.2, player.getZ());
        pigeon.setTargetPlayer(courier, player, p.message, 0, p.itemSlot, true);
        level.addFreshEntity(pigeon);

        player.getItemInHand(p.mainHand ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND).shrink(1);
        level.playSound(null, player.blockPosition(), SoundEvents.PARROT_AMBIENT, SoundSource.MASTER, 1.0F, 1.0F);
    }
}
