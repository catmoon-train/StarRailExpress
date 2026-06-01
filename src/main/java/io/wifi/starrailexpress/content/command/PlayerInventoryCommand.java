package io.wifi.starrailexpress.content.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.wifi.starrailexpress.content.gui.PlayerInventoryInspectMenu;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.jetbrains.annotations.Nullable;

public final class PlayerInventoryCommand {
    private PlayerInventoryCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("sre:inventory")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(context -> open(context.getSource(),
                                EntityArgument.getPlayer(context, "target")))));
        dispatcher.register(Commands.literal("sre:invsee")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(context -> open(context.getSource(),
                                EntityArgument.getPlayer(context, "target")))));
    }

    private static int open(CommandSourceStack source, ServerPlayer target) throws CommandSyntaxException {
        ServerPlayer viewer = source.getPlayerOrException();
        viewer.openMenu(new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return Component.translatable("screen.sre.player_inventory_inspect.title", target.getName());
            }

            @Override
            public @Nullable AbstractContainerMenu createMenu(int syncId, Inventory inventory, Player player) {
                return new PlayerInventoryInspectMenu(syncId, inventory, target);
            }
        });
        source.sendSuccess(() -> Component.translatable("commands.sre.inventory.opened", target.getName()), false);
        return 1;
    }
}
