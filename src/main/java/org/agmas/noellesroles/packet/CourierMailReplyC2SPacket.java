package org.agmas.noellesroles.packet;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
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
import io.wifi.starrailexpress.index.TMMItems;
import org.agmas.noellesroles.content.item.CourierMailItem;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.content.entity.PigeonEntity;
import org.agmas.noellesroles.content.item.CourierMailData;
import org.agmas.noellesroles.init.ModEntities;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

        // 信使已死亡/离线/旁观者 → 返还回信附件物品并提示
        if (courier == null || !courier.isAlive() || courier.isSpectator()) {
            // 返还已选中的附件物品
            if (p.itemSlot >= 0) {
                // 物品在点击"发送"前已在客户端UI中被选中，但尚未从背包扣除
                // 此处无须返还，因为扣除发生在后面
            }
            // 消耗手中的回信
            player.getItemInHand(p.mainHand ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND).shrink(1);
            player.displayClientMessage(Component.translatable("message.noellesroles.courier.courier_dead"), true);
            return;
        }

        if (courier.getInventory().getFreeSlot() < 0) {
            player.displayClientMessage(Component.translatable("message.noellesroles.courier.reply_fail"), true);
            return;
        }

        // 附件物品校验：禁止把德林加手枪、信封本身或炸弹放入回信
        if (p.itemSlot >= 0) {
            ItemStack attach = player.getInventory().getItem(p.itemSlot);
            if (!attach.isEmpty()) {
                if (attach.getItem() == TMMItems.DERRINGER) {
                    player.displayClientMessage(Component.translatable("message.noellesroles.courier.derringer_banned"), true);
                    return;
                }
                if (attach.getItem() instanceof CourierMailItem) {
                    player.displayClientMessage(Component.translatable("message.noellesroles.courier.attachment_banned"), true);
                    return;
                }
                if (attach.is(ModItems.BOMB)) {
                    player.displayClientMessage(Component.translatable("message.noellesroles.courier.attachment_banned"), true);
                    return;
                }
            }
        }

        // 附件物品：发送时立刻消耗并序列化到信鸽身上
        @Nullable CompoundTag attachmentTag = null;
        if (p.itemSlot >= 0) {
            ItemStack slotStack = player.getInventory().getItem(p.itemSlot);
            if (!slotStack.isEmpty()) {
                ItemStack copy = slotStack.copyWithCount(1);
                Tag saved = copy.save(level.registryAccess());
                if (saved instanceof CompoundTag ct) {
                    attachmentTag = ct;
                }
                slotStack.shrink(1);
            }
        }

        // 生成回信鸽
        PigeonEntity pigeon = new PigeonEntity(ModEntities.PIGEON, level);
        pigeon.setPos(player.getX(), player.getY() + 2.2, player.getZ());
        pigeon.setTargetPlayer(courier, player, p.message, 0, attachmentTag, true, 0);
        level.addFreshEntity(pigeon);

        player.getItemInHand(p.mainHand ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND).shrink(1);
        level.playSound(null, player.blockPosition(), SoundEvents.PARROT_AMBIENT, SoundSource.MASTER, 1.0F, 1.0F);
    }
}
