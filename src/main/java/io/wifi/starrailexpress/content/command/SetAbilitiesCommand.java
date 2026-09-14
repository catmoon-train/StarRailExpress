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
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Abilities;
import org.agmas.harpymodloader.Harpymodloader;

public class SetAbilitiesCommand {
  private static final int COLOR_OK = 0x00FF00;

  // /tmm:game abilities <player> ...
  public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
    dispatcher.register(Commands.literal("tmm:game")
        .requires(source -> Harpymodloader.officialVerify
            && source.hasPermission(SREConfig.instance().gameAbilitiesRequiredPermission))
        .then(Commands.literal("abilities")
            // tmm:game 是多个注册点合并出来的，根节点的 requires 由最先注册者决定，
            // 这里必须在自己的子节点上再声明一次，权限才会真正生效
            .requires(source -> Harpymodloader.officialVerify
                && source.hasPermission(SREConfig.instance().gameAbilitiesRequiredPermission))
            .then(Commands.argument("player", EntityArgument.player())
                .executes(SetAbilitiesCommand::query)
                .then(Commands.literal("reset").executes(SetAbilitiesCommand::reset))
                .then(Commands.literal("mayfly")
                    .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(context -> set(context, "mayfly",
                            (abilities, ctx) -> abilities.mayfly = BoolArgumentType.getBool(ctx, "value")))))
                .then(Commands.literal("flying")
                    .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(context -> set(context, "flying",
                            (abilities, ctx) -> abilities.flying = BoolArgumentType.getBool(ctx, "value")))))
                .then(Commands.literal("instabuild")
                    .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(context -> set(context, "instabuild",
                            (abilities, ctx) -> abilities.instabuild = BoolArgumentType.getBool(ctx, "value")))))
                .then(Commands.literal("invulnerable")
                    .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(context -> set(context, "invulnerable",
                            (abilities, ctx) -> abilities.invulnerable = BoolArgumentType.getBool(ctx, "value")))))
                .then(Commands.literal("flying_speed")
                    .then(Commands.argument("value", FloatArgumentType.floatArg(0.0F, 10.0F))
                        .executes(context -> set(context, "flying_speed",
                            (abilities, ctx) -> abilities.setFlyingSpeed(
                                FloatArgumentType.getFloat(ctx, "value"))))))
                .then(Commands.literal("walk_speed")
                    .then(Commands.argument("value", FloatArgumentType.floatArg(0.0F, 10.0F))
                        .executes(context -> set(context, "walk_speed",
                            (abilities, ctx) -> abilities.setWalkingSpeed(
                                FloatArgumentType.getFloat(ctx, "value")))))))));
  }

  /** 写入一个 abilities 字段。 */
  private interface AbilityWriter {
    void write(Abilities abilities, CommandContext<CommandSourceStack> context);
  }

  private static int set(CommandContext<CommandSourceStack> context, String field, AbilityWriter writer)
      throws CommandSyntaxException {
    ServerPlayer target = EntityArgument.getPlayer(context, "player");
    writer.write(target.getAbilities(), context);
    target.onUpdateAbilities();
    feedback(context, target, field, String.valueOf(read(target.getAbilities(), field)));
    return 1;
  }

  private static int reset(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
    ServerPlayer target = EntityArgument.getPlayer(context, "player");
    if (target.isSpectator()) {
      // 旁观者必须保留飞行权限，否则会把玩家卡成"旁观却飞不起来"
      GameUtils.normalizeSpectatorFlightAbilities(target);
    } else {
      GameUtils.resetPlayerAbilities(target);
    }
    context.getSource().sendSuccess(() -> Component.translatable(
        "commands.sre.abilities.reset", target.getDisplayName())
        .withStyle(style -> style.withColor(COLOR_OK)), true);
    return 1;
  }

  private static int query(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
    ServerPlayer target = EntityArgument.getPlayer(context, "player");
    Abilities abilities = target.getAbilities();
    CommandSourceStack source = context.getSource();
    source.sendSuccess(() -> Component.translatable(
        "commands.sre.abilities.header", target.getDisplayName())
        .withStyle(style -> style.withColor(COLOR_OK)), false);
    for (String field : FIELDS) {
      source.sendSuccess(() -> Component.translatable(
          "commands.sre.abilities.entry", field, String.valueOf(read(abilities, field))), false);
    }
    return 1;
  }

  private static final String[] FIELDS = {
      "mayfly", "flying", "instabuild", "invulnerable", "flying_speed", "walk_speed"
  };

  private static Object read(Abilities abilities, String field) {
    return switch (field) {
      case "mayfly" -> abilities.mayfly;
      case "flying" -> abilities.flying;
      case "instabuild" -> abilities.instabuild;
      case "invulnerable" -> abilities.invulnerable;
      case "flying_speed" -> abilities.getFlyingSpeed();
      case "walk_speed" -> abilities.getWalkingSpeed();
      default -> "?";
    };
  }

  private static void feedback(CommandContext<CommandSourceStack> context, ServerPlayer target, String field,
      String value) {
    context.getSource().sendSuccess(() -> Component.translatable(
        "commands.sre.abilities.set", target.getDisplayName(), field, value)
        .withStyle(style -> style.withColor(COLOR_OK)), true);
  }
}
