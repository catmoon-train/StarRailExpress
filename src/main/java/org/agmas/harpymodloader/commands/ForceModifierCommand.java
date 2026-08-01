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

package org.agmas.harpymodloader.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import io.wifi.starrailexpress.SREConfig;
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
                .requires(serverCommandSource -> serverCommandSource.hasPermission(SREConfig.instance().forceModifierRequiredPermission))
                .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("modifier", ModifierArgumentType.create())
                                .executes(ForceModifierCommand::execute))));
    }

    private static int execute(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        if(!Harpymodloader.officialVerify) {
            return 1;
        }
        ServerPlayer targetPlayer = EntityArgument.getPlayer(context, "player");
        SREModifier modifier = ModifierArgumentType.getModifier(context, "modifier");
        Harpymodloader.addToForcedModifiers(modifier, targetPlayer);
        final MutableComponent modifierName = modifier.getName(true).withStyle(style ->
                style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(modifier.identifier().toString()))));
        context.getSource().sendSuccess(() -> Component.translatable("commands.forcerole.success", modifierName, targetPlayer.getName()), true);
        return 1;
    }
}