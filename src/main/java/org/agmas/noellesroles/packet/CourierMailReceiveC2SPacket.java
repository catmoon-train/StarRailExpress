package org.agmas.noellesroles.packet;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.CompoundTag;
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

        // 附件物品：领取时从信件NBT还原并给予收信人
        if (CourierMailData.hasAttached(stack)) {
            CompoundTag itemTag = CourierMailData.getAttachmentItem(stack);
            if (!itemTag.isEmpty()) {
                java.util.Optional<ItemStack> parsed = ItemStack.parse(player.serverLevel().registryAccess(), itemTag);
                if (parsed.isPresent() && !parsed.get().isEmpty()) {
                    ItemStack attached = parsed.get();
                    attached.setCount(1);
                    if (player.getInventory().getFreeSlot() >= 0) {
                        player.getInventory().add(attached);
                    } else {
                        player.drop(attached, false);
                    }
                }
            }
            // 清除标记，防止重复领取
            CourierMailData.setAttached(stack, false);
        }
        // 标记已领取，防止重复领取效果
        CourierMailData.setClaimed(stack, true);
        // 回信在领取后应直接删除（无法也不需要再回复）
        if (CourierMailData.isReply(stack)) {
            stack.shrink(1);
        }
    }
}
