package org.agmas.noellesroles.packet;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.util.ItemSkinManager;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.component.ModComponents;
import net.exmo.sre.repair.role.RepairRoleDatabase;
import net.exmo.sre.repair.role.RepairRoleDefinition;

import java.util.ArrayList;

public record RepairRoleShopPurchaseC2SPacket(String roleId) implements CustomPacketPayload {
    public static final Type<RepairRoleShopPurchaseC2SPacket> ID = new Type<>(Noellesroles.id("repair_role_shop_purchase"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RepairRoleShopPurchaseC2SPacket> CODEC = StreamCodec
            .ofMember(RepairRoleShopPurchaseC2SPacket::encode, RepairRoleShopPurchaseC2SPacket::decode);

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeUtf(roleId);
    }

    public static RepairRoleShopPurchaseC2SPacket decode(RegistryFriendlyByteBuf buf) {
        return new RepairRoleShopPurchaseC2SPacket(buf.readUtf());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static void handle(RepairRoleShopPurchaseC2SPacket payload, ServerPlayNetworking.Context context) {
        ServerPlayer player = context.player();
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(player.level());
        if (game != null && game.isRunning()) {
            player.displayClientMessage(Component.translatable("message.noellesroles.repair.shop_ingame")
                    .withStyle(ChatFormatting.RED), true);
            refresh(player);
            return;
        }

        RepairRoleDatabase.loadInto(player);
        var component = ModComponents.REPAIR_ROLES.get(player);
        RepairRoleDefinition.byId(payload.roleId()).ifPresent(role -> {
            if (role.starter || component.owns(role)) {
                player.displayClientMessage(Component.translatable("message.noellesroles.repair.role_already_owned")
                        .withStyle(ChatFormatting.YELLOW), true);
                refresh(player);
                return;
            }
            int coins = ItemSkinManager.getCoinNum(player);
            if (coins < RepairRoleDefinition.UNLOCK_PRICE) {
                player.displayClientMessage(Component.translatable("message.noellesroles.repair.role_not_enough_skin_coins",
                        RepairRoleDefinition.UNLOCK_PRICE).withStyle(ChatFormatting.RED), true);
                refresh(player);
                return;
            }
            ItemSkinManager.addCoinNum(player, -RepairRoleDefinition.UNLOCK_PRICE);
            component.unlock(role);
            player.displayClientMessage(Component.translatable("message.noellesroles.repair.role_purchased", role.displayName())
                    .withStyle(ChatFormatting.GREEN), true);
            refresh(player);
        });
    }

    public static void refresh(ServerPlayer player) {
        RepairRoleDatabase.loadInto(player);
        var component = ModComponents.REPAIR_ROLES.get(player);
        ServerPlayNetworking.send(player, new OpenRepairRoleShopS2CPacket(ItemSkinManager.getCoinNum(player),
                new ArrayList<>(component.ownedRoles)));
    }
}
