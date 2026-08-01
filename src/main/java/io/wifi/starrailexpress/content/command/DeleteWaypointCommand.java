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
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import io.wifi.starrailexpress.game.data.WaypointManager;
import io.wifi.starrailexpress.util.WaypointSync;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

public class DeleteWaypointCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // /tmm:deletepoint <path> <name> —— 删除单个路径点
        dispatcher.register(Commands.literal("tmm:deletepoint")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("path", StringArgumentType.string())
                        .suggests((context, builder) -> {
                            WaypointManager manager = WaypointManager.get(context.getSource().getServer());
                            return SharedSuggestionProvider.suggest(
                                    manager.getAllPaths().stream().map(String::valueOf), builder);
                        })
                        .then(Commands.argument("name", StringArgumentType.string())
                                .suggests((context, builder) -> {
                                    String path = StringArgumentType.getString(context, "path");
                                    WaypointManager manager = WaypointManager.get(context.getSource().getServer());
                                    return SharedSuggestionProvider.suggest(
                                            manager.getWaypointsByPath(path).stream().map(wp -> wp.getName()), builder);
                                })
                                .executes(context -> deletePoint(
                                        context,
                                        StringArgumentType.getString(context, "path"),
                                        StringArgumentType.getString(context, "name"))))));

        // /tmm:deletepath <path> —— 删除整条路径下所有点
        dispatcher.register(Commands.literal("tmm:deletepath")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("path", StringArgumentType.string())
                        .suggests((context, builder) -> {
                            WaypointManager manager = WaypointManager.get(context.getSource().getServer());
                            return SharedSuggestionProvider.suggest(
                                    manager.getAllPaths().stream().map(String::valueOf), builder);
                        })
                        .executes(context -> deletePath(
                                context,
                                StringArgumentType.getString(context, "path")))));
    }

    private static int deletePoint(CommandContext<CommandSourceStack> context, String path, String name) {
        CommandSourceStack source = context.getSource();
        WaypointManager manager = WaypointManager.get(source.getServer());

        boolean existed = manager.getWaypointsByPath(path).stream().anyMatch(wp -> wp.getName().equals(name));
        if (!existed) {
            source.sendFailure(Component.literal("路径点不存在: " + path + "/" + name));
            return 0;
        }

        manager.removeWaypoint(path, name);
        WaypointSync.syncToAll(source.getServer());
        source.sendSuccess(() -> Component.literal("已删除路径点: " + path + "/" + name), false);
        return 1;
    }

    private static int deletePath(CommandContext<CommandSourceStack> context, String path) {
        CommandSourceStack source = context.getSource();
        WaypointManager manager = WaypointManager.get(source.getServer());

        if (!manager.getAllPaths().contains(path)) {
            source.sendFailure(Component.literal("路径不存在: " + path));
            return 0;
        }

        manager.removePath(path);
        WaypointSync.syncToAll(source.getServer());
        source.sendSuccess(() -> Component.literal("已删除整条路径: " + path), false);
        return 1;
    }
}
