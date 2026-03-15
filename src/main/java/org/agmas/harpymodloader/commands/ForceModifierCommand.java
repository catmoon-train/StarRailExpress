package org.agmas.harpymodloader.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.commands.argument.ModifierArgumentType;
import org.agmas.harpymodloader.modifiers.SREModifier;

public class ForceModifierCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("forceModifier")
                .requires(serverCommandSource -> serverCommandSource.hasPermission(2))
                .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("modifier", ModifierArgumentType.create())
                                .executes(ForceModifierCommand::execute))));
    }

    private static int execute(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        if(!Harpymodloader.isMojangVerify) {
            return 1;
        }
        ServerPlayer targetPlayer = EntityArgument.getPlayer(context, "player");
        SREModifier modifier = ModifierArgumentType.getModifier(context, "modifier");
        Harpymodloader.addToForcedModifiers(modifier, targetPlayer);
        final MutableComponent modifierName = modifier.getName(true).withStyle(style ->
                style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(modifier.identifier().toString()))));
        context.getSource().sendSuccess(() -> Component.translatable("commands.forcerole.success", modifierName, targetPlayer.getDisplayName()), true);
        return 1;
    }
}