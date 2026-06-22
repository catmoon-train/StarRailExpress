package org.agmas.noellesroles.packet;

import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.UUIDUtil;
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
import org.agmas.noellesroles.content.item.CourierMailItem;
import org.agmas.noellesroles.init.ModEntities;
import org.agmas.noellesroles.init.ModItems;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/** 信使发送信件 C2S */
public record CourierMailSendC2SPacket(boolean mainHand, UUID targetUuid, byte[] message, int effect, int itemSlot) implements CustomPacketPayload {
    public static final Type<CourierMailSendC2SPacket> TYPE = new Type<>(Noellesroles.id("courier_mail_send"));
    public static final StreamCodec<FriendlyByteBuf, CourierMailSendC2SPacket> STREAM_CODEC = StreamCodec.ofMember(
            CourierMailSendC2SPacket::write, CourierMailSendC2SPacket::new);

    private CourierMailSendC2SPacket(FriendlyByteBuf buf) {
        this(buf.readBoolean(), buf.readUUID(), buf.readByteArray(), buf.readInt(), buf.readInt());
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeBoolean(mainHand);
        buf.writeUUID(targetUuid);
        buf.writeByteArray(message);
        buf.writeInt(effect);
        buf.writeInt(itemSlot);
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(CourierMailSendC2SPacket p, ServerPlayNetworking.Context ctx) {
        ServerPlayer player = ctx.player();
        ServerLevel level = player.serverLevel();
        ServerPlayer target = level.getServer().getPlayerList().getPlayer(p.targetUuid);
        if (target == null) return;
        if (target.isSpectator() || target.isCreative()) {
            player.displayClientMessage(Component.translatable("message.noellesroles.courier.target_dead"), true);
            return;
        }
        if (target.getInventory().getFreeSlot() < 0) {
            player.displayClientMessage(Component.translatable("message.noellesroles.courier.target_full"), true);
            return;
        }

        // 计算金币
        int cost = 0;
        if (p.message.length > 0) cost += 50;
        if (p.effect > 0) cost += 50;
        if (p.itemSlot >= 0) cost += 75;
        var shop = player.getComponent(SREPlayerShopComponent.KEY);
        if (shop != null && shop.balance < cost) {
            player.displayClientMessage(Component.translatable("message.noellesroles.courier.insufficient_coins", cost - shop.balance), true);
            return;
        }

        // 扣除金币
        if (shop != null) shop.addToBalance(-cost);

        // MC 原版冷却 120 秒
        player.getCooldowns().addCooldown(ModItems.COURIER_MAIL, 120 * 20);

        // 生成信鸽
        PigeonEntity pigeon = new PigeonEntity(ModEntities.PIGEON, level);
        pigeon.setPos(player.getX(), player.getY() + 2.2, player.getZ());
        pigeon.setTargetPlayer(target, player, p.message, p.effect, p.itemSlot, false);
        level.addFreshEntity(pigeon);

        // 主声道鹦鹉叫
        level.playSound(null, player.blockPosition(), SoundEvents.PARROT_AMBIENT, SoundSource.MASTER, 1.0F, 1.0F);
    }
}
