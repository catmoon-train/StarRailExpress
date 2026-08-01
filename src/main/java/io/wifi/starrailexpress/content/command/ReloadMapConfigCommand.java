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
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.game.data.ServerMapConfig;
import io.wifi.starrailexpress.network.SyncMapConfigPayload;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public class ReloadMapConfigCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("tmm:reload")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("vote_map_config").executes(ReloadMapConfigCommand::reloadMapConfig)));
    }

    private static int reloadMapConfig(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        try {
            ServerMapConfig.reload(context.getSource().getServer());
            source.sendSuccess(
                    () -> Component.translatable("commands.sre.reloadmapconfig.success")
                            .withStyle(style -> style.withColor(0x00FF00)),
                    true);
            SRE.LOGGER.info("Map config is reloaded by {}!", source.getTextName());
            for (var serverPlayer : context.getSource().getServer().getPlayerList().getPlayers()) {
                SyncMapConfigPayload.sendToPlayer(serverPlayer);
            }
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.translatable("commands.sre.reloadmapconfig.fail", e.getMessage()));
            SRE.LOGGER.error("Map config reload failed.", e);
            e.printStackTrace();
            return 0;
        }

    }
}