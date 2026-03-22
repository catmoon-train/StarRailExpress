package org.agmas.harpymodloader.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.commands.argument.ModifierArgumentType;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.harpymodloader.events.ModifierAssigned;
import org.agmas.harpymodloader.events.ModifierRemoved;
import org.agmas.harpymodloader.modifiers.SREModifier;

public class AssignModifierCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("changeModifier")
                .requires(serverCommandSource -> serverCommandSource.hasPermission(2))
                .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("modifier", ModifierArgumentType.create())
                                .executes(AssignModifierCommand::execute))));
    }

    private static int execute(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        if(!Harpymodloader.isMojangVerify) {
            return 1;
        }
        ServerPlayer targetPlayer = EntityArgument.getPlayer(context, "player");
        SREModifier modifier = ModifierArgumentType.getModifier(context, "modifier");
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(targetPlayer.level());
        // 获取游戏世界组件
        WorldModifierComponent worldModifierComponent = WorldModifierComponent.KEY.get(targetPlayer.level());

        if(!game.isRunning()){
            context.getSource().sendSuccess(() -> Component.translatable("commands.changemodifier.player.notification.failed.nostart"), true);
            // return 2;
        }
        // 获取玩家当前Modifier状态
        var modifiers = worldModifierComponent.getModifiers(targetPlayer.getUUID());
        final MutableComponent feedbackText;

        if (modifiers.contains(modifier)) {
            ModifierRemoved.EVENT.invoker().removeModifier(targetPlayer, modifier);
            worldModifierComponent.removeModifier(targetPlayer.getUUID(), modifier);
            feedbackText = Component.translatable("commands.changemodifier.player.notification.remove",
                    targetPlayer.getDisplayName(), modifier.getName());
        } else {
            worldModifierComponent.addModifier(targetPlayer.getUUID(), modifier);
            ModifierAssigned.EVENT.invoker().assignModifier(targetPlayer, modifier);
            feedbackText = Component.translatable("commands.changemodifier.player.notification.add",
                    targetPlayer.getDisplayName(), modifier.getName());
        }
        // 发送反馈消息
        context.getSource().sendSuccess(() -> feedbackText, true);

        return 1;
    }
}