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
import io.wifi.starrailexpress.progression.ProgressionDataManager;
import io.wifi.starrailexpress.progression.ProgressionState;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class ProgressionCommand {
    private ProgressionCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("sre:pass")
                .then(Commands.literal("activate")
                        .then(Commands.argument("type", StringArgumentType.word())
                                .executes(context -> activate(
                                        context.getSource().getPlayerOrException(),
                                        StringArgumentType.getString(context, "type"))))));
    }

    private static int activate(ServerPlayer player, String rawType) {
        ProgressionState.FactionCardType type = ProgressionState.FactionCardType.fromString(rawType);
        if (!ProgressionDataManager.activateFactionCard(player, type)) {
            player.displayClientMessage(Component.translatable("message.sre.pass.faction.assign_failed"), true);
            return 0;
        }
        return 1;
    }
}
