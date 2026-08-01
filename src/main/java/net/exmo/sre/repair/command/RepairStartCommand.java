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

package net.exmo.sre.repair.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;

import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.api.SREGameModes;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class RepairStartCommand {
    private RepairStartCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                literal("cy:repair")
                        .requires(source -> source.hasPermission(SREConfig.instance().startGameRequiredPermission))
                        .then(literal("start")
                                .then(argument("minutes", IntegerArgumentType.integer(1))
                                        .executes(context -> start(context.getSource(),
                                                IntegerArgumentType.getInteger(context, "minutes"))))
                                .executes(context -> start(context.getSource(), -1)))));
    }

    private static int start(net.minecraft.commands.CommandSourceStack source, int minutes) {
        if (SREGameWorldComponent.KEY.get(source.getLevel()).isRunning()) {
            source.sendFailure(Component.translatable("game.start_error.game_running"));
            return 0;
        }
        if (!SREConfig.instance().enableRepairMode) {
            source.sendFailure(Component.translatable("game.start_error.disabled_gamemode", SREGameModes.REPAIR_ESCAPE_MODE.getName(),
                    Component.translatable("text.autoconfig.starrailexpress.option.enableRepairMode"),
                    "enableRepairMode",
                    "true"));
            return -1;
        }
        int resolved = minutes >= 0 ? minutes : SREGameModes.REPAIR_ESCAPE_MODE.defaultStartTime;
        GameUtils.startGame(source.getLevel(), SREGameModes.REPAIR_ESCAPE_MODE, GameConstants.getInTicks(resolved, 0));
        source.sendSuccess(
                () -> Component.translatable("commands.sre.start", SREGameModes.REPAIR_ESCAPE_MODE.toString(), resolved)
                        .withStyle(ChatFormatting.GREEN),
                true);
        return 1;
    }
}
