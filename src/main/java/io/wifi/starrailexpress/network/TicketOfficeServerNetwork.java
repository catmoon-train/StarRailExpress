package io.wifi.starrailexpress.network;

import io.wifi.starrailexpress.content.block_entity.TicketOfficeBlockEntity;
import io.wifi.starrailexpress.content.item.AdmissionTicketItem;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.agmas.noellesroles.utils.RoleUtils;

public class TicketOfficeServerNetwork {
    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(TicketPayload.SaveOfficeConfig.TYPE,
                TicketOfficeServerNetwork::handleSave);
        ServerPlayNetworking.registerGlobalReceiver(TicketPayload.BuyTicket.TYPE, TicketOfficeServerNetwork::handleBuy);
    }

    private static void handleSave(TicketPayload.SaveOfficeConfig payload, ServerPlayNetworking.Context context) {
        ServerPlayer player = context.player();
        player.getServer().execute(() -> {
            if (!player.isCreative()) {
                return;
            }
            BlockEntity be = player.level().getBlockEntity(payload.pos());
            if (be instanceof TicketOfficeBlockEntity office) {
                office.loadConfig(payload.data());
                player.displayClientMessage(Component.translatable("message.starrailexpress.ticket_office.saved"), true);
            }
        });
    }

    private static void handleBuy(TicketPayload.BuyTicket payload, ServerPlayNetworking.Context context) {
        ServerPlayer player = context.player();
        player.getServer().execute(() -> {
            BlockEntity be = player.level().getBlockEntity(payload.pos());
            if (!(be instanceof TicketOfficeBlockEntity office)) {
                return;
            }
            if (office.getCurrency().getBalance(player) < office.getPrice()) {
                player.displayClientMessage(Component.translatable("message.starrailexpress.ticket_office.no_money"), true);
                return;
            }
            ItemStack ticket = AdmissionTicketItem.create(office.getTicketId(), office.getTicketName(), office.getUses());
            if (!RoleUtils.insertStackInFreeSlot(player, ticket)) {
                player.displayClientMessage(Component.translatable("message.starrailexpress.ticket_office.hotbar_full"), true);
                return;
            }
            office.getCurrency().add(player, -office.getPrice());
            player.displayClientMessage(Component.translatable("message.starrailexpress.ticket_office.bought"), true);
        });
    }
}
