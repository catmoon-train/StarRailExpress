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
import org.agmas.harpymodloader.Harpymodloader;

public class SetBoundCommand {
  // 是否限制玩家在旁观区域
  public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
    dispatcher.register(Commands.literal("tmm:game")
        .requires(source -> Harpymodloader.officialVerify && source.hasPermission(2))
        .then(Commands.literal("bounds")
            .then(Commands.argument("enabled", BoolArgumentType.bool())
                .executes(context -> execute(context.getSource(),
                    BoolArgumentType.getBool(context,
                        "enabled"))))));
  }

  private static int execute(CommandSourceStack source, boolean enabled) {

    SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(source.getLevel());
    gameWorldComponent.setBound(enabled);

    if (enabled) {
      source.sendSuccess(
          () -> Component.translatable("commands.sre.setbound.enabled")
              .withStyle(style -> style.withColor(0x00FF00)),
          true);
    } else {
      source.sendSuccess(
          () -> Component.translatable("commands.sre.setbound.disabled")
              .withStyle(style -> style.withColor(0x00FF00)),
          true);
    }
    return 1;
  }

}
