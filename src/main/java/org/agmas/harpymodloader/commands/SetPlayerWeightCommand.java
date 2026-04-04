package org.agmas.harpymodloader.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.modded_murder.PlayerRoleWeightManager;

import java.util.concurrent.CompletableFuture;

public class SetPlayerWeightCommand {

  public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
    dispatcher.register(Commands.literal("myRoleWeight")
        .executes(context -> executeGet(context.getSource(),
            context.getSource().getPlayer(),
            0)));
    dispatcher.register(Commands.literal("playerRoleWeight")
        .requires(serverCommandSource -> serverCommandSource.hasPermission(2))
        .then(Commands.argument("player", EntityArgument.player())
            .then(Commands.literal("get")
                .executes(context -> executeGet(context.getSource(),
                    EntityArgument.getPlayer(context, "player"),
                    0))
                .then(Commands.argument("role", IntegerArgumentType.integer(0, 5))
                    .suggests(SetPlayerWeightCommand::suggestRoleType)
                    .executes(context -> executeGet(context.getSource(),
                        EntityArgument.getPlayer(context, "player"),
                        IntegerArgumentType.getInteger(context, "role")))))
            .then(Commands.literal("set")
                .then(Commands.argument("role", IntegerArgumentType.integer(1, 5))
                    .suggests(SetPlayerWeightCommand::suggestRoleType)
                    .then(Commands.argument("weight", IntegerArgumentType.integer(0))
                        .executes(context -> executeSet(context.getSource(),
                            EntityArgument.getPlayer(context, "player"),
                            IntegerArgumentType.getInteger(context, "role"),
                            IntegerArgumentType.getInteger(context, "weight"))))))));
  }

  private static int executeGet(CommandSourceStack source, ServerPlayer player, int roleType)
      throws CommandSyntaxException {
    if (!Harpymodloader.isMojangVerify) {
      return 1;
    }
    if (player == null)
      return 0;
    final String[] TypeMappings = { "All", "Innocent", "Neutral", "Neutral for killers", "Killer", "Vigilante" };
    if (roleType == 0) {
      source.sendSuccess(
          () -> Component.translatable("Player [%s]", player.getName()).withStyle(ChatFormatting.GOLD),
          false);
      for (int i = 1; i <= 5; i++) {
        // 获取玩家角色权重
        final int roleType_1 = i;
        double percent = PlayerRoleWeightManager.getRoleWeightPercent(player, i) * 100;
        source.sendSystemMessage(Component.translatable("%s(%s): Role Selected Weight: %s",
            TypeMappings[roleType_1],
            roleType_1, String.format("%.2f", percent)));
      }
      if (PlayerRoleWeightManager.ForcePlayerTeam.containsKey(player.getUUID())) {
        int roleType_1 = PlayerRoleWeightManager.ForcePlayerTeam.get(player.getUUID());
        if (roleType_1 >= 0 && roleType_1 < TypeMappings.length) {
          source.sendSystemMessage(
              Component.translatable("Forced Team: %s", TypeMappings[roleType_1]).withStyle(ChatFormatting.AQUA));
        }

      }
      return 1;
    }
    if (roleType >= TypeMappings.length)
      return 0;
    // 获取玩家角色权重
    var weightManager = PlayerRoleWeightManager.playerWeights.get(player.getUUID());
    if (weightManager == null) {
      weightManager = new PlayerRoleWeightManager.WeightInfo();
      PlayerRoleWeightManager.playerWeights.putIfAbsent(player.getUUID(), weightManager);
    }
    int weight = weightManager.getWeight(roleType);
    source.sendSuccess(
        () -> Component.translatable("Player [%s]\nRole Type [%s(%s)] Weight [%s]", player.getName(),
            TypeMappings[roleType],
            roleType, weight),
        true);
    return 1;
  }

  private static int executeSet(CommandSourceStack source, ServerPlayer player, int roleType, int weight)
      throws CommandSyntaxException {
    if (!Harpymodloader.isMojangVerify) {
      return 1;
    }
    // 更新玩家角色权重
    var weightManager = PlayerRoleWeightManager.playerWeights.get(player.getUUID());
    if (weightManager == null) {
      weightManager = new PlayerRoleWeightManager.WeightInfo();
      PlayerRoleWeightManager.playerWeights.putIfAbsent(player.getUUID(), weightManager);
    }
    weightManager.putWeight(roleType, weight);
    source.sendSuccess(() -> Component.translatable("Modified successfully!\nPlayer [%s]\nRole Type [%s] Weight [%s]",
        player.getName(), roleType, weight), true);
    return 1;
  }

  public static CompletableFuture<Suggestions> suggestRoleType(CommandContext<CommandSourceStack> context,
      SuggestionsBuilder builder) {
    builder.suggest(1, Component.literal("Innocent"));
    builder.suggest(2, Component.literal("Neutral"));
    builder.suggest(3, Component.literal("Neutral For Killers"));
    builder.suggest(4, Component.literal("Killer"));
    builder.suggest(5, Component.literal("Vigilante"));
    return builder.buildFuture();
  }
}