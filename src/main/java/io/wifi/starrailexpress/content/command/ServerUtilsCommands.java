package io.wifi.starrailexpress.content.command;

import java.util.Collection;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.util.CustomMotdManager;
import io.wifi.starrailexpress.util.MutableMaxPlayer;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.Commands.CommandSelection;
import net.minecraft.commands.arguments.ComponentArgument;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.server.players.ServerOpList;
import net.minecraft.server.players.ServerOpListEntry;

public class ServerUtilsCommands {

  public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext registryAccess,
      CommandSelection environment) {
    dispatcher.register(Commands.literal("sre:my_permission")
        .executes((ctx) -> showPlayerPermission(ctx, ctx.getSource().getPlayerOrException().getGameProfile())));
    dispatcher.register(Commands.literal("sre:server")
        .requires(p -> p.hasPermission(2))
        .executes((ctx) -> listServerInfo(ctx, false))
        .then(Commands.literal("permission")
            .then(Commands.argument("player", GameProfileArgument.gameProfile())
                .then(Commands.literal("get")
                    .executes((ctx) -> {
                      Collection<GameProfile> profiles = GameProfileArgument.getGameProfiles(ctx,
                          "player");
                      return showPlayerPermission(ctx,
                          profiles.stream().findFirst().orElse(null));
                    }))
                .then(Commands.literal("set")
                    .requires(p -> p.hasPermission(3))
                    .then(Commands.argument("level", IntegerArgumentType.integer(0, 4))
                        .executes((ctx) -> {
                          Collection<GameProfile> profiles = GameProfileArgument
                              .getGameProfiles(ctx, "player");
                          return changePlayerPermission(ctx,
                              profiles.stream().findFirst().orElse(null),
                              IntegerArgumentType.getInteger(ctx, "level"), false);
                        })
                        .then(Commands.argument("bypassPlayerLimit", BoolArgumentType.bool())
                            .executes((ctx) -> {
                              Collection<GameProfile> profiles = GameProfileArgument
                                  .getGameProfiles(ctx, "player");
                              return changePlayerPermission(ctx,
                                  profiles.stream().findFirst().orElse(null),
                                  IntegerArgumentType.getInteger(ctx, "level"),
                                  BoolArgumentType.getBool(ctx, "bypassPlayerLimit"));
                            }))))))
        .then(Commands.literal("motd")
            .then(Commands.literal("get")
                .executes((ctx) -> listServerInfo(ctx, true)))
            .then(Commands.literal("reset")
                .requires(p -> p.hasPermission(3))
                .executes((ctx) -> setCustomMotd(ctx, null)))
            .then(Commands.literal("set")
                .requires(p -> p.hasPermission(3))
                .then(Commands.argument("message", ComponentArgument.textComponent(registryAccess))
                    .executes(ctx -> setCustomMotd(ctx,
                        ComponentArgument.getComponent(ctx, "message"))))))
        .then(Commands.literal("max_player")
            .executes(ServerUtilsCommands::getMaxPlayers)
            .then(Commands.literal("get")
                .executes(ServerUtilsCommands::getMaxPlayers))
            .then(Commands.literal("set")
                .requires(p -> p.hasPermission(3))
                .then(Commands.argument("count", IntegerArgumentType.integer(0))
                    .executes(ServerUtilsCommands::setMaxPlayers)))));
  }

  private static int getPermissionLevel(MinecraftServer server, GameProfile profile) {
    return server.getProfilePermissions(profile);
  }

  private static int getPermissionLevel(MinecraftServer server, ServerPlayer player) {
    return getPermissionLevel(server, player.getGameProfile());
  }

  private static int changePlayerPermission(CommandContext<CommandSourceStack> ctx, GameProfile gameProfile,
      final int newPermissionLevel,
      final boolean bypassPlayerLimit) {
    final var server = ctx.getSource().getServer();
    int runnerPermission = ctx.getSource().isPlayer() ? getPermissionLevel(server, ctx.getSource().getPlayer()) : 4;
    if (gameProfile == null) {
      return 0;
    }
    if (server.isSingleplayerOwner(gameProfile) || server.isSingleplayer()) {
      ctx.getSource().sendFailure(
          Component.translatable("message.serverutils.permission.set.failed", gameProfile.getName(),
              Component.translatable("message.serverutils.permission.set.failed.singleplayer")));
      return 4;
    }
    if (newPermissionLevel > runnerPermission) {
      ctx.getSource().sendFailure(
          Component.translatable("message.serverutils.permission.set.failed", gameProfile.getName(),
              Component.translatable("message.serverutils.permission.set.failed.permission_low", runnerPermission,
                  newPermissionLevel)));
      return 0;
    }
    final int permissionLevelBefore = server.getProfilePermissions(gameProfile);
    var t = Component.translatable("message.serverutils.permission.set.success", gameProfile.getName(),
        permissionLevelBefore, newPermissionLevel);
    final PlayerList playerList = server.getPlayerList();
    final ServerOpList ops = playerList.getOps();
    if (newPermissionLevel >= 1) {
      if (playerList.isOp(gameProfile)) {
        ops.remove(gameProfile);
      }
      ops.add(new ServerOpListEntry(gameProfile, newPermissionLevel, bypassPlayerLimit)); // 自己会保存
    } else {
      if (playerList.isOp(gameProfile)) {
        ops.remove(gameProfile); // 自己会保存
      }
      if (bypassPlayerLimit) {
        ops.add(new ServerOpListEntry(gameProfile, newPermissionLevel, bypassPlayerLimit));
      }
    }
    if (bypassPlayerLimit) {
      t = t.append(Component.translatable("message.serverutils.permission.allow_pass"));
    }
    final var result = t;
    ctx.getSource().sendSuccess(
        () -> result,
        true);

    ServerPlayer serverPlayer = playerList.getPlayer(gameProfile.getId());
    if (serverPlayer != null) {
      playerList.sendPlayerPermissionLevel(serverPlayer);
    }
    return permissionLevelBefore;
  }

  private static int showPlayerPermission(CommandContext<CommandSourceStack> ctx, GameProfile gameProfile) {
    if (gameProfile == null) {
      return 0;
    }
    final var server = ctx.getSource().getServer();
    final int permissionLevel = server.getProfilePermissions(gameProfile);
    ServerOpList opList = server.getPlayerList().getOps();
    boolean canBypass = opList.canBypassPlayerLimit(gameProfile);
    MutableComponent t = Component.translatable("message.serverutils.permission.get.success",
        gameProfile.getName(),
        permissionLevel);
    if (canBypass) {
      t = t.append(Component.translatable("message.serverutils.permission.allow_pass"));
    }
    final var result = t;
    ctx.getSource().sendSuccess(
        () -> result,
        false);
    return permissionLevel;
  }

  public static int listServerInfo(CommandContext<CommandSourceStack> context, boolean onlyMotd) {

    CommandSourceStack source = context.getSource();
    MinecraftServer server = source.getServer();

    if (server == null) {
      source.sendFailure(Component.translatable("message.serverutils.server_not_available"));
      return 0;
    }
    int maxPlayers = server.getMaxPlayers();
    int currentPlayers = server.getPlayerList().getPlayers().size();
    Component motd = Component.literal("").withStyle(ChatFormatting.WHITE).append(CustomMotdManager.getMotd());
    Component customMotdPattern = Component.literal("").withStyle(ChatFormatting.WHITE)
        .append(CustomMotdManager.getCustomMotdPattern());
    var t = Component.translatable("message.serverutils.info.motd", motd,
        customMotdPattern,
        onlyMotd ? Component.translatable("message.serverutils.motd.constant")
            .withStyle(ChatFormatting.GRAY) : Component.empty())
        .withStyle(ChatFormatting.YELLOW);
    if (!onlyMotd) {
      t = Component
          .translatable("message.serverutils.info",
              Component.literal(currentPlayers + "").withStyle(ChatFormatting.WHITE),
              Component.literal(maxPlayers + "").withStyle(ChatFormatting.GREEN), t)
          .withStyle(ChatFormatting.GOLD);
    }
    final var result = t;
    source.sendSuccess(
        () -> result,
        false);

    return 1;
  }

  /**
   * Handles the mw:maxplayers get command
   * Displays the current maximum player count
   */
  public static int getMaxPlayers(CommandContext<CommandSourceStack> context) {
    CommandSourceStack source = context.getSource();
    MinecraftServer server = source.getServer();

    if (server == null) {
      source.sendFailure(Component.translatable("message.serverutils.server_not_available"));
      return 0;
    }

    int maxPlayers = server.getMaxPlayers();
    int currentPlayers = server.getPlayerList().getPlayers().size();

    source.sendSuccess(
        () -> Component
            .translatable("message.serverutils.max_player.now",
                Component.literal(currentPlayers + "").withStyle(ChatFormatting.WHITE),
                Component.literal(maxPlayers + "").withStyle(ChatFormatting.GREEN))
            .withStyle(ChatFormatting.GOLD),
        false);

    return Command.SINGLE_SUCCESS;
  }

  public static int setCustomMotd(CommandContext<CommandSourceStack> context, Component motd) {
    CustomMotdManager.setCustomMotd(motd);
    context.getSource()
        .sendSuccess(
            () -> Component.translatable("message.serverutils.motd.set",
                Component.literal("").withStyle(ChatFormatting.WHITE).append(motd))
                .withStyle(ChatFormatting.GREEN),
            true);
    return 1;
  }

  /**
   * Handles the mw:maxplayers set command
   * Sets the maximum player count
   */
  public static int setMaxPlayers(CommandContext<CommandSourceStack> context) {
    CommandSourceStack source = context.getSource();
    MinecraftServer server = source.getServer();
    int newMaxPlayers = IntegerArgumentType.getInteger(context, "count");

    if (server == null) {
      source.sendFailure(Component.translatable("message.serverutils.server_not_available"));
      return 0;
    }

    int oldMaxPlayers = server.getMaxPlayers();

    try {
      int maxPlayers = newMaxPlayers;
      PlayerList playerList = server.getPlayerList();
      // 使用反射修改PlayerList中的maxPlayers字段
      try {
        // 修改最大玩家数
        ((MutableMaxPlayer) playerList).setMaxPlayers(maxPlayers);
      } catch (Exception e) {
        source.sendFailure(Component.translatable("message.serverutils.max_player.set.failed", e.getMessage()));

        SRE.LOGGER.error("Error setting max players", e);
        return 0;
      }

      source.sendSuccess(
          () -> Component
              .translatable("message.serverutils.max_player.set.success",
                  Component.literal("" + oldMaxPlayers).withStyle(ChatFormatting.ITALIC,
                      ChatFormatting.RED, ChatFormatting.STRIKETHROUGH),
                  Component.literal("" + newMaxPlayers).withStyle(ChatFormatting.AQUA))
              .withStyle(ChatFormatting.GREEN),
          true);
      String playerName = source.getDisplayName().getString();
      SRE.LOGGER
          .info("Max players changed from " + oldMaxPlayers + " to " + newMaxPlayers + " by " + playerName);

      return Command.SINGLE_SUCCESS;
    } catch (Exception e) {
      source.sendFailure(Component.translatable("message.serverutils.max_player.set.failed", e.getMessage()));
      SRE.LOGGER.error("Error setting max players", e);
      return 0;
    }
  }
}
