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

package net.exmo.sre.dummy;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class DummyCommand {

  private static boolean lifecycleRegistered = false;

  private DummyCommand() {
  }

  public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
    if (!lifecycleRegistered) {
      lifecycleRegistered = true;
      ServerLifecycleEvents.SERVER_STARTED.register(DummyManager::onServerStarted);
    }
    dispatcher.register(Commands.literal("sre:npc")
        .requires(source -> source.hasPermission(2))
        .then(Commands.literal("add")
            .then(Commands.argument("skin", StringArgumentType.word())
                .then(Commands.argument("name", StringArgumentType.word())
                    .executes(
                        ctx -> spawn(ctx.getSource(), StringArgumentType.getString(ctx, "skin"),
                            StringArgumentType.getString(ctx, "name"), true))
                    .then(Commands.argument("invincible", BoolArgumentType.bool())
                        .executes(ctx -> spawn(ctx.getSource(),
                            StringArgumentType.getString(ctx, "skin"),
                            StringArgumentType.getString(ctx, "name"),
                            BoolArgumentType.getBool(ctx, "invincible")))))))
        .then(Commands.literal("remove")
            .then(Commands.argument("name", StringArgumentType.word())
                .executes(ctx -> {
                  String name = StringArgumentType.getString(ctx, "name");
                  boolean removed = DummyManager.remove(name);
                  Component nameComponent = Component.literal(name); // 继承外层颜色
                  ctx.getSource().sendSuccess(
                      () -> Component.translatable(
                          removed ? "sre.command.dummy.remove.success"
                              : "sre.command.dummy.remove.not_found",
                          nameComponent)
                          .withStyle(removed ? ChatFormatting.GREEN : ChatFormatting.RED),
                      true);
                  return removed ? 1 : 0;
                })))
        .then(Commands.literal("list")
            .executes(ctx -> {
              if (DummyManager.all().isEmpty()) {
                ctx.getSource().sendSuccess(
                    () -> Component.translatable("sre.command.dummy.list.empty")
                        .withStyle(ChatFormatting.GRAY),
                    false);
              } else {
                for (DummyEntity dummy : DummyManager.all()) {
                  Component label = Component.literal(dummy.label()).withStyle(ChatFormatting.GREEN);
                  Component skin = Component.literal(dummy.skinOwner())
                      .withStyle(ChatFormatting.GRAY);
                  Component invincibleSuffix = dummy.invincible()
                      ? Component.translatable("sre.dummy.invincible_suffix")
                          .withStyle(ChatFormatting.GRAY)
                      : Component.empty();
                  ctx.getSource().sendSuccess(
                      () -> Component
                          .translatable("sre.command.dummy.list.entry", label, skin,
                              invincibleSuffix)
                          .withStyle(ChatFormatting.GRAY),
                      false);
                }
              }
              return DummyManager.all().size();
            })));
  }

  private static int spawn(CommandSourceStack source, String skin, String name, boolean invincible) {
    ServerPlayer player = source.getPlayer();
    if (player == null) {
      source.sendFailure(
          Component.translatable("sre.command.dummy.player_only").withStyle(ChatFormatting.RED));
      return 0;
    }
    DummyManager.spawn(player.serverLevel(), player.position(), player.getYRot(), player.getXRot(),
        skin, name, invincible, true);

    Component nameComp = Component.literal(name).withStyle(ChatFormatting.WHITE);
    Component skinComp = Component.literal(skin).withStyle(ChatFormatting.GREEN);
    Component invincibleSuffix = invincible
        ? Component.translatable("sre.dummy.invincible_suffix").withStyle(ChatFormatting.GREEN)
        : Component.empty();

    source.sendSuccess(
        () -> Component.translatable("sre.command.dummy.add.success", nameComp, skinComp, invincibleSuffix)
            .withStyle(ChatFormatting.GREEN),
        true);
    return 1;
  }
}