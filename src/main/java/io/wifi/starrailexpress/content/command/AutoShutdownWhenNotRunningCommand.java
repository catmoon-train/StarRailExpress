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
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

public class AutoShutdownWhenNotRunningCommand {
  public static boolean autoShutdownWhenGameNotRunning = false;

  public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
    dispatcher.register(
        Commands.literal("stop_when_over")
            .requires(t -> t.hasPermission(4))
            .then(
                Commands.argument("enabled", BoolArgumentType.bool())
                    .executes(context -> execute(context.getSource(),
                        BoolArgumentType.getBool(context, "enabled")))));
  }

  private static int execute(CommandSourceStack source, boolean enabled) {
    autoShutdownWhenGameNotRunning = enabled;
    ServerLevel level = source.getLevel();
    if (!SREGameWorldComponent.KEY.get(level).isRunning()){
      level.getServer().halt(false);
    }
    source.sendSuccess(
        () -> Component.literal("Auto stop server: " + (enabled ? "On" : "Off")),
        true);
    return 1;
  }
}