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
import com.mojang.brigadier.context.CommandContext;

import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.network.OpenRoleRosterScreenPayload;
import io.wifi.starrailexpress.roster.RoleRosterManager;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * 职业轮换系统指令：
 * <ul>
 * <li>{@code /sre:roster} —— 打开玩家查看界面（任意玩家）。</li>
 * <li>{@code /sre:roster edit} —— 打开管理员编辑界面（OP）。</li>
 * <li>{@code /sre:roster enable|disable} —— 开关名单是否接管职业的启用/禁用（OP）。</li>
 * <li>{@code /sre:roster status} —— 查看当前状态（OP）。</li>
 * </ul>
 */
public final class RoleRosterCommand {
  private RoleRosterCommand() {
  }

  public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
    dispatcher.register(Commands.literal("sre:roster")
        .executes(ctx -> openScreen(ctx, false))
        .then(Commands.literal("reload")
            .requires(source -> source.hasPermission(SREConfig.instance().rosterCommandPermission))
            .executes(ctx -> {
              if (!RoleRosterManager.loadDataFromFile()) {
                ctx.getSource().sendFailure(
                    Component.literal("Failed to reload roster data from local file!"));
                return 0;
              }
              ctx.getSource().sendSuccess(
                  () -> Component.literal("Success reload roster data from local file!"), true);
              return 1;
            }))
        .then(Commands.literal("download")
            .requires(source -> source.hasPermission(SREConfig.instance().rosterCommandPermission))
            .executes(ctx -> {
              if (!RoleRosterManager.loadDataFromServer()) {
                ctx.getSource().sendFailure(
                    Component.literal("Failed to download roster data from database!"));
                return 0;
              }
              ctx.getSource().sendSuccess(
                  () -> Component.literal("Success download roster data from database!"), true);
              return 1;
            }))
        .then(Commands.literal("edit")
            .requires(source -> source.hasPermission(SREConfig.instance().rosterCommandPermission))
            .executes(ctx -> openScreen(ctx, true)))
        .then(Commands.literal("enable")
            .requires(source -> source.hasPermission(SREConfig.instance().rosterCommandPermission))
            .executes(ctx -> setEnabled(ctx, true)))
        .then(Commands.literal("random")
            .requires(source -> source.hasPermission(SREConfig.instance().rosterCommandPermission))
            .then(Commands.literal("custom")
                .then(Commands.argument("innocent_count", IntegerArgumentType.integer(0))
                    .then(Commands.argument("vigilante_count", IntegerArgumentType.integer(0))
                        .then(Commands.argument("neutrals_count", IntegerArgumentType.integer(0))
                            .then(Commands.argument("killer_count", IntegerArgumentType.integer(0))
                                .then(Commands.argument("modifier_count", IntegerArgumentType.integer(0))
                                    .executes(ctx -> {
                                      setEnabled(ctx, true);
                                      setCustomRandomRoles(ctx, IntegerArgumentType.getInteger(ctx, "innocent_count"),
                                          IntegerArgumentType.getInteger(ctx, "killer_count"),
                                          IntegerArgumentType.getInteger(ctx, "vigilante_count"),
                                          IntegerArgumentType.getInteger(ctx, "neutrals_count"),
                                          IntegerArgumentType.getInteger(ctx, "modifier_count"), false);
                                      return 1;
                                    })
                                    .then(Commands.literal("force")
                                        .executes(ctx -> {
                                          setEnabled(ctx, true);
                                          setCustomRandomRoles(ctx,
                                              IntegerArgumentType.getInteger(ctx, "innocent_count"),
                                              IntegerArgumentType.getInteger(ctx, "killer_count"),
                                              IntegerArgumentType.getInteger(ctx, "vigilante_count"),
                                              IntegerArgumentType.getInteger(ctx, "neutrals_count"),
                                              IntegerArgumentType.getInteger(ctx, "modifier_count"), true);
                                          return 1;
                                        }))))))))
            .then(Commands.argument("role_count", IntegerArgumentType.integer(0))
                .then(Commands.argument("modifier_count", IntegerArgumentType.integer(0))
                    .executes(ctx -> {
                      setEnabled(ctx, true);
                      setRandomRoles(ctx, IntegerArgumentType.getInteger(ctx, "role_count"),
                          IntegerArgumentType.getInteger(ctx, "modifier_count"), false);
                      return 1;
                    })
                    .then(Commands.literal("force")
                        .executes(ctx -> {
                          setEnabled(ctx, true);
                          setRandomRoles(ctx, IntegerArgumentType.getInteger(ctx, "role_count"),
                              IntegerArgumentType.getInteger(ctx, "modifier_count"), true);
                          return 1;
                        })))))
        .then(Commands.literal("disable")
            .requires(source -> source.hasPermission(SREConfig.instance().rosterCommandPermission))
            .executes(ctx -> setEnabled(ctx, false)))
        .then(Commands.literal("status")
            .requires(source -> source.hasPermission(2))
            .executes(RoleRosterCommand::status)));
  }

  private static void setCustomRandomRoles(CommandContext<CommandSourceStack> ctx, int innocent, int killer,
      int vigilante, int neutrals, int modifierCount,
      boolean force) {
    final var source = ctx.getSource();
    RoleRosterManager.randomRoster(innocent, killer, vigilante, neutrals, modifierCount, force);
    source.sendSuccess(() -> Component.literal("Successfully set the random RoleRoster."), true);
  }

  private static void setRandomRoles(CommandContext<CommandSourceStack> ctx, int roleCount, int modifierCount,
      boolean force) {
    final var source = ctx.getSource();
    RoleRosterManager.randomRoster(roleCount, modifierCount, force);
    source.sendSuccess(() -> Component.literal("Successfully set the random RoleRoster."), true);
  }

  private static int openScreen(CommandContext<CommandSourceStack> ctx, boolean admin) {
    ServerPlayer player;
    try {
      player = ctx.getSource().getPlayerOrException();
    } catch (Exception e) {
      ctx.getSource().sendFailure(Component.translatable("commands.sre.role_roster.not_player"));
      return 0;
    }
    ServerPlayNetworking.send(player, new OpenRoleRosterScreenPayload(admin));
    return 1;
  }

  private static int setEnabled(CommandContext<CommandSourceStack> ctx, boolean enabled) {
    RoleRosterManager.setEnabled(enabled);
    ctx.getSource().sendSuccess(() -> Component.translatable(
        enabled ? "commands.sre.role_roster.enabled" : "commands.sre.role_roster.disabled"), true);
    return 1;
  }

  private static int status(CommandContext<CommandSourceStack> ctx) {
    var state = RoleRosterManager.getState();
    ctx.getSource().sendSuccess(() -> Component.translatable("commands.sre.role_roster.status",
        state.enabled ? "ON" : "OFF", state.roleCounts.size()), false);
    return 1;
  }
}
