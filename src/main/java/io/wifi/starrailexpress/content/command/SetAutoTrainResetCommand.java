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
import com.mojang.brigadier.arguments.BoolArgumentType;
import io.wifi.starrailexpress.SREConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public class SetAutoTrainResetCommand {
  public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
    dispatcher.register(
        Commands.literal("tmm:config")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("autoTrainReset")
                .then(
                    Commands.argument("enabled", BoolArgumentType.bool())
                        .executes(
                            context -> execute(context.getSource(), BoolArgumentType.getBool(context, "enabled"))))));
  }

  private static int execute(CommandSourceStack source, boolean enabled) {
    SREConfig.instance().enableAutoTrainReset = enabled;
    SREConfig.HANDLER.save();

    source.sendSuccess(
        () -> Component.translatable("commands.sre.setautotrainreset", enabled)
            .withStyle(style -> style.withColor(0x00FF00)),
        true);
    return 1;
  }
}