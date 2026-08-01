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
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;

import io.wifi.starrailexpress.SREConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.commands.argument.ModifierArgumentType;
import org.agmas.harpymodloader.config.HarpyModLoaderConfig;
import org.agmas.harpymodloader.modifiers.HMLModifiers;
import org.agmas.harpymodloader.modifiers.SREModifier;

public class SetEnabledModifierCommand {
    public static final SimpleCommandExceptionType ROLE_UNCHANGED_EXCEPTION = new SimpleCommandExceptionType(
            Component.translatable("commands.setenabledmodifier.unchanged"));

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("setEnabledModifier")
                        .requires(serverCommandSource -> serverCommandSource.hasPermission(SREConfig.instance().modifyEnableStatusRequiredPermission))
                        .then(Commands.literal("enableAll").executes(SetEnabledModifierCommand::enableAll))
                        .then(Commands.literal("disableAll").executes(SetEnabledModifierCommand::disableAll))
                        .then(Commands.argument("modifier", ModifierArgumentType.create())
                                .then(Commands.argument("enabled", BoolArgumentType.bool())
                                        .executes(SetEnabledModifierCommand::execute))));
    }

    private static int disableAll(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        if (!Harpymodloader.officialVerify) {
            return 1;
        }
        HarpyModLoaderConfig.HANDLER.instance().disabledModifiers.clear();
        for (var role : HMLModifiers.MODIFIERS) {
            HarpyModLoaderConfig.HANDLER.instance().disabledModifiers.add(role.identifier().toString());
        }
        HarpyModLoaderConfig.HANDLER.save();
        context.getSource()
                .sendSuccess(() -> Component.translatable("commands.setenabledmodifier.disable.success", "ALL"), true);

        return 1;
    }

    private static int enableAll(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        if (!Harpymodloader.officialVerify) {
            return 1;
        }

        HarpyModLoaderConfig.HANDLER.instance().disabledModifiers.clear();
        HarpyModLoaderConfig.HANDLER.save();

        context.getSource().sendSuccess(
                () -> Component.translatable("commands.setenabledmodifier.enable.success","ALL"), true);
        return 1;
    }

    private static int execute(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        if (!Harpymodloader.officialVerify) {
            return 1;
        }
        boolean enabled = BoolArgumentType.getBool(context, "enabled");
        SREModifier modifier = ModifierArgumentType.getModifier(context, "modifier");
        final String modifierId = modifier.identifier().toString();
        boolean disabled = HarpyModLoaderConfig.HANDLER.instance().disabledModifiers.contains(modifierId);
        Component modifierName = modifier.getName(true).withStyle(style -> style
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(modifierId))));

        if (disabled && enabled) {
            HarpyModLoaderConfig.HANDLER.instance().disabledModifiers.remove(modifierId);
            HarpyModLoaderConfig.HANDLER.save();

            context.getSource().sendSuccess(
                    () -> Component.translatable("commands.setenabledmodifier.enable.success", modifierName), true);
        } else if (!disabled && !enabled) {
            HarpyModLoaderConfig.HANDLER.instance().disabledModifiers.add(modifierId);
            HarpyModLoaderConfig.HANDLER.save();

            context.getSource().sendSuccess(
                    () -> Component.translatable("commands.setenabledmodifier.disable.success", modifierName), true);
        } else {
            throw ROLE_UNCHANGED_EXCEPTION.create();
        }

        HarpyModLoaderConfig.HANDLER.save();
        return 1;
    }
}
