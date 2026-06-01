package io.wifi.starrailexpress.content.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import io.wifi.starrailexpress.cca.DNFInventoryBackupComponent;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class RestoreDNFInventoryCommand {
    
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("sre:restore-dnf-inventory")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(context -> executeRestore(context, EntityArgument.getPlayer(context, "target")))));
    }

    private static int executeRestore(CommandContext<CommandSourceStack> context, ServerPlayer targetPlayer) {
        try {
            DNFInventoryBackupComponent backupComponent = DNFInventoryBackupComponent.KEY.get(targetPlayer);
            
            if (!backupComponent.hasBackup()) {
                context.getSource().sendFailure(
                    Component.translatable("commands.sre.restore-dnf-inventory.no_backup", targetPlayer.getName())
                );
                return 0;
            }
            
            boolean success = backupComponent.restoreInventory(targetPlayer);
            
            if (success) {
                context.getSource().sendSuccess(
                    () -> Component.translatable("commands.sre.restore-dnf-inventory.success", targetPlayer.getName()),
                    true
                );
                
                // 通知玩家
                targetPlayer.sendSystemMessage(
                    Component.translatable("commands.sre.restore-dnf-inventory.restored")
                );
                
                return 1;
            } else {
                context.getSource().sendFailure(
                    Component.translatable("commands.sre.restore-dnf-inventory.failed", targetPlayer.getName())
                );
                return 0;
            }
        } catch (Exception e) {
            context.getSource().sendFailure(
                Component.translatable("commands.sre.restore-dnf-inventory.error", 
                    targetPlayer.getName(), 
                    e.getMessage())
            );
            return 0;
        }
    }
}
