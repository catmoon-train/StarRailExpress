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

package org.agmas.noellesroles.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.game.roles.neutral.gambler.GamblerHandler;

public class GamblerMiracleCommand {
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("tmm:game")
                    .requires(source -> source.hasPermission(2))
                    .then(Commands.literal("tests")
                            .then(Commands.literal("gambler_miracle")
                                    .executes(GamblerMiracleCommand::executeSelf)
                                    .then(Commands.argument("player", EntityArgument.player())
                                            .executes(GamblerMiracleCommand::executeTarget)))));
        });
    }

    private static int executeSelf(CommandContext<CommandSourceStack> context) {
        ServerPlayer sourcePlayer = context.getSource().getPlayer();
        if (sourcePlayer == null) {
            context.getSource().sendFailure(Component.literal("请指定一个玩家。"));
            return 0;
        }
        return trigger(context, sourcePlayer);
    }

    private static int executeTarget(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer target = EntityArgument.getPlayer(context, "player");
            return trigger(context, target);
        } catch (CommandSyntaxException e) {
            context.getSource().sendFailure(Component.literal("无法找到目标玩家。"));
            return 0;
        }
    }

    private static int trigger(CommandContext<CommandSourceStack> context, ServerPlayer target) {
        GamblerHandler.triggerOnePercentMiracle(target.serverLevel(), target);
        context.getSource().sendSuccess(
                () -> Component.literal("已触发赌徒 1% 奇迹效果: " + target.getGameProfile().getName()),
                true);
        return Command.SINGLE_SUCCESS;
    }
}
