package org.agmas.noellesroles.commands;

import com.mojang.brigadier.Command;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.util.ItemSkinManager;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.game.modes.repair.RepairRoleDatabase;
import org.agmas.noellesroles.packet.OpenRepairRoleShopS2CPacket;

import java.util.ArrayList;

public final class RepairShopCommand {
    private RepairShopCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("cy:repairshop").executes(context -> {
                ServerPlayer player = context.getSource().getPlayerOrException();
                SREGameWorldComponent game = SREGameWorldComponent.KEY.get(player.level());
                if (game != null && game.isRunning()) {
                    player.displayClientMessage(Component.translatable("message.noellesroles.repair.shop_ingame")
                            .withStyle(ChatFormatting.RED), true);
                    return 0;
                }
                RepairRoleDatabase.loadInto(player);
                var component = ModComponents.REPAIR_ROLES.get(player);
                ServerPlayNetworking.send(player, new OpenRepairRoleShopS2CPacket(ItemSkinManager.getCoinNum(player),
                        new ArrayList<>(component.ownedRoles)));
                return Command.SINGLE_SUCCESS;
            }));
        });
    }
}
