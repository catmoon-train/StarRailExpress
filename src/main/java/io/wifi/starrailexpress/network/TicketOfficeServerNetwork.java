/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.wifi.starrailexpress.network;

import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.content.block_entity.TicketOfficeBlockEntity;
import io.wifi.starrailexpress.content.item.AdmissionTicketItem;
import io.wifi.starrailexpress.event.OnGameEnd;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.agmas.noellesroles.utils.RoleUtils;

public class TicketOfficeServerNetwork {
    private static boolean registered;

    public static void register() {
        if (registered) return;
        registered = true;
        ServerPlayNetworking.registerGlobalReceiver(TicketPayload.SaveOfficeConfig.TYPE,
                TicketOfficeServerNetwork::handleSave);
        ServerPlayNetworking.registerGlobalReceiver(TicketPayload.BuyTicket.TYPE, TicketOfficeServerNetwork::handleBuy);

        // 游戏结束时重置所有售票处的购买计数
        OnGameEnd.EVENT.register((serverLevel, gameWorldComponent) -> {
            var playArea = AreasWorldComponent.KEY.get(serverLevel).getPlayArea();
            int minChunkX = ((int) playArea.minX) >> 4;
            int maxChunkX = ((int) playArea.maxX) >> 4;
            int minChunkZ = ((int) playArea.minZ) >> 4;
            int maxChunkZ = ((int) playArea.maxZ) >> 4;
            var chunkSource = serverLevel.getChunkSource();
            for (int cx = minChunkX; cx <= maxChunkX; cx++) {
                for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                    var chunk = chunkSource.getChunkNow(cx, cz);
                    if (chunk != null) {
                        for (var be : chunk.getBlockEntities().values()) {
                            if (be instanceof TicketOfficeBlockEntity office
                                    && playArea.contains(office.getBlockPos().getCenter())) {
                                office.resetPurchaseCounts();
                            }
                        }
                    }
                }
            }
        });
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
            if (!office.canPurchase(player)) {
                player.displayClientMessage(
                        Component.translatable("message.starrailexpress.ticket_office.purchase_limit"), true);
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
            office.recordPurchase(player);
            player.displayClientMessage(Component.translatable("message.starrailexpress.ticket_office.bought"), true);
        });
    }
}
