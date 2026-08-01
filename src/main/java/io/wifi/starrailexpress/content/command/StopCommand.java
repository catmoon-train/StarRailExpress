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
import com.mojang.brigadier.tree.LiteralCommandNode;

import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import org.agmas.harpymodloader.Harpymodloader;
import org.jetbrains.annotations.NotNull;

public class StopCommand {
    public static LiteralCommandNode<CommandSourceStack> STOP_COMMAND_NODE;

    public static void register(@NotNull CommandDispatcher<CommandSourceStack> dispatcher) {
        STOP_COMMAND_NODE = dispatcher.register(Commands.literal("tmm:stop")
                .requires(source -> Harpymodloader.officialVerify
                        && source.hasPermission(SREConfig.instance().stopGameRequiredPermission))
                .then(Commands.literal("force").executes(context -> {
                    GameUtils.finalizeGame(context.getSource().getLevel());
                    return 1;
                }))
                .executes(context -> {
                    GameUtils.stopGame(context.getSource().getLevel());
                    context.getSource().sendSuccess(
                            () -> Component.translatable("commands.sre.stop")
                                    .withStyle(style -> style.withColor(0x00FF00)),
                            true);
                    return 1;
                }));
    }
}