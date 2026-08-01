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
import com.mojang.brigadier.arguments.IntegerArgumentType;
import io.wifi.starrailexpress.cca.AutoStartComponent;
import io.wifi.starrailexpress.game.GameConstants;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public class AutoStartCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("tmm:autoStart")
                        .requires(source -> source.hasPermission(2))
                        .then(
                                Commands.argument("seconds", IntegerArgumentType.integer(0, 60))
                                        .executes(context -> setAutoStart(context.getSource(),
                                                IntegerArgumentType.getInteger(context, "seconds")))));
    }

    private static int setAutoStart(CommandSourceStack source, int seconds) {
        AutoStartComponent.KEY.get(source.getLevel()).setStartTime(GameConstants.getInTicks(0, seconds));
        if (seconds > 0) {
            source.sendSuccess(
                    () -> Component.translatable("commands.sre.autostart.enabled", seconds)
                            .withStyle(style -> style.withColor(0x00FF00)),
                    true);
        } else {
            source.sendSuccess(
                    () -> Component.translatable("commands.sre.autostart.disabled")
                            .withStyle(style -> style.withColor(0x00FF00)),
                    true);
        }
        return 1;
    }
}
