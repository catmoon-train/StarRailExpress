package org.agmas.noellesroles.commands;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.packet.DisplayItemS2CPacket;

public class DisplayItemCommand {
    public static void register() {
        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess, environment) -> {
                    dispatcher.register(Commands.literal("DisplayItem")
                            .executes(context->{
                                ServerPlayer player = context.getSource().getPlayer();
                                if(player != null) {
                                    ItemStack itemStack = player.getItemInHand(InteractionHand.MAIN_HAND);
                                    if(!itemStack.isEmpty())
                                        ServerPlayNetworking.send(player, new DisplayItemS2CPacket(itemStack));
                                    return 1;
                                }
                                return 0;
                            })
                    );
                });
    }
}
