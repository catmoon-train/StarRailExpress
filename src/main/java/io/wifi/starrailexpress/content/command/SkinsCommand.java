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
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.wifi.starrailexpress.network.OpenSkinScreenPaylod;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.harpymodloader.Harpymodloader;

public class SkinsCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("tmm:skins")
                        .requires((t) -> Harpymodloader.officialVerify)
                        .executes(context -> execute(context.getSource(), null)) // 不指定玩家，默认自己
                        .then(Commands.argument("player", EntityArgument.player())
                                .requires(source -> source.hasPermission(2)) // 需要权限等级2来查看其他玩家
                                .executes(context -> execute(context.getSource(),
                                        EntityArgument.getPlayer(context, "player")))));
    }

    private static int execute(CommandSourceStack source, ServerPlayer player)
            throws CommandSyntaxException {
        ServerPlayer sender = source.getPlayerOrException();

        if (player == null) {
            // 未指定玩家，打开自己的皮肤管理界面
            openSkinScreen(sender);
            source.sendSuccess(() -> Component.translatable("commands.sre.showskin.self"), false);
        } else {
            // 指定玩家，打开指定玩家的皮肤管理界面

            if (player != null) {
                openSkinScreen(player);
                source.sendSuccess(() -> Component.translatable("commands.sre.showskin.other", player.getName()),
                        false);
            }

        }
        return 1;
    }

    private static void openSkinScreen(ServerPlayer player) {
        ServerPlayNetworking.send(player, new OpenSkinScreenPaylod());
    }
}