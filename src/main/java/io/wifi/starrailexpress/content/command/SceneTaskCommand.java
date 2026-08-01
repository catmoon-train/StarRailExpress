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

package io.wifi.starrailexpress.content.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import org.agmas.noellesroles.scene.SceneTaskManager;

/**
 * 场景任务指派指令 /sre:scenetask。
 */
public final class SceneTaskCommand {
    private SceneTaskCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var give = Commands.literal("give");
        for (SceneTaskManager.Type type : SceneTaskManager.Type.values()) {
            give.then(Commands.literal(type.name().toLowerCase())
                    .then(Commands.argument("player", EntityArgument.player())
                            .executes(ctx -> assign(ctx, type))));
        }
        dispatcher.register(Commands.literal("sre:scenetask")
                .requires(source -> source.hasPermission(2))
                .then(give)
                .then(Commands.literal("clear")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(SceneTaskCommand::clear))));
    }

    private static int assign(CommandContext<CommandSourceStack> ctx, SceneTaskManager.Type type) {
        try {
            ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
            SceneTaskManager.assign(player, type);
            ctx.getSource().sendSuccess(
                    () -> Component.literal("已为 " + player.getName().getString() + " 指派场景任务: " + type), true);
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("指派失败: " + e.getMessage()));
            return 0;
        }
    }

    private static int clear(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
            SceneTaskManager.clear(player);
            ctx.getSource().sendSuccess(() -> Component.literal("已清除 " + player.getName().getString() + " 的场景任务"),
                    true);
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("清除失败: " + e.getMessage()));
            return 0;
        }
    }
}
